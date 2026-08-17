package de.jbaconsult.haushaltsbuch.persistenz;

import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.DEMO_EINS;
import static de.jbaconsult.haushaltsbuch.persistenz.Ledgerdaten.GIRO_DEMO_EINS;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;

import jakarta.inject.Inject;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.jbaconsult.haushaltsbuch.kern.Auszugsquelle;
import de.jbaconsult.haushaltsbuch.kern.BenutzerId;
import de.jbaconsult.haushaltsbuch.kern.Benutzerkontext;
import de.jbaconsult.haushaltsbuch.kern.Betrag;
import de.jbaconsult.haushaltsbuch.kern.Buchung;
import de.jbaconsult.haushaltsbuch.kern.Iban;
import de.jbaconsult.haushaltsbuch.kern.Importdienst;
import de.jbaconsult.haushaltsbuch.kern.Importergebnis;
import de.jbaconsult.haushaltsbuch.kern.KontoId;

/**
 * Der Import von der Datei bis in den Bestand.
 *
 * <p>Die Reihenfolge der Testschritte ist Teil der Aussage: erst wird gelesen, dann wird geprüft,
 * ob die strukturierten Felder einzeln angekommen sind, und dann wird derselbe Auszug ein zweites
 * Mal eingelesen. Der zweite Lauf ist der eigentliche Nachweis von I4 - Exportzeiträume überlappen
 * sich an den Randtagen, und ohne Deduplizierung entstehen dort Doubletten.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LedgerImportTest {

    private static final KontoId KONTO = new KontoId(GIRO_DEMO_EINS);
    private static final String AUSZUG = "00042";

    @Inject
    Importdienst importdienst;

    @Inject
    BuchungRepository buchungen;

    @Inject
    Benutzerkontext benutzerkontext;

    @Inject
    AgroalDataSource quelle;

    @BeforeEach
    void anmelden() {
        benutzerkontext.setzen(new BenutzerId(DEMO_EINS));
    }

    @Test
    @Order(1)
    @DisplayName("ein sauberer Auszug landet vollstaendig im Bestand")
    void sauberLandetVollstaendig() throws SQLException {
        Importergebnis ergebnis =
                importdienst.importiere(KONTO, Auszugsquelle.MT940, Fixture.lies("mt940/haushalt-august.sta"));

        assertThat(ergebnis.fehler()).isEmpty();
        assertThat(ergebnis.neueBuchungen()).isEqualTo(3);
        assertThat(buchungenZuAuszug(AUSZUG)).isEqualTo(3);
    }

    @Test
    @Order(2)
    @DisplayName("jede importierte Buchung hat genau einen Split ueber ihren vollen Betrag")
    void jedeBuchungHatGenauEinenSplit() {
        // Der Anfangszustand aus ADR-0004. "Aufschluesseln" ersetzt spaeter den einen Split durch
        // mehrere - dieselbe Struktur, nur mehr Zeilen.
        List<Buchung> importiert = buchungenDesAuszugs();

        assertThat(importiert).hasSize(3);
        for (Buchung buchung : importiert) {
            assertThat(buchungen.anzahlSplits(buchung.id()))
                    .as("Splits von %s", buchung.zeile().bankreferenz())
                    .isEqualTo(1);
            assertThat(buchungen.splitsumme(buchung.id()))
                    .as("Splitsumme von %s", buchung.zeile().bankreferenz())
                    .isEqualTo(buchung.betrag());
        }
    }

    /**
     * Akzeptanzkriterium 6 und der Grund für dieses ganze Schema.
     *
     * <p>{@code constraint.klassifikation-iban-mref} hält fest, dass eine Namensheuristik in der
     * Analyse zweimal vierstellige Posten verschluckt hat. Die Gegenmaßnahme ist, über IBAN,
     * Mandatsreferenz und Gläubigerkennung zu klassifizieren - und das setzt voraus, dass diese drei
     * den Import als eigene, abfragbare Spalten überleben.
     */
    @Test
    @Order(3)
    @DisplayName("Mandatsreferenz, Glaeubigerkennung und Gegenpartei-IBAN sind einzeln abfragbar")
    void strukturierteFelderSindEinzelnAbfragbar() {
        Iban versicherung = Iban.lesen("DE25100100100000765432").orElseThrow();

        assertThat(buchungen.mitGegenparteiIban(versicherung))
                .extracting(b -> b.zeile().bankreferenz())
                .contains("IMPREF0000000001");

        assertThat(buchungen.mitGlaeubigerkennung("DE55ZZZ00011122233"))
                .extracting(b -> b.zeile().bankreferenz())
                .contains("IMPREF0000000001");

        // Die Mandatsreferenz war in der Datei ueber einen Zeilenumbruch verteilt und ist trotzdem
        // ganz angekommen.
        assertThat(buchungen.mitMandatsreferenz("MND-2021-00077"))
                .extracting(b -> b.zeile().bankreferenz())
                .contains("IMPREF0000000001");

        Buchung lastschrift =
                buchungen.findeNachBankreferenz(KONTO, "IMPREF0000000001").orElseThrow();

        assertThat(lastschrift.zeile().gegenparteiName()).isEqualTo("Beispiel Versicherung");
        assertThat(lastschrift.zeile().betrag()).isEqualTo(Betrag.von("-89.90"));
        // Und der Verwendungszweck traegt sie nicht mehr mit sich herum.
        assertThat(lastschrift.zeile().verwendungszweck())
                .isEqualTo("Beitrag August 2026")
                .doesNotContain("MREF", "CRED");
    }

    @Test
    @Order(4)
    @DisplayName("die Gutschrift traegt ihre End-zu-Ende-Referenz, nicht ihr Mandat")
    void gutschriftTraegtEndeZuEndeReferenz() {
        Buchung gutschrift =
                buchungen.findeNachBankreferenz(KONTO, "IMPREF0000000002").orElseThrow();

        assertThat(gutschrift.zeile().endeZuEndeReferenz()).isEqualTo("RECHNUNG-2026-014");
        assertThat(gutschrift.zeile().mandatsreferenz()).isNull();
        assertThat(gutschrift.zeile().betrag()).isEqualTo(Betrag.von("1800.00"));
    }

    /** I4. Der eigentliche Nachweis: derselbe Auszug ein zweites Mal ergibt denselben Bestand. */
    @Test
    @Order(5)
    @DisplayName("zwei Laeufe desselben Auszugs erzeugen denselben Datenbestand")
    void zweiLaeufeErzeugenDenselbenBestand() throws SQLException {
        long vorher = buchungenZuAuszug(AUSZUG);

        Importergebnis zweiterLauf =
                importdienst.importiere(KONTO, Auszugsquelle.MT940, Fixture.lies("mt940/haushalt-august.sta"));

        assertThat(zweiterLauf.fehler()).isEmpty();
        assertThat(zweiterLauf.neueBuchungen()).isZero();
        assertThat(zweiterLauf.uebersprungeneBuchungen()).isEqualTo(3);
        assertThat(zweiterLauf.geschrieben().get(0).bereitsVorhanden()).isTrue();

        assertThat(buchungenZuAuszug(AUSZUG)).isEqualTo(vorher);
        assertThat(auszuegeMitNummer(AUSZUG)).isEqualTo(1);
    }

    private List<Buchung> buchungenDesAuszugs() {
        return buchungen.zuKonto(KONTO).stream()
                .filter(b -> b.zeile().bankreferenz().startsWith("IMPREF"))
                .toList();
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
