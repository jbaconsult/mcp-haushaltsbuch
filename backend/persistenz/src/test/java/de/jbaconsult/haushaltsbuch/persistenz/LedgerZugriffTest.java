package de.jbaconsult.haushaltsbuch.persistenz;

import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.DEMO_EINS;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.DEMO_ZWEI;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.GIRO_DEMO_ZWEI;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.HAUSHALT_GEMEINSAM;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.legeBuchung;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.legeSplit;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.referenz;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;

import jakarta.inject.Inject;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die zeilenbasierte Zugriffskontrolle auf dem Ledger.
 *
 * <p>Buchungen sind der Teil des Bestands, bei dem das zählt. Ein Konto verrät seine Bezeichnung,
 * eine Buchung verrät, was jemand wann wo ausgegeben hat.
 *
 * <p>Der erste Test ist der wichtigste und zugleich der langweiligste: ohne gesetzten
 * Benutzerkontext liefert eine Abfrage <b>nichts</b>. Die umgekehrte Voreinstellung wäre ein
 * Datenleck, das niemandem auffällt, weil nichts fehlschlägt.
 */
@QuarkusTest
class LedgerZugriffTest {

    @Inject
    AgroalDataSource quelle;

    @Test
    @DisplayName("ohne Benutzerkontext liefern buchung und buchungssplit null Zeilen")
    void ohneKontextNullZeilen() throws SQLException {
        // Erst dafuer sorgen, dass ueberhaupt etwas da ist - sonst prueft der Test nichts.
        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            UUID buchung = legeBuchung(
                    transaktion, UUID.randomUUID(), HAUSHALT_GEMEINSAM, "-19.99", referenz("RLS-VORHANDEN"));
            legeSplit(transaktion, buchung, "-19.99", null);
            transaktion.bestaetigen();
        }

        try (Datenbanktransaktion ohneKontext = new Datenbanktransaktion(quelle, null)) {
            assertThat(ohneKontext.zaehle("SELECT count(*) FROM buchung")).isZero();
            assertThat(ohneKontext.zaehle("SELECT count(*) FROM buchungssplit")).isZero();
            assertThat(ohneKontext.zaehle("SELECT count(*) FROM bewegung")).isZero();
            assertThat(ohneKontext.zaehle("SELECT count(*) FROM kontoauszug")).isZero();
            assertThat(ohneKontext.zaehle("SELECT count(*) FROM kategorie")).isZero();
        }
    }

    @Test
    @DisplayName("eine fremde Buchung ist nicht sichtbar, auch nicht mit bekannter Kennung")
    void fremdeBuchungNichtSichtbar() throws SQLException {
        String referenz = referenz("RLS-FREMD");

        try (Datenbanktransaktion alsZwei = new Datenbanktransaktion(quelle, DEMO_ZWEI)) {
            UUID buchung = legeBuchung(alsZwei, UUID.randomUUID(), GIRO_DEMO_ZWEI, "-5.00", referenz);
            legeSplit(alsZwei, buchung, "-5.00", null);
            alsZwei.bestaetigen();
        }

        try (Datenbanktransaktion alsEins = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            assertThat(alsEins.zaehle("SELECT count(*) FROM buchung WHERE bankreferenz = ?", referenz))
                    .isZero();
        }

        try (Datenbanktransaktion alsZwei = new Datenbanktransaktion(quelle, DEMO_ZWEI)) {
            assertThat(alsZwei.zaehle("SELECT count(*) FROM buchung WHERE bankreferenz = ?", referenz))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("der Split erbt die Sichtbarkeit seiner Buchung")
    void splitErbtSichtbarkeit() throws SQLException {
        String referenz = referenz("RLS-SPLIT");
        UUID buchung;

        try (Datenbanktransaktion alsZwei = new Datenbanktransaktion(quelle, DEMO_ZWEI)) {
            buchung = legeBuchung(alsZwei, UUID.randomUUID(), GIRO_DEMO_ZWEI, "-7.00", referenz);
            legeSplit(alsZwei, buchung, "-7.00", null);
            alsZwei.bestaetigen();
        }

        try (Datenbanktransaktion alsEins = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            // Die Kennung ist bekannt - das ist der interessante Fall. Wer sie erraet oder aus
            // einem alten Protokoll hat, darf trotzdem nichts sehen.
            assertThat(alsEins.zaehle("SELECT count(*) FROM buchungssplit WHERE buchung_id = ?", buchung))
                    .isZero();
        }
    }

    @Test
    @DisplayName("Leserecht allein erlaubt kein Schreiben")
    void leserechtErlaubtKeinSchreiben() throws SQLException {
        // Demo Zwei hat auf dem gemeinsamen Konto nur LESEN. Genau der Fall, um den es geht:
        // mitsehen duerfen, ohne eingreifen zu koennen.
        try (Datenbanktransaktion alsZwei = new Datenbanktransaktion(quelle, DEMO_ZWEI)) {
            assertThatThrownBy(() -> legeBuchung(
                            alsZwei, UUID.randomUUID(), HAUSHALT_GEMEINSAM, "-1.00", referenz("RLS-NUR-LESEN")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    @DisplayName("das gemeinsame Konto sehen beide")
    void gemeinsamesKontoSehenBeide() throws SQLException {
        String referenz = referenz("RLS-GEMEINSAM");

        try (Datenbanktransaktion alsEins = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            UUID buchung = legeBuchung(alsEins, UUID.randomUUID(), HAUSHALT_GEMEINSAM, "-12.00", referenz);
            legeSplit(alsEins, buchung, "-12.00", null);
            alsEins.bestaetigen();
        }

        for (UUID benutzer : new UUID[] {DEMO_EINS, DEMO_ZWEI}) {
            try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, benutzer)) {
                assertThat(transaktion.zaehle("SELECT count(*) FROM buchung WHERE bankreferenz = ?", referenz))
                        .as("Benutzer %s sieht die Buchung auf dem gemeinsamen Konto", benutzer)
                        .isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("die Kategorientaxonomie ist haushaltsweit sichtbar")
    void kategorienHaushaltsweitSichtbar() throws SQLException {
        // Das ist eine Entscheidung und kein Versehen: HB-05 stellt fest, dass es innerhalb der
        // Ehe keinen Geheimhaltungsbedarf gibt, und zwei Menschen mit verschiedenen
        // Kategorienlisten koennen ueber denselben Haushalt nicht reden.
        for (UUID benutzer : new UUID[] {DEMO_EINS, DEMO_ZWEI}) {
            try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, benutzer)) {
                assertThat(transaktion.zaehle("SELECT count(*) FROM kategorie")).isEqualTo(5);
                assertThat(transaktion.zaehle("SELECT count(*) FROM kategoriegruppe"))
                        .isEqualTo(3);
            }
        }
    }
}
