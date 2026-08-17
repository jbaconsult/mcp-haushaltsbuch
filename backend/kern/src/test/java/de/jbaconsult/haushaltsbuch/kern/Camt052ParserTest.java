package de.jbaconsult.haushaltsbuch.kern;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CAMT.052. Die Felder kommen strukturiert an - das entbindet nicht davon, sie richtig zuzuordnen. */
class Camt052ParserTest {

    private final Parsebefund befund = Camt052Parser.lies(Fixture.lies("camt052/sauber.xml"));

    @Test
    @DisplayName("ein sauberer Report wird ohne Befund gelesen")
    void sauberOhneBefund() {
        assertThat(befund.fehler()).isEmpty();
        assertThat(befund.auszuege()).hasSize(1);
    }

    @Test
    @DisplayName("Salden und Zeitraum stammen aus OPBD und CLBD")
    void saldenAusOpbdUndClbd() {
        Kontoauszug report = befund.auszuege().get(0);

        assertThat(report.auszugsnummer()).isEqualTo("8");
        assertThat(report.konto()).map(Iban::wert).contains("DE04500500500000334455");
        assertThat(report.anfangssaldo()).isEqualTo(Betrag.von("2000.00"));
        assertThat(report.endsaldo()).isEqualTo(Betrag.von("2430.50"));
        assertThat(report.von()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(report.bis()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("die Salden gehen auf")
    void saldenGehenAuf() {
        assertThat(Auszugspruefung.pruefe(befund.auszuege())).isEmpty();
    }

    @Test
    @DisplayName("Mandatsreferenz, Glaeubigerkennung und Gegenpartei-IBAN stehen einzeln")
    void strukturierteFelderEinzeln() {
        Buchungszeile lastschrift = befund.auszuege().get(0).zeilen().get(0);

        assertThat(lastschrift.mandatsreferenz()).isEqualTo("MND-000998877");
        assertThat(lastschrift.glaeubigerkennung()).isEqualTo("DE77ZZZ00000111222");
        assertThat(lastschrift.gegenpartei()).map(Iban::wert).contains("DE25100100100000765432");
        assertThat(lastschrift.gegenparteiName()).isEqualTo("Beispiel Strom AG");
        assertThat(lastschrift.endeZuEndeReferenz()).isEqualTo("E2E-2026-08-12-0001");
        assertThat(lastschrift.verwendungszweck()).isEqualTo("Abschlag Strom August 2026");
        assertThat(lastschrift.betrag()).isEqualTo(Betrag.von("-119.50"));
    }

    /**
     * Bei einer Gutschrift ist die Gegenpartei der Schuldner, nicht der Gläubiger. Immer dasselbe
     * Element zu lesen verliert eine Richtung vollständig - und mit ihr jede Einnahme.
     */
    @Test
    @DisplayName("bei einer Gutschrift ist die Gegenpartei der Schuldner")
    void gutschriftLiestDenSchuldner() {
        Buchungszeile gutschrift = befund.auszuege().get(0).zeilen().get(1);

        assertThat(gutschrift.betrag()).isEqualTo(Betrag.von("500.00"));
        assertThat(gutschrift.gegenparteiName()).isEqualTo("Beispiel Auftraggeber");
        assertThat(gutschrift.gegenpartei()).map(Iban::wert).contains("DE79200200200000112233");
        assertThat(gutschrift.storno()).isFalse();
    }

    @Test
    @DisplayName("RvslInd kennzeichnet ein Storno")
    void rvslIndIstStorno() {
        Buchungszeile storno = befund.auszuege().get(0).zeilen().get(2);

        assertThat(storno.storno()).isTrue();
        assertThat(storno.betrag()).isEqualTo(Betrag.von("50.00"));
        // Bei einer Ruecklastschrift fuehrt die Bank weiter den urspruenglichen Glaeubiger.
        assertThat(storno.gegenparteiName()).isEqualTo("Beispiel Handel");
        assertThat(storno.glaeubigerkennung()).isEqualTo("DE22ZZZ00000333444");
    }

    @Test
    @DisplayName("AcctSvcrRef ist die Bankreferenz")
    void acctSvcrRefIstBankreferenz() {
        assertThat(befund.auszuege().get(0).zeilen())
                .extracting(Buchungszeile::bankreferenz)
                .containsExactly("CAMTREF00000001", "CAMTREF00000002", "CAMTREF00000003");
    }

    @Test
    @DisplayName("ein Eintrag ohne NtryDtls meldet I3")
    void eintragOhneDetailsMeldetI3() {
        Parsebefund ohneDetails = Camt052Parser.lies(Fixture.lies("camt052/ohne-detailblock.xml"));

        assertThat(ohneDetails.fehler()).extracting(Importfehler::invariante).containsExactly(Invariante.I3);
        assertThat(ohneDetails.auszuege().get(0).zeilen()).isEmpty();
    }

    /**
     * Eine Bankdatei ist Fremdeingabe. Ein XML-Leser, der DOCTYPE akzeptiert, liest auf Zuruf
     * lokale Dateien - dieselbe Klasse Fehler, wegen der XXE einen Namen hat.
     */
    @Test
    @DisplayName("eine Datei mit DOCTYPE wird abgelehnt, statt externe Verweise aufzuloesen")
    void doctypeWirdAbgelehnt() {
        String mitDoctype = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Document [<!ENTITY x "unbedenklich">]>
                <Document><BkToCstmrAcctRpt><Rpt><Id>&x;</Id></Rpt></BkToCstmrAcctRpt></Document>
                """;

        List<Importfehler> fehler = Camt052Parser.lies(mitDoctype).fehler();

        assertThat(fehler).isNotEmpty();
        assertThat(fehler.get(0).invariante()).isEqualTo(Invariante.I3);
    }
}
