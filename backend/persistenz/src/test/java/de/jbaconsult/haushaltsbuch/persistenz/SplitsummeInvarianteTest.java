package de.jbaconsult.haushaltsbuch.persistenz;

import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.DEMO_EINS;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.KATEGORIE_LEBENSMITTEL;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.RUECKLAGE;
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
 * Die Summe der Splits ist der Buchungsbetrag - und die Datenbank hält das fest, nicht der
 * Java-Code.
 *
 * <p>Der Unterschied ist der Punkt dieses Tests. Eine Prüfung in der Anwendung fehlt genau dann,
 * wenn jemand an ihr vorbei schreibt: beim Import, beim Reparaturskript, beim Migrationsschritt. Was
 * hier geprüft wird, hält auch dann.
 *
 * <p>Geprüft wird deshalb an einer Verbindung unter der Anwendungsrolle, nicht über Hibernate.
 */
@QuarkusTest
class SplitsummeInvarianteTest {

    @Inject
    AgroalDataSource quelle;

    @Test
    @DisplayName("passende Splitsumme wird angenommen")
    void passendeSplitsummeWirdAngenommen() throws SQLException {
        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            UUID buchung = legeBuchung(transaktion, UUID.randomUUID(), RUECKLAGE, "-100.00", referenz("SPLIT-OK"));
            legeSplit(transaktion, buchung, "-100.00", null);

            transaktion.bestaetigen();

            assertThat(zaehleSplits(buchung)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("mehrere Splits duerfen sich zum Buchungsbetrag ergaenzen")
    void mehrereSplitsErgaenzenSich() throws SQLException {
        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            UUID buchung = legeBuchung(transaktion, UUID.randomUUID(), RUECKLAGE, "-100.00", referenz("SPLIT-DREI"));
            // Das ist der "Aufschluesseln"-Vorgang aus ADR-0004: dieselbe Struktur, nur mehr Zeilen.
            legeSplit(transaktion, buchung, "-60.00", KATEGORIE_LEBENSMITTEL);
            legeSplit(transaktion, buchung, "-25.50", null);
            legeSplit(transaktion, buchung, "-14.50", null);

            transaktion.bestaetigen();

            assertThat(zaehleSplits(buchung)).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("eine abweichende Splitsumme wird beim Bestaetigen abgelehnt")
    void abweichendeSplitsummeWirdAbgelehnt() throws SQLException {
        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            UUID buchung = legeBuchung(transaktion, UUID.randomUUID(), RUECKLAGE, "-100.00", referenz("SPLIT-KRUMM"));
            legeSplit(transaktion, buchung, "-90.00", null);

            // Aufgeschoben, und das muss so sein: zwischen dem Einfuegen der Buchung und dem ihrer
            // Splits ist sie zwingend unausgeglichen.
            assertThatThrownBy(transaktion::bestaetigen)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Splitsumme");
        }
    }

    @Test
    @DisplayName("eine Buchung ohne Split wird abgelehnt")
    void buchungOhneSplitWirdAbgelehnt() throws SQLException {
        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            legeBuchung(transaktion, UUID.randomUUID(), RUECKLAGE, "-100.00", referenz("SPLIT-KEIN"));

            assertThatThrownBy(transaktion::bestaetigen)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("keinen Split");
        }
    }

    @Test
    @DisplayName("ein nachtraeglich veraenderter Split faellt ebenfalls auf")
    void nachtraeglicheAenderungFaelltAuf() throws SQLException {
        UUID buchung;
        String referenz = referenz("SPLIT-AENDERUNG");

        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            buchung = legeBuchung(transaktion, UUID.randomUUID(), RUECKLAGE, "-100.00", referenz);
            legeSplit(transaktion, buchung, "-100.00", null);
            transaktion.bestaetigen();
        }

        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            transaktion.aus("UPDATE buchungssplit SET betrag = -50.00 WHERE buchung_id = ?", buchung);

            assertThatThrownBy(transaktion::bestaetigen)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Splitsumme");
        }
    }

    private long zaehleSplits(UUID buchung) throws SQLException {
        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            return transaktion.zaehle("SELECT count(*) FROM buchungssplit WHERE buchung_id = ?", buchung);
        }
    }
}
