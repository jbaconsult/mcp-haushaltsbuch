package de.jbaconsult.haushaltsbuch.app;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.jbaconsult.haushaltsbuch.mcp.BankkontenTools;

/**
 * Prüft die MCP-Werkzeuge zu Bankkonten.
 *
 * <p>Beide sind Klasse 1 nach ADR-0007: sie lesen aus dem gespeicherten Bestand und lösen keinen
 * Bankabruf aus. Genau das prüft der erste Test - die Attrappe zählt keine Eröffnung, weil keine
 * stattfindet.
 */
@QuarkusTest
class BankkontenToolsTest {

    @Inject
    BankkontenTools tools;

    @Inject
    AnbieterAttrappe anbieter;

    @Test
    @DisplayName("ohne eingerichteten Zugang erklärt das Werkzeug die Leere")
    void leereWirdErklaert() {
        String ausgabe = tools.bankkontenAuflisten();

        // Ein Modell, das nur "keine Konten" liest, schliesst auf ein leeres System. Der
        // wahrscheinlichere Grund ist ein fehlender Zugang - und das steht da.
        assertThat(ausgabe).contains("Bankzugang");
    }

    @Test
    @DisplayName("nach einer Autorisierung erscheinen Konten mit Saldo und Abrufzeitpunkt")
    void kontenMitSaldoUndZeitpunkt() {
        zugangEinrichten();

        String ausgabe = tools.bankkontenAuflisten();

        assertThat(ausgabe).contains("1234.56");
        assertThat(ausgabe).contains("gebucht");
        // Der Abrufzeitpunkt gehoert in jede Antwort: ein Saldo ohne Zeitangabe ist eine Zahl
        // ohne Aussage.
        assertThat(ausgabe).contains("abgerufen");
    }

    @Test
    @DisplayName("die Detailansicht nennt den Zustand des Bankzugangs")
    void detailsNennenZugangszustand() {
        zugangEinrichten();

        String ausgabe = tools.bankkontoDetails("stabil-eins");

        assertThat(ausgabe).contains("Bankzugang");
        assertThat(ausgabe).contains("AUTORISIERT");
        assertThat(ausgabe).contains("Testbank");
        // Der Hinweis auf die Kennzahl verfuegbar gehoert dazu: ein Kontostand beantwortet die
        // Frage "geht das oder nicht" nicht.
        assertThat(ausgabe).contains("verfügbar");
    }

    @Test
    @DisplayName("eine unbekannte Kennung wird erklärt, nicht erfunden")
    void unbekannteKennung() {
        String ausgabe = tools.bankkontoDetails("gibt-es-nicht");

        assertThat(ausgabe).contains("gibt-es-nicht");
        assertThat(ausgabe).contains("zu unterscheiden");
    }

    @Test
    @DisplayName("ohne Kennung verweist das Werkzeug auf die Auflistung")
    void ohneKennung() {
        assertThat(tools.bankkontoDetails("  ")).contains("bankkonten_auflisten");
    }

    /** Richtet über die REST-Schicht einen Zugang ein - denselben Weg, den ein Mensch nimmt. */
    private void zugangEinrichten() {
        anbieter.zuruecksetzen();

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {"institutName":"Testbank","institutLand":"DE"}""")
                .when()
                .post("/api/bankzugaenge")
                .then()
                .statusCode(200);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {"zustand":"%s","code":"code"}""".formatted(anbieter.letzterZustand()))
                .when()
                .post("/api/bankzugaenge/rueckleitung")
                .then()
                .statusCode(200);
    }
}
