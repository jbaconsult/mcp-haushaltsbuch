package de.jbaconsult.haushaltsbuch.bankzugang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.jbaconsult.haushaltsbuch.kern.Autorisierungswunsch;
import de.jbaconsult.haushaltsbuch.kern.ExternerSaldo;
import de.jbaconsult.haushaltsbuch.kern.Feldabdeckung;
import de.jbaconsult.haushaltsbuch.kern.Institut;
import de.jbaconsult.haushaltsbuch.kern.InstitutKennung;
import de.jbaconsult.haushaltsbuch.kern.Kontobefund;
import de.jbaconsult.haushaltsbuch.kern.Kontoreferenz;
import de.jbaconsult.haushaltsbuch.kern.Saldenart;
import de.jbaconsult.haushaltsbuch.kern.Sitzungskennung;
import de.jbaconsult.haushaltsbuch.kern.Zugangsbestand;
import de.jbaconsult.haushaltsbuch.kern.Zugangseroeffnung;
import de.jbaconsult.haushaltsbuch.kern.Zugangsfehler;

/**
 * Prüft den Adapter gegen einen Anbieter, den dieser Test selbst stellt.
 *
 * <p>Ein Attrappen-Server aus dem JDK statt einer Bibliothek: der Adapter spricht schlichtes HTTP
 * mit JSON, und ein echter Server prüft mehr als eine nachgebaute Klasse - Kopfzeilen, Statuscodes
 * und die Frage, ob das JWT überhaupt entsteht.
 *
 * <p>Der Schlüssel wird im Test erzeugt. Ein mitgelieferter Schlüssel wäre ein Schlüssel im
 * Repositorium, auch wenn er nur für Tests gilt.
 */
class EnableBankingAdapterTest {

    private HttpServer server;
    private Path schluesseldatei;
    private EnableBankingAdapter adapter;

    /** Was der Attrappen-Server auf welchen Pfad antwortet. */
    private final Map<String, Antwort> antworten = new LinkedHashMap<>();

    /** Die Aufrufe, die tatsächlich ankamen - für Zusicherungen über Kopfzeilen und Rumpf. */
    private final List<Aufruf> aufrufe = new ArrayList<>();

    private record Antwort(int status, String rumpf) {}

    private record Aufruf(String methode, String pfad, String rumpf, String autorisierung, String psuIp) {}

    @BeforeEach
    void aufbauen() throws Exception {
        KeyPair paar = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(paar.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        schluesseldatei = Files.createTempFile("bankzugang-test", ".pem");
        Files.writeString(schluesseldatei, pem);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::bedienen);
        server.start();

        Anwendungsschluessel schluessel =
                new Anwendungsschluessel(Optional.of("test-anwendung"), Optional.of(schluesseldatei.toString()));
        schluessel.schluesselLaden();

        adapter = new EnableBankingAdapter(
                schluessel, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void abbauen() throws IOException {
        server.stop(0);
        Files.deleteIfExists(schluesseldatei);
    }

    @Test
    @DisplayName("Institute werden gelesen, fehlende Höchstdauer wird konservativ ersetzt")
    void instituteLesen() {
        antworten.put("GET /aspsps", new Antwort(200, """
                        {"aspsps":[
                          {"name":"Testbank","country":"DE","maximum_consent_validity":15552000,
                           "required_psu_headers":["Psu-Ip-Address"]},
                          {"name":"Ohnegrenze","country":"DE"}
                        ]}"""));

        List<Institut> institute = adapter.institute("DE");

        assertThat(institute).hasSize(2);
        assertThat(institute.get(0).kennung()).isEqualTo(new InstitutKennung("Testbank", "DE"));
        assertThat(institute.get(0).hoechsteGueltigkeit().toDays()).isEqualTo(180);
        assertThat(institute.get(0).benoetigteAngaben()).containsExactly("Psu-Ip-Address");

        // Ohne Angabe gilt der konservativere Wert: eine zu lange Anfrage lehnt das Institut ab.
        assertThat(institute.get(1).hoechsteGueltigkeit().toDays()).isEqualTo(90);
    }

    @Test
    @DisplayName("das JWT geht als Bearer mit, und Psu-Ip-Address nur wenn vorhanden")
    void kopfzeilenWerdenGesetzt() {
        antworten.put("POST /auth", new Antwort(200, """
                {"url":"https://institut.invalid/anmelden"}"""));

        adapter.autorisierungStarten(new Autorisierungswunsch(
                new InstitutKennung("Testbank", "DE"),
                java.time.Instant.parse("2027-01-01T00:00:00Z"),
                "https://beispiel.invalid/rueck",
                "zustand-4711",
                Optional.of("203.0.113.7")));

        Aufruf aufruf = aufrufe.get(0);
        assertThat(aufruf.autorisierung()).startsWith("Bearer ");
        // Drei Teile, durch Punkte getrennt: Kopf, Rumpf, Signatur.
        assertThat(aufruf.autorisierung().substring("Bearer ".length()).split("\\."))
                .hasSize(3);
        assertThat(aufruf.psuIp()).isEqualTo("203.0.113.7");
        assertThat(aufruf.rumpf()).contains("\"state\":\"zustand-4711\"");
        assertThat(aufruf.rumpf()).contains("\"redirect_url\":\"https://beispiel.invalid/rueck\"");

        aufrufe.clear();
        adapter.autorisierungStarten(new Autorisierungswunsch(
                new InstitutKennung("Testbank", "DE"),
                java.time.Instant.parse("2027-01-01T00:00:00Z"),
                "https://beispiel.invalid/rueck",
                "zustand-4712",
                Optional.empty()));

        // Einige Institute verlangen entweder alle geforderten Kopfzeilen oder keine. Ohne
        // Adresse wird sie deshalb gar nicht gesetzt.
        assertThat(aufrufe.get(0).psuIp()).isNull();
    }

    @Test
    @DisplayName("die Sitzungseröffnung liefert stabile und flüchtige Kennung getrennt")
    void zugangEroeffnen() {
        antworten.put("POST /sessions", new Antwort(200, """
                        {"session_id":"sitzung-1",
                         "access":{"valid_until":"2027-01-01T00:00:00Z"},
                         "accounts":[
                           {"uid":"fluechtig-1","identification_hash":"stabil-1",
                            "account_id":{"iban":"DE02120300000000202051"},
                            "currency":"EUR","cash_account_type":"CACC","product":"Giro","name":"Girokonto"},
                           {"uid":"ohne-hash"}
                         ]}"""));

        Zugangseroeffnung eroeffnung = adapter.zugangEroeffnen("code-1");

        assertThat(eroeffnung.sitzung()).isEqualTo(new Sitzungskennung("sitzung-1"));
        assertThat(eroeffnung.gueltigBis()).isEqualTo(java.time.Instant.parse("2027-01-01T00:00:00Z"));

        // Das zweite Konto hat keine stabile Kennung und wird deshalb gar nicht uebernommen -
        // ersatzweise die fluechtige zu speichern waere der Fehler, der erst Monate spaeter auffaellt.
        assertThat(eroeffnung.konten()).hasSize(1);
        Kontobefund konto = eroeffnung.konten().get(0);
        assertThat(konto.kennung().wert()).isEqualTo("stabil-1");
        assertThat(konto.referenz()).isEqualTo(new Kontoreferenz("fluechtig-1"));
        assertThat(konto.bezeichnung()).isEqualTo("Girokonto");
        assertThat(konto.iban()).isPresent();
    }

    @Test
    @DisplayName("ohne Ende der Gültigkeit wird die Eröffnung abgelehnt")
    void eroeffnungOhneAblaufWirdAbgelehnt() {
        antworten.put("POST /sessions", new Antwort(200, """
                {"session_id":"sitzung-1","accounts":[]}"""));

        assertThatThrownBy(() -> adapter.zugangEroeffnen("code-1"))
                .isInstanceOf(Zugangsfehler.class)
                .hasMessageContaining("Ende der Gültigkeit");
    }

    @Test
    @DisplayName("eine erloschene Sitzung ist ein Befund, kein Ausnahmefall")
    void erlosceneSitzungIstEinBefund() {
        antworten.put("GET /sessions/sitzung-1", new Antwort(401, """
                {"message":"session expired"}"""));

        Zugangsbestand bestand = adapter.bestand(new Sitzungskennung("sitzung-1"));

        assertThat(bestand.nochAutorisiert()).isFalse();
        assertThat(bestand.konten()).isEmpty();
    }

    @Test
    @DisplayName("ein nicht autorisierter Status gilt ebenfalls als erloschen")
    void nichtAutorisierterStatus() {
        antworten.put("GET /sessions/sitzung-1", new Antwort(200, """
                {"status":"REVOKED","accounts":[]}"""));

        assertThat(adapter.bestand(new Sitzungskennung("sitzung-1")).nochAutorisiert())
                .isFalse();
    }

    @Test
    @DisplayName("Salden werden gelesen, unbekannte Arten behalten ihren Originalcode")
    void saldenLesen() {
        antworten.put("GET /accounts/fluechtig-1/balances", new Antwort(200, """
                        {"balances":[
                          {"name":"Gebucht","balance_type":"CLBD","reference_date":"2026-08-18",
                           "balance_amount":{"currency":"EUR","amount":"1234.56"}},
                          {"name":"Merkwuerdig","balance_type":"XYZ1",
                           "balance_amount":{"currency":"EUR","amount":"10.00"}},
                          {"name":"Ohne Betrag","balance_type":"CLAV",
                           "balance_amount":{"currency":"EUR"}}
                        ]}"""));

        List<ExternerSaldo> salden = adapter.salden(new Sitzungskennung("s"), new Kontoreferenz("fluechtig-1"));

        // Der Saldo ohne Betrag wird uebersprungen und nicht durch eine Null ersetzt: eine
        // erfundene Zahl waere schlimmer als eine fehlende.
        assertThat(salden).hasSize(2);
        assertThat(salden.get(0).art()).isEqualTo(Saldenart.GEBUCHT);
        assertThat(salden.get(0).betrag().wert().toPlainString()).isEqualTo("1234.56");
        assertThat(salden.get(0).referenzdatum()).isPresent();
        assertThat(salden.get(1).art()).isEqualTo(Saldenart.SONSTIGE);
        assertThat(salden.get(1).artOriginal()).isEqualTo("XYZ1");
    }

    @Test
    @DisplayName("die Feldmessung zählt je Feld und sucht Mandatsreferenz und Gläubigerkennung")
    void feldabdeckungMessen() {
        antworten.put("GET /accounts/fluechtig-1/transactions", new Antwort(200, """
                        {"transactions":[
                          {"entry_reference":"E1","booking_date":"2026-08-01","value_date":"2026-08-01",
                           "transaction_amount":{"currency":"EUR","amount":"-10.00"},
                           "creditor":{"name":"Haendler"},"creditor_account":{"iban":"DE02120300000000202051"},
                           "remittance_information":["Einkauf"],"credit_debit_indicator":"DBIT"},
                          {"entry_reference":"E2","booking_date":"2026-08-02",
                           "transaction_amount":{"currency":"EUR","amount":"5.00"},
                           "remittance_information":[],"credit_debit_indicator":"CRDT"}
                        ]}"""));

        Feldabdeckung abdeckung =
                adapter.feldabdeckungMessen(new Sitzungskennung("s"), new Kontoreferenz("fluechtig-1"));

        assertThat(abdeckung.anzahlBuchungen()).isEqualTo(2);

        Map<String, Feldabdeckung.Feldbefund> nachName = new LinkedHashMap<>();
        abdeckung.felder().forEach(feld -> nachName.put(feld.name(), feld));

        assertThat(nachName.get("entry_reference").belegt()).isEqualTo(2);
        assertThat(nachName.get("creditor").belegt()).isEqualTo(1);
        // Eine leere Liste gilt als nicht belegt - sonst zaehlte ein vorhandenes, aber leeres
        // Feld als Information.
        assertThat(nachName.get("remittance_information").belegt()).isEqualTo(1);

        assertThat(nachName.get("Mandatsreferenz (MREF)").belegt()).isZero();
        assertThat(nachName.get("Gläubigerkennung (CRED)").belegt()).isZero();
        assertThat(abdeckung.hinweise())
                .anyMatch(hinweis -> hinweis.contains("Weder Mandatsreferenz noch Gläubigerkennung"));
    }

    @Test
    @DisplayName("die Meldung des Anbieters wird durchgereicht, nicht verschluckt")
    void fehlermeldungWirdDurchgereicht() {
        antworten.put("GET /aspsps", new Antwort(500, """
                {"message":"Interner Fehler beim Anbieter"}"""));

        assertThatThrownBy(() -> adapter.institute("DE"))
                .isInstanceOf(Zugangsfehler.class)
                .hasMessageContaining("Interner Fehler beim Anbieter");
    }

    @Test
    @DisplayName("ohne eingerichteten Zugang gibt es keinen Aufruf, sondern eine Auskunft")
    void ohneEinrichtungKeinAufruf() {
        EnableBankingAdapter ohneSchluessel = new EnableBankingAdapter(
                new Anwendungsschluessel(Optional.empty(), Optional.empty()), "http://127.0.0.1:1");

        assertThat(ohneSchluessel.istEingerichtet()).isFalse();
        assertThatThrownBy(() -> ohneSchluessel.institute("DE"))
                .isInstanceOf(Zugangsfehler.class)
                .hasMessageContaining("nicht eingerichtet");
    }

    // ------------------------------------------------------------------ Server

    private void bedienen(HttpExchange austausch) throws IOException {
        String rumpf = new String(austausch.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String pfad = austausch.getRequestURI().getPath();
        String schluessel = austausch.getRequestMethod() + " " + pfad;

        aufrufe.add(new Aufruf(
                austausch.getRequestMethod(),
                pfad,
                rumpf,
                austausch.getRequestHeaders().getFirst("Authorization"),
                austausch.getRequestHeaders().getFirst("Psu-Ip-Address")));

        Antwort antwort = antworten.getOrDefault(schluessel, new Antwort(404, "{\"message\":\"nicht hinterlegt\"}"));
        byte[] daten = antwort.rumpf().getBytes(StandardCharsets.UTF_8);
        austausch.getResponseHeaders().add("Content-Type", "application/json");
        austausch.sendResponseHeaders(antwort.status(), daten.length);
        try (OutputStream ausgabe = austausch.getResponseBody()) {
            ausgabe.write(daten);
        }
    }
}
