package de.jbaconsult.haushaltsbuch.persistenz;

import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.DEMO_EINS;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.GESCHAEFT_DEMO_EINS;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.GIRO_DEMO_EINS;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.KATEGORIE_LEBENSMITTEL;
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
 * Die Stelle, an der die Doppelzählung strukturell unmöglich wird.
 *
 * <p>Sammelabbuchung der Karte und Einzelumsätze beschreiben denselben Geldfluss. ADR-0003 verlangt,
 * dass keine Auswertungsregel daran denken muss - erreicht wird das über zwei Bedingungen an
 * mehrseitigen Bewegungen: die Seiten ergänzen sich zu null, und keine trägt eine Kategorie.
 *
 * <p>Damit ist eine Kategorienauswertung eine einzige Abfrage über Splits mit Kategorie. Der
 * Ausgleich der Karte kann keine haben, die Einzelumsätze haben je genau eine. Doppelt zu zählen ist
 * nicht verboten, sondern nicht formulierbar.
 *
 * <p>Der Fall wird hier an einer Privatentnahme durchgespielt - Geschäftskonto nach Girokonto, eine
 * der beiden Kopplungskanten zwischen den Sphären. Die Mechanik ist dieselbe wie bei der Karte.
 */
@QuarkusTest
class BewegungInvarianteTest {

    @Inject
    AgroalDataSource quelle;

    @Test
    @DisplayName("zwei Seiten, die sich zu null ergaenzen, werden angenommen")
    void zweiSeitenZuNullWerdenAngenommen() throws SQLException {
        UUID bewegung = UUID.randomUUID();

        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            UUID abgang = legeBuchung(transaktion, bewegung, GESCHAEFT_DEMO_EINS, "-1500.00", referenz("ENTNAHME-AB"));
            legeSplit(transaktion, abgang, "-1500.00", null);

            UUID zugang = legeBuchung(transaktion, bewegung, GIRO_DEMO_EINS, "1500.00", referenz("ENTNAHME-ZU"));
            legeSplit(transaktion, zugang, "1500.00", null);

            transaktion.bestaetigen();

            assertThat(zaehleSeiten(bewegung)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("zwei Seiten, die sich nicht zu null ergaenzen, werden abgelehnt")
    void zweiSeitenMitDifferenzWerdenAbgelehnt() throws SQLException {
        UUID bewegung = UUID.randomUUID();

        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            UUID abgang = legeBuchung(transaktion, bewegung, GESCHAEFT_DEMO_EINS, "-1500.00", referenz("KRUMM-AB"));
            legeSplit(transaktion, abgang, "-1500.00", null);

            // Eine Umbuchung zwischen eigenen Konten verliert kein Geld. Kommt weniger an, als
            // abgegangen ist, ist eine Seite falsch erfasst - und der Saldo beider Konten stimmt
            // danach nicht mehr, ohne dass irgendetwas fehlschlaegt.
            UUID zugang = legeBuchung(transaktion, bewegung, GIRO_DEMO_EINS, "1400.00", referenz("KRUMM-ZU"));
            legeSplit(transaktion, zugang, "1400.00", null);

            assertThatThrownBy(transaktion::bestaetigen)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("nicht zu null ergaenzen");
        }
    }

    @Test
    @DisplayName("eine Umbuchung darf keine Kategorie tragen - sonst zaehlt die Auswertung sie mit")
    void umbuchungMitKategorieWirdAbgelehnt() throws SQLException {
        UUID bewegung = UUID.randomUUID();

        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            UUID abgang = legeBuchung(transaktion, bewegung, GESCHAEFT_DEMO_EINS, "-1500.00", referenz("KATEGORIE-AB"));
            // Genau der Griff, der die Doppelzaehlung erzeugt: die Sammelabbuchung bekommt eine
            // Kategorie, und die Einzelumsaetze haben ihre schon.
            legeSplit(transaktion, abgang, "-1500.00", KATEGORIE_LEBENSMITTEL);

            UUID zugang = legeBuchung(transaktion, bewegung, GIRO_DEMO_EINS, "1500.00", referenz("KATEGORIE-ZU"));
            legeSplit(transaktion, zugang, "1500.00", null);

            assertThatThrownBy(transaktion::bestaetigen)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("darf keine Kategorie tragen");
        }
    }

    @Test
    @DisplayName("eine einseitige Bewegung darf eine Kategorie tragen - sie ist echter Aufwand")
    void einseitigeBewegungDarfKategorieTragen() throws SQLException {
        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            UUID buchung = legeBuchung(transaktion, UUID.randomUUID(), GIRO_DEMO_EINS, "-42.00", referenz("EINSEITIG"));
            legeSplit(transaktion, buchung, "-42.00", KATEGORIE_LEBENSMITTEL);

            transaktion.bestaetigen();
        }
    }

    private long zaehleSeiten(UUID bewegung) throws SQLException {
        try (Datenbanktransaktion transaktion = new Datenbanktransaktion(quelle, DEMO_EINS)) {
            return transaktion.zaehle("SELECT count(*) FROM buchung WHERE bewegung_id = ?", bewegung);
        }
    }
}
