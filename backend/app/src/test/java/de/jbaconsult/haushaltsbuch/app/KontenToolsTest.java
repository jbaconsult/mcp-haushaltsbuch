package de.jbaconsult.haushaltsbuch.app;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.jbaconsult.haushaltsbuch.mcp.KontenTools;

/**
 * Prüft die MCP-Werkzeuge.
 *
 * <p>Laut HB-05 ist die MCP-Oberfläche die primäre Schnittstelle - sie verdient dieselbe
 * Testabdeckung wie REST, nicht weniger.
 */
@QuarkusTest
class KontenToolsTest {

    @Inject
    KontenTools kontenTools;

    @Test
    @DisplayName("listet die Konten des angemeldeten Benutzers")
    void listetKonten() {
        String ausgabe = kontenTools.kontenAuflisten();

        assertThat(ausgabe).contains("Haushalt gemeinsam");
        assertThat(ausgabe).doesNotContain("Giro Demo Zwei");
    }

    @Test
    @DisplayName("filtert nach Sphaere")
    void filtertNachSphaere() {
        String freiberuflich = kontenTools.kontenEinerSphaere("FREIBERUFLICH");

        assertThat(freiberuflich).contains("Geschaeft Demo Eins");
        assertThat(freiberuflich).doesNotContain("Haushalt gemeinsam");
    }

    @Test
    @DisplayName("erklaert eine unbekannte Sphaere, statt zu scheitern")
    void unbekannteSphaereWirdErklaert() {
        // Ein Modell, das eine Ausnahme bekommt, kann daraus nichts lernen. Ein Satz, der die
        // erlaubten Werte nennt, korrigiert den naechsten Versuch.
        String ausgabe = kontenTools.kontenEinerSphaere("GEWERBLICH");

        assertThat(ausgabe).contains("PRIVAT", "FREIBERUFLICH", "FINANZAMT");
    }
}
