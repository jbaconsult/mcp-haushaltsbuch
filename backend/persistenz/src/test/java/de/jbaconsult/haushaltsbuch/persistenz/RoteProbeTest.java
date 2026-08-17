package de.jbaconsult.haushaltsbuch.persistenz;

import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.DEMO_EINS;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.GIRO_DEMO_EINS;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import jakarta.inject.Inject;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.jbaconsult.haushaltsbuch.kern.Auszugsquelle;
import de.jbaconsult.haushaltsbuch.kern.BenutzerId;
import de.jbaconsult.haushaltsbuch.kern.Benutzerkontext;
import de.jbaconsult.haushaltsbuch.kern.Importdienst;
import de.jbaconsult.haushaltsbuch.kern.Importergebnis;
import de.jbaconsult.haushaltsbuch.kern.Importfehler;
import de.jbaconsult.haushaltsbuch.kern.Invariante;
import de.jbaconsult.haushaltsbuch.kern.KontoId;

/**
 * Die Rote Probe des Sub-Sprints, in zwei Hälften.
 *
 * <p><b>Erste Hälfte, das Gate greift.</b> Ein sauberer Auszug wird in der Kopie um einen Cent im
 * Endsaldo verändert. Erwartung: der Import schlägt fehl, die Fehlerliste nennt I1, und für diesen
 * Auszug steht keine einzige Zeile im Bestand. Ein Import, der neun von zehn Buchungen schreibt und
 * die zehnte meldet, ist ein Fehlschlag der Probe und kein Teilerfolg - der Teilbestand sieht aus
 * wie ein vollständiger.
 *
 * <p><b>Zweite Hälfte, die Daten überleben.</b> Vor dem manipulierten Auszug wird ein sauberer
 * importiert. Erwartung: der bleibt danach unverändert vollständig. Ein Rollback, der zu weit
 * zurückrollt, ist derselbe Datenverlust wie ein fehlender Rollback, nur schwerer zu bemerken.
 */
@QuarkusTest
class RoteProbeTest {

    private static final KontoId KONTO = new KontoId(GIRO_DEMO_EINS);
    private static final String SAUBER = "00043";
    private static final String MANIPULIERT = "00044";

    @Inject
    Importdienst importdienst;

    @Inject
    Benutzerkontext benutzerkontext;

    @Inject
    AgroalDataSource quelle;

    @BeforeEach
    void anmelden() {
        benutzerkontext.setzen(new BenutzerId(DEMO_EINS));
    }

    @Test
    @DisplayName("ein Cent Abweichung: der Auszug wird abgelehnt und der vorige Bestand bleibt stehen")
    void einCentAbweichung() throws SQLException {
        // --- Vorlauf: ein sauberer Auszug geht in den Bestand -------------------------------
        Importergebnis sauber =
                importdienst.importiere(KONTO, Auszugsquelle.MT940, Fixture.lies("mt940/haushalt-september.sta"));

        assertThat(sauber.fehler()).isEmpty();
        assertThat(buchungenZuAuszug(SAUBER)).isEqualTo(2);

        // --- Erste Haelfte: das Gate greift -------------------------------------------------
        String original = Fixture.lies("mt940/haushalt-oktober.sta");
        String verfaelscht = original.replace(":62F:C261031EUR5620,40", ":62F:C261031EUR5620,41");
        assertThat(verfaelscht).isNotEqualTo(original);

        Importergebnis abgelehnt = importdienst.importiere(KONTO, Auszugsquelle.MT940, verfaelscht);

        assertThat(abgelehnt.fehler()).extracting(Importfehler::invariante).containsExactly(Invariante.I1);
        assertThat(abgelehnt.geschrieben()).isEmpty();

        // Kein Auszug, keine Buchung. Nicht "die meisten", sondern keine.
        assertThat(buchungenZuAuszug(MANIPULIERT)).isZero();
        assertThat(auszuegeMitNummer(MANIPULIERT)).isZero();

        // --- Zweite Haelfte: die Daten ueberleben -------------------------------------------
        assertThat(buchungenZuAuszug(SAUBER)).isEqualTo(2);
        assertThat(auszuegeMitNummer(SAUBER)).isEqualTo(1);
    }

    @Test
    @DisplayName("die Fehlermeldung nennt die Invariante und die Differenz, nicht nur ein Scheitern")
    void fehlermeldungNenntInvarianteUndDifferenz() {
        String verfaelscht =
                Fixture.lies("mt940/haushalt-oktober.sta").replace(":62F:C261031EUR5620,40", ":62F:C261031EUR5620,41");

        Importfehler fehler = importdienst
                .importiere(KONTO, Auszugsquelle.MT940, verfaelscht)
                .fehler()
                .get(0);

        assertThat(fehler.invariante()).isEqualTo(Invariante.I1);
        assertThat(fehler.auszug()).isEqualTo("Auszug " + MANIPULIERT);
        assertThat(fehler.meldung()).contains("Differenz 0.01 EUR");
    }

    private long buchungenZuAuszug(String auszugsnummer) throws SQLException {
        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            return transaktion.zaehle("""
                    SELECT count(*) FROM buchung b
                      JOIN kontoauszug a ON a.id = b.kontoauszug_id
                     WHERE a.auszugsnummer = ? AND a.konto_id = ?
                    """, auszugsnummer, GIRO_DEMO_EINS);
        }
    }

    private long auszuegeMitNummer(String auszugsnummer) throws SQLException {
        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            return transaktion.zaehle(
                    "SELECT count(*) FROM kontoauszug WHERE auszugsnummer = ? AND konto_id = ?",
                    auszugsnummer,
                    GIRO_DEMO_EINS);
        }
    }
}
