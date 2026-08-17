package de.jbaconsult.haushaltsbuch.kern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests der Geldarithmetik.
 *
 * <p>Läuft ohne Containerstart in Millisekunden - das ist der Grund, warum {@code kern}
 * frameworkfrei ist. Die kritischste Logik im System muss so schnell prüfbar sein, dass man sie
 * auch tatsächlich oft prüft.
 */
class BetragTest {

    @Test
    @DisplayName("normalisiert auf Skalierung 2")
    void normalisiertAufZweiNachkommastellen() {
        assertThat(Betrag.von("1").wert()).isEqualByComparingTo("1.00");
        assertThat(Betrag.von("1.5").wert().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("rundet kaufmännisch")
    void rundetHalfUp() {
        assertThat(Betrag.von("1.005").wert()).isEqualByComparingTo("1.01");
        assertThat(Betrag.von("1.004").wert()).isEqualByComparingTo("1.00");
        assertThat(Betrag.von("-1.005").wert()).isEqualByComparingTo("-1.01");
    }

    @Test
    @DisplayName("addiert und subtrahiert ohne Genauigkeitsverlust")
    void rechnetExakt() {
        Betrag summe = Betrag.von("0.10").plus(Betrag.von("0.20"));

        // In Gleitkomma wäre das 0.30000000000000004. Genau deshalb BigDecimal.
        assertThat(summe.wert()).isEqualByComparingTo("0.30");
        assertThat(summe.minus(Betrag.von("0.30"))).isEqualTo(Betrag.NULL_BETRAG);
    }

    @Test
    @DisplayName("vergleicht nach Wert, nicht nach Skalierung")
    void vergleichtNachWert() {
        // BigDecimal.equals() würde hier false liefern, weil es die Skalierung mitvergleicht.
        assertThat(new Betrag(new BigDecimal("1.0"))).isEqualTo(new Betrag(new BigDecimal("1.00")));
    }

    @Test
    @DisplayName("baut Beträge aus Euro und Cent")
    void bautAusEuroUndCent() {
        assertThat(Betrag.von(4779, 40).wert()).isEqualByComparingTo("4779.40");
        assertThat(Betrag.von(-600, 0).wert()).isEqualByComparingTo("-600.00");

        assertThatThrownBy(() -> Betrag.von(1, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("kennt sein Vorzeichen")
    void kenntVorzeichen() {
        assertThat(Betrag.von("-0.01").istNegativ()).isTrue();
        assertThat(Betrag.NULL_BETRAG.istNull()).isTrue();
        assertThat(Betrag.von("0.01").istPositiv()).isTrue();

        // Minus null ist null - der Sonderfall, der bei Vorzeichenlogik gern durchrutscht.
        assertThat(Betrag.NULL_BETRAG.negiert().istNegativ()).isFalse();
    }
}
