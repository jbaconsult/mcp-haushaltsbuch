package de.jbaconsult.haushaltsbuch.bankzugang;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.jbaconsult.haushaltsbuch.kern.Autorisierungsstart;
import de.jbaconsult.haushaltsbuch.kern.Autorisierungswunsch;
import de.jbaconsult.haushaltsbuch.kern.BankanbieterPort;
import de.jbaconsult.haushaltsbuch.kern.Betrag;
import de.jbaconsult.haushaltsbuch.kern.ExternerSaldo;
import de.jbaconsult.haushaltsbuch.kern.Feldabdeckung;
import de.jbaconsult.haushaltsbuch.kern.Iban;
import de.jbaconsult.haushaltsbuch.kern.Institut;
import de.jbaconsult.haushaltsbuch.kern.InstitutKennung;
import de.jbaconsult.haushaltsbuch.kern.Kontobefund;
import de.jbaconsult.haushaltsbuch.kern.Kontokennung;
import de.jbaconsult.haushaltsbuch.kern.Kontoreferenz;
import de.jbaconsult.haushaltsbuch.kern.Saldenart;
import de.jbaconsult.haushaltsbuch.kern.Sitzungskennung;
import de.jbaconsult.haushaltsbuch.kern.Zugangsbestand;
import de.jbaconsult.haushaltsbuch.kern.Zugangseroeffnung;
import de.jbaconsult.haushaltsbuch.kern.Zugangsfehler;

/**
 * Adapter zu Enable Banking.
 *
 * <p>Die einzige Stelle im System, an der Anbieterbegriffe vorkommen. {@code uid},
 * {@code aspsp}, {@code psu_type}, {@code identification_hash} und die Feldnamen der Antworten
 * enden hier; nach außen spricht ausschließlich {@link BankanbieterPort} in den Begriffen dieses
 * Systems.
 *
 * <p><b>Ausschließlich Kontoinformation.</b> Die registrierte Anwendung führt auch
 * Zahlungsauslösung als Dienst, weil der Anbieter sie in der Sandbox automatisch freischaltet. Es
 * gibt hier trotzdem keine Zahlungsmethode - auch keine auskommentierte und keine ungenutzte. An
 * dieser Stelle ist Disziplin der einzige Schutz, weil die Konfiguration ihn nicht mehr leistet.
 */
@ApplicationScoped
public class EnableBankingAdapter implements BankanbieterPort {

    private static final String ANBIETER = "Enable Banking";

    /** Pfad der Sitzungsressource. Auch die Erkennung der erloschenen Sitzung haengt daran. */
    private static final String SITZUNGSPFAD = "/sessions/";

    /** Wie viele Buchungen die Feldmessung höchstens betrachtet. */
    private static final int MESSUMFANG = 200;

    private static final DateTimeFormatter ZEITPUNKT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final Anwendungsschluessel schluessel;
    private final String basisUrl;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient klient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Inject
    public EnableBankingAdapter(
            Anwendungsschluessel schluessel,
            @ConfigProperty(name = "haushaltsbuch.bankzugang.basis-url", defaultValue = "https://api.enablebanking.com")
                    String basisUrl) {
        this.schluessel = schluessel;
        this.basisUrl = basisUrl.endsWith("/") ? basisUrl.substring(0, basisUrl.length() - 1) : basisUrl;
    }

    @Override
    public String anbieter() {
        return ANBIETER;
    }

    /** Ob der Zugang konfiguriert ist. Ohne Anwendungs-ID und Schlüssel gibt es keinen Aufruf. */
    public boolean istEingerichtet() {
        return schluessel.istEingerichtet();
    }

    // ---------------------------------------------------------------- Institute

    @Override
    public List<Institut> institute(String land) {
        JsonNode antwort = holen("/aspsps?country=" + land, Optional.empty());

        List<Institut> institute = new ArrayList<>();
        for (JsonNode eintrag : antwort.path("aspsps")) {
            String name = eintrag.path("name").asText(null);
            String landKennung = eintrag.path("country").asText(land);
            if (name == null) {
                continue;
            }

            // Fehlt die Angabe, gilt der konservativere Wert: eine zu lange Anfrage lehnt das
            // Institut ab, und die Meldung weist nicht auf den Zeitraum hin.
            long sekunden = eintrag.path("maximum_consent_validity").asLong(0);
            Duration hoechstdauer = sekunden > 0 ? Duration.ofSeconds(sekunden) : Duration.ofDays(90);

            List<String> benoetigt = new ArrayList<>();
            for (JsonNode kopf : eintrag.path("required_psu_headers")) {
                benoetigt.add(kopf.asText());
            }

            institute.add(new Institut(
                    new InstitutKennung(name, landKennung),
                    eintrag.path("name").asText(name),
                    hoechstdauer,
                    benoetigt));
        }
        return institute;
    }

    // ----------------------------------------------------------- Autorisierung

    @Override
    public Autorisierungsstart autorisierungStarten(Autorisierungswunsch wunsch) {
        ObjectNode zugriff = json.createObjectNode();
        zugriff.put("valid_until", ZEITPUNKT.format(wunsch.gueltigBis()));

        ObjectNode institut = json.createObjectNode();
        institut.put("name", wunsch.institut().name());
        institut.put("country", wunsch.institut().land());

        ObjectNode rumpf = json.createObjectNode();
        rumpf.set("access", zugriff);
        rumpf.set("aspsp", institut);
        rumpf.put("state", wunsch.zustand());
        rumpf.put("redirect_url", wunsch.rueckleitung());
        rumpf.put("psu_type", "personal");

        JsonNode antwort = senden("POST", "/auth", rumpf, wunsch.ipAdresse());

        String weiterleitung = antwort.path("url").asText(null);
        if (weiterleitung == null || weiterleitung.isBlank()) {
            throw new Zugangsfehler("Der Anbieter hat keine Weiterleitungsadresse geliefert.");
        }
        return new Autorisierungsstart(weiterleitung);
    }

    @Override
    public Zugangseroeffnung zugangEroeffnen(String autorisierungscode) {
        ObjectNode rumpf = json.createObjectNode();
        rumpf.put("code", autorisierungscode);

        JsonNode antwort = senden("POST", "/sessions", rumpf, Optional.empty());

        String sitzung = antwort.path("session_id").asText(null);
        if (sitzung == null || sitzung.isBlank()) {
            throw new Zugangsfehler("Der Anbieter hat keine Sitzungskennung geliefert.");
        }

        Instant gueltigBis = zeitpunkt(antwort.path("access").path("valid_until"))
                .orElseThrow(() ->
                        new Zugangsfehler("Der Anbieter hat kein Ende der Gültigkeit geliefert. Ohne Ablaufzeitpunkt "
                                + "wäre der Zugang ein Vorgang ohne Frist."));

        return new Zugangseroeffnung(new Sitzungskennung(sitzung), gueltigBis, kontenLesen(antwort.path("accounts")));
    }

    /**
     * Beendet die Sitzung beim Anbieter.
     *
     * <p>Eine Sitzung, die es nicht mehr gibt, gilt als erfolgreich beendet. Das ist keine
     * Nachsicht, sondern das Ziel des Aufrufs: die Autorisierung soll nicht mehr bestehen, und sie
     * besteht nicht mehr. Wer hier eine Ausnahme wirft, meldet dem Menschen einen Fehlschlag für
     * einen Zustand, den er gerade herstellen wollte.
     *
     * <p>Alles andere - Netzfehler, 500er, abgelehnter Widerruf - wird durchgereicht. Dort bleibt
     * offen, ob die Autorisierung noch gilt, und das gehört gesagt statt geschluckt.
     */
    @Override
    public void sitzungBeenden(Sitzungskennung sitzung) {
        try {
            senden("DELETE", SITZUNGSPFAD + sitzung.wert(), null, Optional.empty());
        } catch (Zugangsfehler fehler) {
            if (!fehler.istSitzungUngueltig()) {
                throw fehler;
            }
        }
    }

    @Override
    public Zugangsbestand bestand(Sitzungskennung sitzung) {
        JsonNode antwort;
        try {
            antwort = holen(SITZUNGSPFAD + sitzung.wert(), Optional.empty());
        } catch (Zugangsfehler fehler) {
            if (fehler.istSitzungUngueltig()) {
                return Zugangsbestand.nichtMehrAutorisiert();
            }
            throw fehler;
        }

        String status = antwort.path("status").asText("");
        if (!"AUTHORIZED".equalsIgnoreCase(status)) {
            return Zugangsbestand.nichtMehrAutorisiert();
        }

        // Der Anbieter liefert die Konten je nach Endpunkt als ausführliche Objekte oder als
        // blosse Kennungsliste. Beide Formen werden gelesen; die zweite verlangt einen
        // Detailaufruf je Konto, weil sonst die stabile Kennung fehlt - und ohne sie liesse sich
        // nur die flüchtige speichern, was der Fehler ist, den dieses System nicht machen will.
        JsonNode ausfuehrlich = antwort.path("accounts_data");
        if (ausfuehrlich.isArray() && !ausfuehrlich.isEmpty()) {
            return new Zugangsbestand(true, kontenLesen(ausfuehrlich));
        }

        List<Kontobefund> konten = new ArrayList<>();
        for (JsonNode kennung : antwort.path("accounts")) {
            String uid =
                    kennung.isTextual() ? kennung.asText() : kennung.path("uid").asText(null);
            if (uid == null || uid.isBlank()) {
                continue;
            }
            kontenLesen(json.createArrayNode().add(holen("/accounts/" + uid + "/details", Optional.empty())))
                    .forEach(konten::add);
        }
        return new Zugangsbestand(true, konten);
    }

    // ------------------------------------------------------------------ Salden

    @Override
    public List<ExternerSaldo> salden(Sitzungskennung sitzung, Kontoreferenz konto) {
        JsonNode antwort = holen("/accounts/" + konto.fluechtigeKennung() + "/balances", Optional.empty());
        Instant jetzt = Instant.now();

        List<ExternerSaldo> salden = new ArrayList<>();
        for (JsonNode eintrag : antwort.path("balances")) {
            JsonNode betragsknoten = eintrag.path("balance_amount");
            String betragstext = betragsknoten.path("amount").asText(null);
            if (betragstext == null || betragstext.isBlank()) {
                // Ein Saldo ohne Betrag ist kein Saldo. Er wird übersprungen und nicht durch eine
                // Null ersetzt - eine erfundene Zahl wäre schlimmer als eine fehlende.
                continue;
            }

            String artOriginal = eintrag.path("balance_type").asText("");
            salden.add(new ExternerSaldo(
                    saldenart(artOriginal),
                    artOriginal.isBlank() ? "(ohne Angabe)" : artOriginal,
                    new Betrag(new BigDecimal(betragstext)),
                    betragsknoten.path("currency").asText("EUR"),
                    datum(eintrag.path("reference_date")),
                    jetzt));
        }
        return salden;
    }

    // ------------------------------------------------------------ Feldmessung

    /**
     * Misst, welche Felder der Anbieter je Buchung tatsächlich liefert.
     *
     * <p>Das Ergebnis wird berichtet und nicht gespeichert. Buchungen gelangen ausschließlich über
     * den Importdienst in dieses System, weil sie dort gegen die Saldeninvarianten geprüft werden.
     *
     * <p>Neben den dokumentierten Feldern wird der <b>Rohtext</b> nach Mandatsreferenz und
     * Gläubigerkennung durchsucht. Im dokumentierten Modell gibt es für beide kein Feld; die Frage
     * ist, ob sie trotzdem irgendwo auftauchen - etwa im Verwendungszweck. Nur eine Messung an
     * Daten kann das beantworten.
     */
    @Override
    public Feldabdeckung feldabdeckungMessen(Sitzungskennung sitzung, Kontoreferenz konto) {
        JsonNode antwort = holen("/accounts/" + konto.fluechtigeKennung() + "/transactions", Optional.empty());
        JsonNode buchungen = antwort.path("transactions");

        int gesamt = 0;
        Map<String, Integer> zaehler = new LinkedHashMap<>();
        for (String feld : List.of(
                "entry_reference",
                "booking_date",
                "value_date",
                "transaction_amount",
                "creditor",
                "creditor_account",
                "debtor",
                "debtor_account",
                "remittance_information",
                "bank_transaction_code",
                "merchant_category_code",
                "balance_after_transaction",
                "credit_debit_indicator")) {
            zaehler.put(feld, 0);
        }

        int mandatstreffer = 0;
        int glaeubigertreffer = 0;

        for (JsonNode buchung : buchungen) {
            if (gesamt >= MESSUMFANG) {
                break;
            }
            gesamt++;

            for (String feld : zaehler.keySet()) {
                if (belegt(buchung.path(feld))) {
                    zaehler.merge(feld, 1, Integer::sum);
                }
            }

            String rohtext = buchung.toString();
            if (enthaeltMandatsreferenz(rohtext)) {
                mandatstreffer++;
            }
            if (enthaeltGlaeubigerkennung(rohtext)) {
                glaeubigertreffer++;
            }
        }

        List<Feldabdeckung.Feldbefund> felder = new ArrayList<>();
        for (Map.Entry<String, Integer> eintrag : zaehler.entrySet()) {
            felder.add(new Feldabdeckung.Feldbefund(eintrag.getKey(), "Feld der Buchung", eintrag.getValue(), gesamt));
        }
        felder.add(new Feldabdeckung.Feldbefund(
                "Mandatsreferenz (MREF)",
                "im gesamten Datensatz gesucht - im dokumentierten Modell gibt es kein Feld dafür",
                mandatstreffer,
                gesamt));
        felder.add(new Feldabdeckung.Feldbefund(
                "Gläubigerkennung (CRED)",
                "im gesamten Datensatz gesucht - im dokumentierten Modell gibt es kein Feld dafür",
                glaeubigertreffer,
                gesamt));

        List<String> hinweise = new ArrayList<>();
        hinweise.add("Stichprobe: %d von %d gelieferten Buchungen.".formatted(gesamt, buchungen.size()));
        if (!antwort.path("continuation_key").isMissingNode()
                && !antwort.path("continuation_key").isNull()) {
            hinweise.add("Der Anbieter liefert seitenweise; gemessen wurde nur die erste Seite.");
        }
        if (mandatstreffer == 0 && glaeubigertreffer == 0) {
            hinweise.add("Weder Mandatsreferenz noch Gläubigerkennung tauchen in irgendeiner Form auf. "
                    + "Damit ist die Klassifikation über IBAN, Mandatsreferenz und Gläubigerkennung "
                    + "über diesen Kanal nicht vollständig möglich.");
        }
        return new Feldabdeckung(gesamt, felder, hinweise);
    }

    /** Sucht die Mandatsreferenz in jeder Form, in der sie üblicherweise auftaucht. */
    private static boolean enthaeltMandatsreferenz(String rohtext) {
        String klein = rohtext.toLowerCase();
        return klein.contains("mandate_id")
                || klein.contains("mandateid")
                || klein.contains("mndtid")
                || rohtext.contains("MREF+")
                || klein.contains("mandatsreferenz");
    }

    private static boolean enthaeltGlaeubigerkennung(String rohtext) {
        String klein = rohtext.toLowerCase();
        return klein.contains("creditor_id")
                || klein.contains("creditorid")
                || klein.contains("cdtrschmeid")
                || klein.contains("creditor_scheme")
                || rohtext.contains("CRED+")
                || klein.contains("glaeubiger")
                || klein.contains("gläubiger");
    }

    // ------------------------------------------------------------------ Hilfen

    private List<Kontobefund> kontenLesen(JsonNode konten) {
        List<Kontobefund> befunde = new ArrayList<>();
        for (JsonNode konto : konten) {
            String fluechtig = konto.path("uid").asText(null);
            String stabil = konto.path("identification_hash").asText(null);

            if (fluechtig == null || fluechtig.isBlank() || stabil == null || stabil.isBlank()) {
                // Ohne stabile Kennung wird nichts übernommen. Ersatzweise die flüchtige zu
                // speichern wäre genau der Fehler, der erst Monate später auffällt.
                continue;
            }

            Optional<Iban> iban =
                    Iban.lesen(konto.path("account_id").path("iban").asText(""));
            String bezeichnung = ersterText(
                            konto.path("name"),
                            konto.path("product"),
                            konto.path("account_id").path("iban"))
                    .orElse("Konto");

            befunde.add(new Kontobefund(
                    new Kontokennung(stabil),
                    new Kontoreferenz(fluechtig),
                    iban,
                    konto.path("currency").asText("EUR"),
                    text(konto.path("cash_account_type")),
                    text(konto.path("product")),
                    bezeichnung));
        }
        return befunde;
    }

    /**
     * Bildet den Saldencode des Anbieters auf die fachliche Art ab.
     *
     * <p>Unbekannte Codes werden {@link Saldenart#SONSTIGE} und behalten ihren Originalwert. Eine
     * Zuordnung zu raten wäre schlimmer als sie offenzulassen: ein falsch als „verfügbar"
     * geführter Wert geht später in eine Kennzahl ein, die über Zahlungsfähigkeit entscheidet.
     */
    private static Saldenart saldenart(String code) {
        return switch (code == null ? "" : code.toUpperCase()) {
            case "CLBD", "ITBD", "OPBD" -> Saldenart.GEBUCHT;
            case "CLAV", "ITAV", "FWAV" -> Saldenart.VERFUEGBAR;
            case "XPCD", "PDNG" -> Saldenart.VORGEMERKT;
            case "PRCD" -> Saldenart.ABSCHLUSS;
            default -> Saldenart.SONSTIGE;
        };
    }

    private static boolean belegt(JsonNode knoten) {
        if (knoten == null || knoten.isMissingNode() || knoten.isNull()) {
            return false;
        }
        if (knoten.isTextual()) {
            return !knoten.asText().isBlank();
        }
        if (knoten.isArray() || knoten.isObject()) {
            return !knoten.isEmpty();
        }
        return true;
    }

    private static Optional<String> text(JsonNode knoten) {
        return belegt(knoten) ? Optional.of(knoten.asText()) : Optional.empty();
    }

    private static Optional<String> ersterText(JsonNode... knoten) {
        for (JsonNode kandidat : knoten) {
            Optional<String> wert = text(kandidat);
            if (wert.isPresent()) {
                return wert;
            }
        }
        return Optional.empty();
    }

    private static Optional<Instant> zeitpunkt(JsonNode knoten) {
        if (!belegt(knoten)) {
            return Optional.empty();
        }
        String wert = knoten.asText();
        try {
            return Optional.of(Instant.parse(wert));
        } catch (Exception nichtInstant) {
            try {
                return Optional.of(
                        LocalDate.parse(wert).atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (Exception auchNichtDatum) {
                return Optional.empty();
            }
        }
    }

    private static Optional<LocalDate> datum(JsonNode knoten) {
        if (!belegt(knoten)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(knoten.asText().substring(0, 10)));
        } catch (Exception unlesbar) {
            return Optional.empty();
        }
    }

    private JsonNode holen(String pfad, Optional<String> ipAdresse) {
        return senden("GET", pfad, null, ipAdresse);
    }

    /**
     * Setzt einen Aufruf ab.
     *
     * <p>Zu {@code Psu-Ip-Address}: einige Institute verlangen zusätzliche Kopfzeilen, und zwar
     * entweder alle geforderten oder keine. Ein Teil davon führt zu einem Fehler, der auf die
     * Kopfzeile nicht hinweist. Deshalb wird die Adresse nur gesetzt, wenn sie vorliegt.
     */
    private JsonNode senden(String methode, String pfad, JsonNode rumpf, Optional<String> ipAdresse) {
        HttpRequest.Builder bauer = HttpRequest.newBuilder()
                .uri(URI.create(basisUrl + pfad))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + schluessel.token())
                .header("Accept", "application/json");

        ipAdresse.filter(adresse -> !adresse.isBlank()).ifPresent(adresse -> bauer.header("Psu-Ip-Address", adresse));

        if (rumpf == null) {
            bauer.method(methode, HttpRequest.BodyPublishers.noBody());
        } else {
            bauer.header("Content-Type", "application/json")
                    .method(methode, HttpRequest.BodyPublishers.ofString(rumpf.toString()));
        }

        HttpResponse<String> antwort;
        try {
            antwort = klient.send(bauer.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException unterbrochen) {
            Thread.currentThread().interrupt();
            throw new Zugangsfehler("Der Aufruf beim Anbieter wurde unterbrochen.", unterbrochen);
        } catch (Exception fehler) {
            throw new Zugangsfehler("Der Anbieter ist nicht erreichbar.", fehler);
        }

        if (antwort.statusCode() >= 200 && antwort.statusCode() < 300) {
            // Ein leerer Rumpf ist kein Fehler. Ein DELETE quittiert je nach Anbieter mit 204 und
            // ohne Inhalt; readTree wuerde daran scheitern und einen geglueckten Aufruf als
            // unlesbare Antwort melden.
            if (antwort.body() == null || antwort.body().isBlank()) {
                return json.createObjectNode();
            }
            try {
                return json.readTree(antwort.body());
            } catch (Exception unlesbar) {
                throw new Zugangsfehler("Die Antwort des Anbieters ist nicht lesbar.", unlesbar);
            }
        }

        String meldung = fehlermeldung(antwort.body());
        // 401 und 403 auf einer Sitzung heissen: die Autorisierung gilt nicht mehr. Das ist kein
        // Netzfehler und darf nicht als solcher behandelt werden - ein Netzfehler dürfte keinen
        // Zugang entwerten, dieser Fall muss es.
        boolean sitzungWeg = pfad.startsWith(SITZUNGSPFAD)
                && (antwort.statusCode() == 401 || antwort.statusCode() == 403 || antwort.statusCode() == 404);

        throw new Zugangsfehler(
                "Der Anbieter antwortete mit %d: %s".formatted(antwort.statusCode(), meldung), sitzungWeg, null);
    }

    /** Holt die Meldung des Anbieters heraus, damit sie angezeigt werden kann statt verschluckt. */
    private String fehlermeldung(String rumpf) {
        if (rumpf == null || rumpf.isBlank()) {
            return "(ohne Meldung)";
        }
        try {
            JsonNode knoten = json.readTree(rumpf);
            return ersterText(knoten.path("message"), knoten.path("error"), knoten.path("detail"))
                    .orElse(rumpf.length() > 300 ? rumpf.substring(0, 300) : rumpf);
        } catch (Exception keinJson) {
            return rumpf.length() > 300 ? rumpf.substring(0, 300) : rumpf;
        }
    }
}
