package de.jbaconsult.haushaltsbuch.kern;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Tor vor dem Datenbestand.
 *
 * <p>Geprüft wird hier die Reihenfolge - lesen, prüfen, dann erst schreiben -, nicht das Schreiben
 * selbst. Der Schreibport ist ein Mitschnitt: er zeichnet auf, was ihm angeboten wurde. Genau das
 * ist die Frage, um die es geht. Ein Auszug, der die Prüfung nicht besteht, darf hier gar nicht
 * erst ankommen; ob ein späteres Zurückrollen ihn wieder entfernen würde, ist die schwächere Zusage.
 */
class ImportdienstTest {

    private static final KontoId KONTO = new KontoId(UUID.fromString("10000000-0000-0000-0000-000000000001"));

    /** Zeichnet auf, was angeboten wurde. Schreibt nichts. */
    private static final class Mitschnitt implements LedgerSchreibPort {
        private final List<Kontoauszug> angeboten = new ArrayList<>();

        @Override
        public Auszugsergebnis schreibe(KontoId konto, Kontoauszug auszug) {
            angeboten.add(auszug);
            return new Auszugsergebnis(auszug.bezeichnung(), auszug.zeilen().size(), 0, false);
        }
    }

    @Test
    @DisplayName("ein sauberer Auszug wird angeboten")
    void sauberWirdAngeboten() {
        Mitschnitt mitschnitt = new Mitschnitt();
        Importergebnis ergebnis =
                new Importdienst(mitschnitt).importiere(KONTO, Auszugsquelle.MT940, Fixture.lies("mt940/sauber.sta"));

        assertThat(ergebnis.istSauber()).isTrue();
        assertThat(ergebnis.neueBuchungen()).isEqualTo(2);
        assertThat(mitschnitt.angeboten).hasSize(1);
    }

    /**
     * Die Rote Probe in ihrer reinen Form: ein Cent Abweichung, und der Auszug erreicht den
     * Schreibport überhaupt nicht.
     */
    @Test
    @DisplayName("ein Cent Abweichung im Endsaldo, und nichts wird angeboten")
    void einCentUndNichtsWirdAngeboten() {
        String verfaelscht =
                Fixture.lies("mt940/sauber.sta").replace(":62F:C260831EUR3050,00", ":62F:C260831EUR3050,01");

        Mitschnitt mitschnitt = new Mitschnitt();
        Importergebnis ergebnis = new Importdienst(mitschnitt).importiere(KONTO, Auszugsquelle.MT940, verfaelscht);

        assertThat(mitschnitt.angeboten).isEmpty();
        assertThat(ergebnis.geschrieben()).isEmpty();
        assertThat(ergebnis.verletzt(Invariante.I1)).isTrue();
    }

    @Test
    @DisplayName("in einer Datei mit zwei Bloecken kommt der unversehrte durch")
    void unversehrterBlockKommtDurch() {
        Mitschnitt mitschnitt = new Mitschnitt();
        Importergebnis ergebnis = new Importdienst(mitschnitt)
                .importiere(KONTO, Auszugsquelle.MT940, Fixture.lies("mt940/blockkette-gebrochen.sta"));

        // Der Befund haengt am spaeteren Block. Der fruehere ist fuer sich in Ordnung und wird
        // geschrieben - die Granularitaet ist der einzelne Auszug, nicht die Datei.
        assertThat(ergebnis.verletzt(Invariante.I2)).isTrue();
        assertThat(mitschnitt.angeboten).extracting(Kontoauszug::auszugsnummer).containsExactly("00020");
    }

    @Test
    @DisplayName("eine unlesbare Datei belastet alles")
    void unlesbareDateiBelastetAlles() {
        Mitschnitt mitschnitt = new Mitschnitt();
        Importergebnis ergebnis =
                new Importdienst(mitschnitt).importiere(KONTO, Auszugsquelle.MT940, "das ist kein MT940");

        assertThat(mitschnitt.angeboten).isEmpty();
        assertThat(ergebnis.istSauber()).isFalse();
    }

    @Test
    @DisplayName("ein Auszug auf eine andere IBAN wird abgelehnt")
    void andereIbanWirdAbgelehnt() {
        Mitschnitt mitschnitt = new Mitschnitt();
        Iban andere = Iban.lesen("DE51123456780000998877").orElseThrow();

        Importergebnis ergebnis = new Importdienst(mitschnitt)
                .importiere(KONTO, andere, Auszugsquelle.MT940, Fixture.lies("mt940/sauber.sta"));

        assertThat(mitschnitt.angeboten).isEmpty();
        assertThat(ergebnis.verletzt(Invariante.I5)).isTrue();
    }

    @Test
    @DisplayName("die erwartete IBAN laesst den Auszug durch")
    void erwarteteIbanLaesstDurch() {
        Mitschnitt mitschnitt = new Mitschnitt();
        Iban erwartet = Iban.lesen("DE40123456780000123456").orElseThrow();

        Importergebnis ergebnis = new Importdienst(mitschnitt)
                .importiere(KONTO, erwartet, Auszugsquelle.MT940, Fixture.lies("mt940/sauber.sta"));

        assertThat(ergebnis.istSauber()).isTrue();
        assertThat(mitschnitt.angeboten).hasSize(1);
    }

    @Test
    @DisplayName("CAMT laeuft durch denselben Weg")
    void camtLaeuftDurchDenselbenWeg() {
        Mitschnitt mitschnitt = new Mitschnitt();
        Importergebnis ergebnis = new Importdienst(mitschnitt)
                .importiere(KONTO, Auszugsquelle.CAMT052, Fixture.lies("camt052/sauber.xml"));

        assertThat(ergebnis.istSauber()).isTrue();
        assertThat(ergebnis.neueBuchungen()).isEqualTo(3);
    }
}
