package de.jbaconsult.haushaltsbuch.kern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KontoTest {

    private static Konto konto(Kontoart art, Sphaere sphaere) {
        return new Konto(new KontoId(UUID.randomUUID()), "Testkonto", art, sphaere);
    }

    @Test
    @DisplayName("Haushaltskonto hat keine Kreditlinie")
    void haushaltskontoOhneKreditlinie() {
        // Bindend: Finanzierung vor Belastung, Speisung vor dem Belastungstermin,
        // Mandate in aufsteigender Schadenshöhe.
        assertThat(konto(Kontoart.HAUSHALTSKONTO, Sphaere.PRIVAT).hatKreditlinie())
                .isFalse();
        assertThat(konto(Kontoart.GIROKONTO, Sphaere.PRIVAT).hatKreditlinie()).isTrue();
    }

    @Test
    @DisplayName("verlangt eine Bezeichnung")
    void verlangtBezeichnung() {
        KontoId id = new KontoId(UUID.randomUUID());

        assertThatThrownBy(() -> new Konto(id, "  ", Kontoart.GIROKONTO, Sphaere.PRIVAT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("KontoId und BenutzerId sind nicht verwechselbar")
    void bezeichnerSindNichtVerwechselbar() {
        UUID rohwert = UUID.randomUUID();

        // Gleicher Rohwert, verschiedene Typen. Eine Verwechslung fällt beim Kompilieren auf -
        // das ist der ganze Zweck der eigenen Typen.
        assertThat((Object) new KontoId(rohwert)).isNotEqualTo(new BenutzerId(rohwert));
    }
}
