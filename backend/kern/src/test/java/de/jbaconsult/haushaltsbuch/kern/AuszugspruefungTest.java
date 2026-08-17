package de.jbaconsult.haushaltsbuch.kern;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * I1 und I2.
 *
 * <p>Ein Import, der sich nicht selbst validiert, gilt als nicht erfolgt - und die Prüfung muss
 * gegen die von der Bank gelieferten Salden laufen. Wer den Endsaldo aus den Buchungen ableitet,
 * bekommt eine Rechnung, die immer aufgeht.
 */
class AuszugspruefungTest {

    @Test
    @DisplayName("ein aufgehender Auszug erzeugt keinen Befund")
    void aufgehenderAuszugOhneBefund() {
        List<Kontoauszug> auszuege =
                Mt940Parser.lies(Fixture.lies("mt940/sauber.sta")).auszuege();

        assertThat(Auszugspruefung.pruefe(auszuege)).isEmpty();
    }

    @Test
    @DisplayName("ein Cent Abweichung im Endsaldo meldet I1")
    void einCentMeldetI1() {
        String verfaelscht =
                Fixture.lies("mt940/sauber.sta").replace(":62F:C260831EUR3050,00", ":62F:C260831EUR3050,01");

        List<Importfehler> fehler =
                Auszugspruefung.pruefe(Mt940Parser.lies(verfaelscht).auszuege());

        assertThat(fehler).hasSize(1);
        assertThat(fehler.get(0).invariante()).isEqualTo(Invariante.I1);
        assertThat(fehler.get(0).meldung()).contains("Differenz 0.01 EUR");
    }

    @Test
    @DisplayName("eine fehlende Buchung meldet I1 - genau dafuer gibt es die Invariante")
    void fehlendeBuchungMeldetI1() {
        List<Kontoauszug> auszuege =
                Mt940Parser.lies(Fixture.lies("mt940/detailblock-fehlt.sta")).auszuege();

        assertThat(Auszugspruefung.pruefe(auszuege))
                .extracting(Importfehler::invariante)
                .containsExactly(Invariante.I1);
    }

    @Test
    @DisplayName("eine geschlossene Blockkette erzeugt keinen Befund")
    void geschlosseneBlockketteOhneBefund() {
        List<Kontoauszug> auszuege =
                Mt940Parser.lies(Fixture.lies("mt940/blockkette.sta")).auszuege();

        assertThat(auszuege).hasSize(2);
        assertThat(Auszugspruefung.pruefe(auszuege)).isEmpty();
    }

    @Test
    @DisplayName("eine gebrochene Blockkette meldet I2 am spaeteren Block")
    void gebrocheneBlockketteMeldetI2() {
        List<Kontoauszug> auszuege =
                Mt940Parser.lies(Fixture.lies("mt940/blockkette-gebrochen.sta")).auszuege();

        List<Importfehler> fehler = Auszugspruefung.pruefe(auszuege);

        assertThat(fehler).hasSize(1);
        assertThat(fehler.get(0).invariante()).isEqualTo(Invariante.I2);
        // Der spaetere Block traegt den Befund: sein Anfangssaldo ist die Angabe, die nicht passt.
        assertThat(fehler.get(0).auszug()).isEqualTo("Auszug 00021");
    }
}
