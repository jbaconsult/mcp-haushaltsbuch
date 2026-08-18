package de.jbaconsult.haushaltsbuch.app;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prüft den Vorgang eines Bankzugangs über den ganzen Stapel.
 *
 * <p>Im Testprofil ist OIDC abgeschaltet, die Anfragen laufen als Demo Eins. Der Anbieter ist
 * durch {@link AnbieterAttrappe} ersetzt - ein Test gegen die echte Sandbox prüfte deren
 * Verfügbarkeit mit.
 */
@QuarkusTest
class BankzugangResourceTest {

    @Inject
    AnbieterAttrappe anbieter;

    @BeforeEach
    void zuruecksetzen() {
        anbieter.zuruecksetzen();
    }

    @Test
    @DisplayName("liefert die wählbaren Institute")
    void instituteLiefern() {
        given().when()
                .get("/api/bankzugaenge/institute")
                .then()
                .statusCode(200)
                .body("name", hasItem("Testbank"))
                .body("hoechsteGueltigkeitTage[0]", equalTo(180));
    }

    @Test
    @DisplayName("eine Autorisierung liefert die Weiterleitung und legt den Vorgang an")
    void autorisierungStarten() {
        String weiterleitung = given().contentType(ContentType.JSON)
                .body("""
                        {"institutName":"Testbank","institutLand":"DE"}""")
                .when()
                .post("/api/bankzugaenge")
                .then()
                .statusCode(200)
                .body("weiterleitung", notNullValue())
                .extract()
                .path("weiterleitung");

        assertThat(weiterleitung).contains("institut.invalid");
        assertThat(anbieter.letzterZustand()).isNotBlank();

        // Der Zustandswert steht in keiner Antwort dieses Systems - er geht ausschliesslich
        // ueber die Weiterleitung zum Institut.
        given().when()
                .get("/api/bankzugaenge")
                .then()
                .statusCode(200)
                .body(containsString("AUTORISIERUNG_LAEUFT"))
                .body(org.hamcrest.Matchers.not(containsString(anbieter.letzterZustand())));
    }

    @Test
    @DisplayName("ohne Institut wird die Anfrage abgewiesen")
    void ohneInstitutAbgewiesen() {
        given().contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/bankzugaenge")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("eine Rückleitung mit unbekanntem Zustandswert richtet nichts ein")
    void unbekannterZustandWirdAbgelehnt() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"zustand":"nie-vergeben","code":"egal"}""")
                .when()
                .post("/api/bankzugaenge/rueckleitung")
                .then()
                // 409 und nicht 500: der Vorgang ist gescheitert, nicht die Anwendung.
                .statusCode(409)
                .body("meldung", containsString("unbekannt"));
    }

    @Test
    @DisplayName("eine Rückleitung ohne Vorgangsbezug wird abgewiesen")
    void ohneZustandAbgewiesen() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"code":"egal"}""")
                .when()
                .post("/api/bankzugaenge/rueckleitung")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("eine erfolgreiche Rückleitung übernimmt Konten und Salden")
    void rueckleitungUebernimmtKonten() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"institutName":"Testbank","institutLand":"DE"}""")
                .when()
                .post("/api/bankzugaenge")
                .then()
                .statusCode(200);

        given().contentType(ContentType.JSON)
                .body("""
                        {"zustand":"%s","code":"code-eins"}""".formatted(anbieter.letzterZustand()))
                .when()
                .post("/api/bankzugaenge/rueckleitung")
                .then()
                .statusCode(200)
                .body("status", equalTo("AUTORISIERT"))
                .body("restgueltigkeitTage", notNullValue());

        // Gezielt das eigene Konto, nicht das erste der Liste: andere Tests legen ebenfalls
        // Konten an, und eine Zusicherung auf salden[0] haengt dann an der Reihenfolge der
        // Testausfuehrung statt an der Sache.
        given().when()
                .get("/api/bankzugaenge/konten")
                .then()
                .statusCode(200)
                .body("kennung", hasItem("stabil-eins"))
                .body("find { it.kennung == 'stabil-eins' }.salden[0].betrag", equalTo("1234.56"))
                .body("find { it.kennung == 'stabil-eins' }.salden[0].art", equalTo("GEBUCHT"));
    }

    @Test
    @DisplayName("ein Fehler des Instituts führt zu einem sichtbaren Fehlzustand")
    void fehlerFuehrtZuFehlzustand() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"institutName":"Testbank","institutLand":"DE"}""")
                .when()
                .post("/api/bankzugaenge")
                .then()
                .statusCode(200);

        given().contentType(ContentType.JSON)
                .body("""
                        {"zustand":"%s","fehler":"access_denied","fehlerbeschreibung":"vom Kunden abgebrochen"}""".formatted(anbieter.letzterZustand()))
                .when()
                .post("/api/bankzugaenge/rueckleitung")
                .then()
                .statusCode(200)
                .body("status", equalTo("FEHLGESCHLAGEN"))
                // Die Meldung des Anbieters wird angezeigt statt verschluckt.
                .body("fehlermeldung", containsString("vom Kunden abgebrochen"));
    }

    @Test
    @DisplayName("ein abgelehnter Autorisierungscode endet ebenfalls sichtbar")
    void abgelehnterCodeEndetSichtbar() {
        anbieter.fehlerBeimEroeffnen("Die Zustimmung wurde beim Institut abgelehnt.");

        given().contentType(ContentType.JSON)
                .body("""
                        {"institutName":"Testbank","institutLand":"DE"}""")
                .when()
                .post("/api/bankzugaenge")
                .then()
                .statusCode(200);

        given().contentType(ContentType.JSON)
                .body("""
                        {"zustand":"%s","code":"code-zwei"}""".formatted(anbieter.letzterZustand()))
                .when()
                .post("/api/bankzugaenge/rueckleitung")
                .then()
                .statusCode(200)
                .body("status", equalTo("FEHLGESCHLAGEN"))
                .body("fehlermeldung", containsString("abgelehnt"));
    }

    @Test
    @DisplayName("die Feldmessung berichtet und speichert nichts")
    void feldmessungBerichtet() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"institutName":"Testbank","institutLand":"DE"}""")
                .when()
                .post("/api/bankzugaenge")
                .then()
                .statusCode(200);

        given().contentType(ContentType.JSON)
                .body("""
                        {"zustand":"%s","code":"code-drei"}""".formatted(anbieter.letzterZustand()))
                .when()
                .post("/api/bankzugaenge/rueckleitung")
                .then()
                .statusCode(200);

        String kontoId = given().when()
                .get("/api/bankzugaenge/konten")
                .then()
                .statusCode(200)
                .extract()
                .path("find { it.kennung == 'stabil-eins' }.id");

        given().when()
                .post("/api/bankzugaenge/konten/" + kontoId + "/feldabdeckung")
                .then()
                .statusCode(200)
                .body("anzahlBuchungen", equalTo(1))
                .body("felder[0].name", equalTo("entry_reference"))
                .body("felder[0].bewertung", containsString("durchgehend"));
    }

    @Test
    @DisplayName("ein unbekanntes externes Konto liefert 404")
    void unbekanntesKontoErgibt404() {
        given().when()
                .get("/api/bankzugaenge/konten/00000000-0000-0000-0000-0000000000ff")
                .then()
                .statusCode(404);
    }
}
