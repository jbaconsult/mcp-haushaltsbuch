package de.jbaconsult.haushaltsbuch.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.jbaconsult.haushaltsbuch.kern.Bankzugang;
import de.jbaconsult.haushaltsbuch.kern.BankzugangId;
import de.jbaconsult.haushaltsbuch.kern.BankzugangPort;
import de.jbaconsult.haushaltsbuch.kern.Bankzugangstatus;
import de.jbaconsult.haushaltsbuch.kern.BenutzerId;
import de.jbaconsult.haushaltsbuch.kern.Benutzerkontext;
import de.jbaconsult.haushaltsbuch.kern.Betrag;
import de.jbaconsult.haushaltsbuch.kern.ExternerSaldo;
import de.jbaconsult.haushaltsbuch.kern.ExternesKonto;
import de.jbaconsult.haushaltsbuch.kern.ExternesKontoId;
import de.jbaconsult.haushaltsbuch.kern.InstitutKennung;
import de.jbaconsult.haushaltsbuch.kern.Kontokennung;
import de.jbaconsult.haushaltsbuch.kern.Saldenart;
import de.jbaconsult.haushaltsbuch.kern.Sitzungskennung;

/**
 * Weist die Zusagen nach, die das Datenmodell des Bankzugangs machen muss.
 *
 * <p>Drei davon sind Abnahmekriterien des Auftrags und keine Kür:
 *
 * <ul>
 *   <li>Ohne Benutzerkontext liefern die neuen Tabellen nichts.
 *   <li>Eine zweite Autorisierung desselben Kontos erzeugt keinen zweiten Datensatz.
 *   <li>Ein Zustandswert ist einmalig, kurz gültig und an einen Benutzer gebunden.
 * </ul>
 *
 * <p>Läuft gegen echtes PostgreSQL - Quarkus Dev Services startet es. Eine In-Memory-Datenbank
 * wäre wertlos, weil keine davon Row-Level-Security kennt.
 */
@QuarkusTest
class BankzugangPersistenzTest {

    private static final BenutzerId DEMO_EINS = BenutzerId.von("00000000-0000-0000-0000-000000000001");
    private static final BenutzerId DEMO_ZWEI = BenutzerId.von("00000000-0000-0000-0000-000000000002");

    @Inject
    BankzugangPort speicher;

    @Inject
    Benutzerkontext benutzerkontext;

    @Test
    @DisplayName("ohne Benutzerkontext liefern die neuen Tabellen nichts")
    void ohneKontextNichtsSichtbar() {
        // Erst mit Kontext anlegen, damit es überhaupt etwas zu sehen gäbe.
        benutzerkontext.setzen(DEMO_EINS);
        Bankzugang zugang = zugangAnlegen(DEMO_EINS);
        ExternesKontoId kontoId = kontoAnlegen(zugang.id(), "hash-ohne-kontext");
        speicher.saldoAblegen(kontoId, saldo("100.00"));

        assertThat(speicher.alleZugaenge()).isNotEmpty();

        // Kontext leeren. Die Policies sind fail-closed: kein Benutzer, keine Zeile.
        benutzerkontext.setzen(null);

        assertThat(speicher.alleZugaenge()).isEmpty();
        assertThat(speicher.alleKonten()).isEmpty();
        assertThat(speicher.findeZugang(zugang.id())).isEmpty();
        assertThat(speicher.saldenDesKontos(kontoId)).isEmpty();
    }

    @Test
    @DisplayName("eine zweite Autorisierung desselben Kontos erzeugt keinen zweiten Datensatz")
    void zweiteAutorisierungErzeugtKeinenZweitenDatensatz() {
        benutzerkontext.setzen(DEMO_EINS);

        Bankzugang ersterZugang = zugangAnlegen(DEMO_EINS);
        Kontokennung kennung = new Kontokennung("stabiler-hash-4711");

        ExternesKontoId ersteId =
                speicher.kontoUebernehmen(konto(ExternesKontoId.neu(), ersterZugang.id(), kennung, "Girokonto"));

        // Zweite Autorisierung: neuer Zugang, dasselbe Konto beim Institut. Die
        // Sitzungskennung waere jetzt eine andere - deshalb darf sie nicht der Schluessel sein.
        Bankzugang zweiterZugang = zugangAnlegen(DEMO_EINS);
        ExternesKontoId zweiteId = speicher.kontoUebernehmen(
                konto(ExternesKontoId.neu(), zweiterZugang.id(), kennung, "Girokonto neu benannt"));

        assertThat(zweiteId)
                .as("dasselbe Konto muss denselben Datensatz treffen, erkannt an der stabilen Kennung")
                .isEqualTo(ersteId);

        List<ExternesKonto> mitDieserKennung = speicher.alleKonten().stream()
                .filter(kandidat -> kandidat.kennung().equals(kennung))
                .toList();
        assertThat(mitDieserKennung).hasSize(1);

        ExternesKonto uebernommen = mitDieserKennung.get(0);
        assertThat(uebernommen.bezeichnung()).isEqualTo("Girokonto neu benannt");
        assertThat(uebernommen.bankzugang()).contains(zweiterZugang.id());
    }

    @Test
    @DisplayName("ein Zustandswert lässt sich genau einmal einlösen")
    void zustandIstEinmalig() {
        benutzerkontext.setzen(DEMO_EINS);

        Bankzugang zugang = zugangAnlegen(DEMO_EINS);
        String zustand = "zustand-" + UUID.randomUUID();
        speicher.zustandHinterlegen(
                zugang.id(), zustand, DEMO_EINS, Instant.now().plus(15, ChronoUnit.MINUTES));

        assertThat(speicher.zustandEinloesen(zustand, DEMO_EINS, Instant.now())).contains(zugang.id());

        assertThat(speicher.zustandEinloesen(zustand, DEMO_EINS, Instant.now()))
                .as("ein zweites Einlösen desselben Wertes muss ins Leere laufen")
                .isEmpty();
    }

    @Test
    @DisplayName("ein unbekannter, abgelaufener oder fremder Zustandswert wird abgelehnt")
    void zustandWirdGeprueft() {
        benutzerkontext.setzen(DEMO_EINS);
        Bankzugang zugang = zugangAnlegen(DEMO_EINS);

        // Unbekannt.
        assertThat(speicher.zustandEinloesen("nie-vergeben", DEMO_EINS, Instant.now()))
                .isEmpty();

        // Abgelaufen: hinterlegt mit einer Gültigkeit, die bereits verstrichen ist.
        String abgelaufen = "zustand-alt-" + UUID.randomUUID();
        speicher.zustandHinterlegen(
                zugang.id(), abgelaufen, DEMO_EINS, Instant.now().minus(1, ChronoUnit.MINUTES));
        assertThat(speicher.zustandEinloesen(abgelaufen, DEMO_EINS, Instant.now()))
                .as("ein abgelaufener Vorgang darf keinen Zugang mehr einrichten")
                .isEmpty();

        // Fremder Benutzer: ohne diese Prüfung genügte ein untergeschobener Link, um im Namen
        // eines Angemeldeten einen fremden Bankzugang einzurichten.
        String fremd = "zustand-fremd-" + UUID.randomUUID();
        speicher.zustandHinterlegen(zugang.id(), fremd, DEMO_EINS, Instant.now().plus(15, ChronoUnit.MINUTES));
        assertThat(speicher.zustandEinloesen(fremd, DEMO_ZWEI, Instant.now()))
                .as("ein Zustandswert eines anderen Benutzers darf nicht greifen")
                .isEmpty();

        // Und er ist danach immer noch für den richtigen Benutzer gültig - der abgewiesene
        // Versuch darf den Vorgang nicht verbrauchen.
        assertThat(speicher.zustandEinloesen(fremd, DEMO_EINS, Instant.now())).contains(zugang.id());
    }

    @Test
    @DisplayName("Salden überleben einen Fehlschlag des Zugangs")
    void saldenUeberlebenDenFehlschlag() {
        benutzerkontext.setzen(DEMO_EINS);

        Bankzugang zugang = zugangAnlegen(DEMO_EINS);
        ExternesKontoId kontoId = kontoAnlegen(zugang.id(), "hash-rote-probe-" + UUID.randomUUID());
        speicher.saldoAblegen(kontoId, saldo("1234.56"));

        // Die Sitzung besteht beim Anbieter nicht mehr - der Zugang wechselt in einen sichtbaren
        // Zustand. Ein Fehlschlag, der die letzten bekannten Zahlen löscht, wäre derselbe
        // Datenverlust wie ein abgestürzter Import, nur bequemer zu übersehen.
        speicher.aktualisieren(zugang.fehlgeschlagen("Sitzung beim Anbieter gelöscht"));

        Optional<Bankzugang> danach = speicher.findeZugang(zugang.id());
        assertThat(danach).isPresent();
        assertThat(danach.orElseThrow().status()).isEqualTo(Bankzugangstatus.FEHLGESCHLAGEN);
        assertThat(danach.orElseThrow().fehlermeldung()).contains("Sitzung beim Anbieter gelöscht");

        assertThat(speicher.saldenDesKontos(kontoId))
                .as("die zuletzt bekannten Salden bleiben erhalten")
                .hasSize(1);
        assertThat(speicher.alleKonten()).extracting(ExternesKonto::id).contains(kontoId);
    }

    // ------------------------------------------------------------------ Hilfen

    @Test
    @DisplayName("ein entfernter Zugang löst den Bezug seiner Konten, statt sie mitzunehmen")
    void entfernenLoestDenBezug() {
        benutzerkontext.setzen(DEMO_EINS);

        Bankzugang zugang = zugangAnlegen(DEMO_EINS);
        Kontokennung kennung = new Kontokennung("loesen-" + UUID.randomUUID());
        ExternesKontoId kontoId = kontoAnlegen(zugang.id(), kennung.wert());
        speicher.saldoAblegen(kontoId, saldo("100.00"));

        int geloest = speicher.entfernen(zugang.id());

        assertThat(geloest).isEqualTo(1);
        assertThat(speicher.findeZugang(zugang.id())).isEmpty();

        ExternesKonto uebriges = speicher.findeKonto(kontoId).orElseThrow();
        assertThat(uebriges.bankzugang())
                .as("der Fremdschluessel steht auf SET NULL - genau das prueft dieser Test an der Migration")
                .isEmpty();
        assertThat(speicher.saldenDesKontos(kontoId))
                .as("gemessene Vergangenheit ueberlebt das Entfernen ihres Zugangs")
                .isNotEmpty();
    }

    @Test
    @DisplayName("kontenEntfernen nimmt die Salden mit")
    void kontenEntfernenNimmtSaldenMit() {
        benutzerkontext.setzen(DEMO_EINS);

        Bankzugang zugang = zugangAnlegen(DEMO_EINS);
        ExternesKontoId kontoId = kontoAnlegen(zugang.id(), "mitnehmen-" + UUID.randomUUID());
        speicher.saldoAblegen(kontoId, saldo("250.00"));

        int entfernt = speicher.kontenEntfernen(zugang.id());
        speicher.entfernen(zugang.id());

        assertThat(entfernt).isEqualTo(1);
        assertThat(speicher.findeKonto(kontoId)).isEmpty();
        assertThat(speicher.saldenDesKontos(kontoId)).isEmpty();
    }

    @Test
    @DisplayName("ein entfernter Zugang nimmt seinen Zustandswert mit")
    void entfernenEntwertetDenZustand() {
        benutzerkontext.setzen(DEMO_EINS);

        Bankzugang zugang = zugangAnlegen(DEMO_EINS);
        String zustand = "zustand-" + UUID.randomUUID();
        speicher.zustandHinterlegen(
                zugang.id(), zustand, DEMO_EINS, Instant.now().plus(15, ChronoUnit.MINUTES));

        speicher.entfernen(zugang.id());

        assertThat(speicher.zustandEinloesen(zustand, DEMO_EINS, Instant.now()))
                .as("eine Rueckleitung, die spaeter noch eintrifft, darf nichts wiederbeleben")
                .isEmpty();
    }

    private Bankzugang zugangAnlegen(BenutzerId benutzer) {
        Bankzugang zugang = new Bankzugang(
                BankzugangId.neu(),
                "Testanbieter",
                new InstitutKennung("Testbank", "DE"),
                "Testbank",
                Bankzugangstatus.AUTORISIERT,
                Optional.of(Instant.now().plus(180, ChronoUnit.DAYS)),
                Optional.of(new Sitzungskennung("sitzung-" + UUID.randomUUID())),
                Optional.empty(),
                benutzer,
                Instant.now());
        speicher.anlegen(zugang);
        return zugang;
    }

    private ExternesKontoId kontoAnlegen(BankzugangId zugang, String kennung) {
        return speicher.kontoUebernehmen(konto(ExternesKontoId.neu(), zugang, new Kontokennung(kennung), "Testkonto"));
    }

    private static ExternesKonto konto(
            ExternesKontoId id, BankzugangId zugang, Kontokennung kennung, String bezeichnung) {
        return new ExternesKonto(
                id,
                Optional.of(zugang),
                kennung,
                Optional.empty(),
                "EUR",
                Optional.of("CACC"),
                Optional.of("Testprodukt"),
                bezeichnung,
                Optional.empty());
    }

    private static ExternerSaldo saldo(String betrag) {
        return new ExternerSaldo(Saldenart.GEBUCHT, "CLBD", Betrag.von(betrag), "EUR", Optional.empty(), Instant.now());
    }
}
