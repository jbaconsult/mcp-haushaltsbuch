package de.jbaconsult.haushaltsbuch.kern;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Der Parser gegen die Fallen, die das Format bereithält.
 *
 * <p>Die Referenzimplementierung dieser Parser existiert außerhalb dieses Repositories in Python
 * und hat den validierten Bestand über zwei Jahre und acht Konten ohne Validierungsfehler erzeugt.
 * Der Java-Code ist neu geschrieben, nicht portiert. Scheitert hier ein Fall, der dort durchläuft,
 * ist das ein Befund über diesen Code und keine Schwäche des Fixtures.
 */
class Mt940ParserTest {

    @Nested
    @DisplayName("Ein sauberer Auszug")
    class SaubererAuszug {

        private final Parsebefund befund = Mt940Parser.lies(Fixture.lies("mt940/sauber.sta"));

        @Test
        @DisplayName("wird ohne Befund gelesen")
        void wirdOhneBefundGelesen() {
            assertThat(befund.fehler()).isEmpty();
            assertThat(befund.auszuege()).hasSize(1);
        }

        @Test
        @DisplayName("traegt Kopfdaten und Salden der Bank")
        void traegtKopfdatenUndSalden() {
            Kontoauszug auszug = befund.auszuege().get(0);

            assertThat(auszug.auszugsnummer()).isEqualTo("00005");
            assertThat(auszug.konto()).map(Iban::wert).contains("DE40123456780000123456");
            assertThat(auszug.anfangssaldo()).isEqualTo(Betrag.von("1000.00"));
            assertThat(auszug.endsaldo()).isEqualTo(Betrag.von("3050.00"));
            assertThat(auszug.von()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(auszug.bis()).isEqualTo(LocalDate.of(2026, 8, 31));
        }

        /**
         * Der Kern des Sub-Sprints. Mandatsreferenz, Gläubigerkennung und Gegenpartei-IBAN stehen
         * einzeln - nicht als Teil des Verwendungszwecks. Ohne diese Trennung ist weder die Regel
         * „IBAN vor Mandatsreferenz vor Namenstext" ausführbar noch die Acquirer-Unterscheidung
         * berechenbar.
         */
        @Test
        @DisplayName("zerlegt die strukturierten Felder einzeln")
        void zerlegtDieStrukturiertenFelderEinzeln() {
            Buchungszeile lastschrift = befund.auszuege().get(0).zeilen().get(0);

            assertThat(lastschrift.mandatsreferenz()).isEqualTo("MANDAT-2019-00042");
            assertThat(lastschrift.glaeubigerkennung()).isEqualTo("DE98ZZZ09999999999");
            assertThat(lastschrift.gegenpartei()).map(Iban::wert).contains("DE03876543210000555444");
            assertThat(lastschrift.gegenparteiName()).isEqualTo("Muster Vermietung GmbH");
            assertThat(lastschrift.verwendungszweck()).isEqualTo("Miete August 2026");
            assertThat(lastschrift.buchungstext()).isEqualTo("SEPA-LASTSCHRIFT");

            // Und der Verwendungszweck traegt sie nicht mehr mit sich herum.
            assertThat(lastschrift.verwendungszweck()).doesNotContain("MREF", "CRED");
        }

        @Test
        @DisplayName("liest die End-zu-Ende-Referenz getrennt vom Zweck")
        void liestEndeZuEndeReferenz() {
            Buchungszeile gutschrift = befund.auszuege().get(0).zeilen().get(1);

            assertThat(gutschrift.endeZuEndeReferenz()).isEqualTo("HONORAR-2026-08");
            assertThat(gutschrift.verwendungszweck()).isEqualTo("Honorar August 2026");
            assertThat(gutschrift.mandatsreferenz()).isNull();
        }

        @Test
        @DisplayName("setzt das Vorzeichen aus der Richtung, nicht aus dem Waehrungszeichen")
        void setztVorzeichenAusRichtung() {
            List<Buchungszeile> zeilen = befund.auszuege().get(0).zeilen();

            assertThat(zeilen.get(0).betrag()).isEqualTo(Betrag.von("-450.00"));
            assertThat(zeilen.get(1).betrag()).isEqualTo(Betrag.von("2500.00"));
        }

        @Test
        @DisplayName("nimmt die Bankreferenz hinter dem doppelten Schraegstrich")
        void nimmtBankreferenz() {
            assertThat(befund.auszuege().get(0).zeilen())
                    .extracting(Buchungszeile::bankreferenz)
                    .containsExactly("BREF0000000001", "BREF0000000002");
        }
    }

    /**
     * Die erste Falle. Das Zeichen nach C/D ist das dritte Zeichen von „EUR" und nicht die
     * Stornokennung. Wer {@code CR} als Storno liest, dreht jede zweite Buchung um.
     */
    @Nested
    @DisplayName("Storno gegen Waehrungsartefakt")
    class StornoGegenWaehrungsartefakt {

        private final Parsebefund befund = Mt940Parser.lies(Fixture.lies("mt940/storno.sta"));

        @Test
        @DisplayName("CR ist Haben in Euro und kein Storno")
        void crIstHabenUndKeinStorno() {
            Buchungszeile zeile = befund.auszuege().get(0).zeilen().get(0);

            assertThat(zeile.storno()).isFalse();
            assertThat(zeile.betrag()).isEqualTo(Betrag.von("120.00"));
        }

        @Test
        @DisplayName("RC ist ein Storno im Haben")
        void rcIstStornoImHaben() {
            Buchungszeile zeile = befund.auszuege().get(0).zeilen().get(1);

            assertThat(zeile.storno()).isTrue();
            assertThat(zeile.betrag()).isEqualTo(Betrag.von("75.50"));
        }

        @Test
        @DisplayName("RD ist ein Storno im Soll")
        void rdIstStornoImSoll() {
            Buchungszeile zeile = befund.auszuege().get(0).zeilen().get(2);

            assertThat(zeile.storno()).isTrue();
            assertThat(zeile.betrag()).isEqualTo(Betrag.von("-200.00"));
        }

        @Test
        @DisplayName("die Salden gehen trotzdem auf")
        void saldenGehenAuf() {
            assertThat(Auszugspruefung.pruefe(befund.auszuege())).isEmpty();
        }
    }

    /**
     * Die zweite Falle. Feld 61 liefert das Buchungsdatum nur als MMTT. Das Jahr der Valuta zu
     * übernehmen ist elf Monate im Jahr richtig.
     */
    @Nested
    @DisplayName("Buchungsdatum ohne Jahr")
    class BuchungsdatumOhneJahr {

        private final Parsebefund befund = Mt940Parser.lies(Fixture.lies("mt940/jahreswechsel.sta"));

        @Test
        @DisplayName("Buchung im Dezember, Valuta im Januar - das Vorjahr")
        void buchungImDezemberValutaImJanuar() {
            Buchungszeile zeile = befund.auszuege().get(0).zeilen().get(0);

            assertThat(zeile.valuta()).isEqualTo(LocalDate.of(2027, 1, 2));
            assertThat(zeile.buchungstag()).isEqualTo(LocalDate.of(2026, 12, 30));
        }

        @Test
        @DisplayName("Buchung im Januar, Valuta im Dezember - das Folgejahr")
        void buchungImJanuarValutaImDezember() {
            Buchungszeile zeile = befund.auszuege().get(0).zeilen().get(1);

            assertThat(zeile.valuta()).isEqualTo(LocalDate.of(2026, 12, 30));
            assertThat(zeile.buchungstag()).isEqualTo(LocalDate.of(2027, 1, 2));
        }

        @Test
        @DisplayName("innerhalb des Jahres bleibt es beim Jahr der Valuta")
        void innerhalbDesJahres() {
            assertThat(Mt940Parser.buchungstagAusValuta(LocalDate.of(2026, 8, 5), 8, 2))
                    .isEqualTo(LocalDate.of(2026, 8, 2));
        }

        @Test
        @DisplayName("der 29. Februar wird nicht in ein Nicht-Schaltjahr gezwungen")
        void neunundzwanzigsterFebruarImSchaltjahr() {
            // Valuta 01.03.2024 - 2024 ist ein Schaltjahr, der Tag existiert.
            assertThat(Mt940Parser.buchungstagAusValuta(LocalDate.of(2024, 3, 1), 2, 29))
                    .isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        @DisplayName("liegt kein Kandidat in der Naehe der Valuta, bleibt die Valuta stehen")
        void keinKandidatInDerNaehe() {
            // Valuta 01.03.2027. Weder 2026 noch 2027 haben einen 29. Februar; 2028 hat einen,
            // liegt aber ein Jahr entfernt. Ein Datum, das ein Jahr danebenliegt und trotzdem
            // plausibel aussieht, ist schlechter als die Valuta selbst.
            assertThat(Mt940Parser.buchungstagAusValuta(LocalDate.of(2027, 3, 1), 2, 29))
                    .isEqualTo(LocalDate.of(2027, 3, 1));
        }
    }

    /** Invariante I5. Zeilen brechen bei etwa 65 Zeichen um, gern mitten in einer IBAN. */
    @Nested
    @DisplayName("Zeilenumbruch mitten in einer IBAN")
    class ZeilenumbruchInIban {

        @Test
        @DisplayName("wird ohne Trennzeichen wieder zusammengefuegt")
        void wirdZusammengefuegt() {
            Parsebefund befund = Mt940Parser.lies(Fixture.lies("mt940/iban-umbruch.sta"));

            assertThat(befund.fehler()).isEmpty();
            Buchungszeile zeile = befund.auszuege().get(0).zeilen().get(0);

            assertThat(zeile.gegenpartei()).map(Iban::wert).contains("DE25100100100000765432");
            // Auch die Mandatsreferenz war ueber den Umbruch verteilt.
            assertThat(zeile.mandatsreferenz()).isEqualTo("MND-000123456");
            assertThat(zeile.glaeubigerkennung()).isEqualTo("DE55ZZZ00011122233");
        }

        @Test
        @DisplayName("eine falsche Pruefsumme meldet I5, statt still eine Phantasie-IBAN zu speichern")
        void falschePruefsummeMeldetI5() {
            Parsebefund befund = Mt940Parser.lies(Fixture.lies("mt940/iban-pruefsumme-falsch.sta"));

            assertThat(befund.fehler()).extracting(Importfehler::invariante).containsExactly(Invariante.I5);
            assertThat(befund.auszuege().get(0).zeilen().get(0).gegenpartei()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Fehlender Detailblock")
    class FehlenderDetailblock {

        @Test
        @DisplayName("meldet I3 und liefert die Buchung nicht aus")
        void meldetI3() {
            Parsebefund befund = Mt940Parser.lies(Fixture.lies("mt940/detailblock-fehlt.sta"));

            assertThat(befund.fehler()).extracting(Importfehler::invariante).contains(Invariante.I3);
            // Die zweite Buchung hat ihren Detailblock und kommt durch. Ohne die erste geht I1
            // nicht mehr auf - genau so soll es sein, der Auszug ist unvollstaendig.
            assertThat(befund.auszuege().get(0).zeilen()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Buchungen ohne Bankreferenz")
    class OhneBankreferenz {

        @Test
        @DisplayName("bekommen eine inhaltsstabile Ersatzreferenz statt aufeinander zu fallen")
        void bekommenErsatzreferenz() {
            String inhalt = Fixture.lies("mt940/ohne-bankreferenz.sta");
            List<Buchungszeile> zeilen =
                    Mt940Parser.lies(inhalt).auszuege().get(0).zeilen();

            assertThat(zeilen).hasSize(2);
            assertThat(zeilen).extracting(Buchungszeile::bankreferenz).doesNotHaveDuplicates();
            assertThat(zeilen.get(0).bankreferenz()).startsWith("ABGELEITET:");

            // Stabil ueber Laeufe hinweg. Ohne diese Eigenschaft erzeugt jeder erneute Import
            // Doubletten, weil I4 den Schluessel nicht wiedererkennt.
            List<Buchungszeile> zweiterLauf =
                    Mt940Parser.lies(inhalt).auszuege().get(0).zeilen();
            assertThat(zweiterLauf)
                    .extracting(Buchungszeile::bankreferenz)
                    .containsExactlyElementsOf(
                            zeilen.stream().map(Buchungszeile::bankreferenz).toList());
        }
    }
}
