package de.jbaconsult.haushaltsbuch.kern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Invariante I5. Die Prüfsumme ist das Einzige, was einen falsch zusammengefügten Umbruch bemerkt. */
class IbanTest {

    @Test
    @DisplayName("eine gueltige IBAN wird angenommen")
    void gueltigeIbanWirdAngenommen() {
        assertThat(Iban.lesen("DE40123456780000123456")).map(Iban::wert).contains("DE40123456780000123456");
    }

    @Test
    @DisplayName("Leerraum und Kleinschreibung stoeren nicht")
    void leerraumUndKleinschreibungStoerenNicht() {
        assertThat(Iban.lesen("de40 1234 5678 0000 1234 56")).map(Iban::wert).contains("DE40123456780000123456");
    }

    @Test
    @DisplayName("eine falsche Pruefziffer wird abgelehnt")
    void falschePruefzifferWirdAbgelehnt() {
        // Letzte Stelle geaendert. Sieht aus wie eine IBAN, ist keine.
        assertThat(Iban.lesen("DE40123456780000123457")).isEmpty();
    }

    @Test
    @DisplayName("eine abgeschnittene IBAN wird abgelehnt - der Fall des Zeilenumbruchs")
    void abgeschnitteneIbanWirdAbgelehnt() {
        assertThat(Iban.lesen("DE401234567800")).isEmpty();
    }

    @Test
    @DisplayName("eine Kontonummer alter Bauart ist keine IBAN")
    void kontonummerAlterBauartIstKeineIban() {
        assertThat(Iban.lesen("0000123456")).isEmpty();
    }

    @Test
    @DisplayName("der Konstruktor laesst sich nicht mit ungeprueftem Text fuettern")
    void konstruktorLaesstSichNichtFuettern() {
        assertThatThrownBy(() -> new Iban("DE00000000000000000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keine gueltige IBAN");
    }
}
