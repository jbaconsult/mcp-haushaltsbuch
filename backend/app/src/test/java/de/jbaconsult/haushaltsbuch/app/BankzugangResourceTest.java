package de.jbaconsult.haushaltsbuch.app;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;

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

    // ------------------------------------------------------- Zugang entfernen

    @Test
    @DisplayName("ein laufender Autorisierungsvorgang lässt sich abbrechen")
    void laufenderVorgangLaesstSichAbbrechen() {
        String id = laufenderVorgang();

        given().when()
                .delete("/api/bankzugaenge/" + id)
                .then()
                .statusCode(200)
                .body("sitzungBeendet", equalTo(false))
                .body("anbietermeldung", nullValue())
                .body("entfernteKonten", equalTo(0));

        assertThat(zugangsIds())
                .as("die Liste zeigt danach nichts von dem Versuch")
                .doesNotContain(id);

        assertThat(anbieter.sitzungenBeendet())
                .as("ein Vorgang ohne Sitzung hat beim Anbieter nichts zu widerrufen")
                .isZero();
    }

    @Test
    @DisplayName("ein entfernter Zugang widerruft die Autorisierung und lässt die Zahlen stehen")
    void entfernenBehaeltDieKonten() {
        String id = autorisierterZugang("code-entfernen-eins");

        given().when()
                .delete("/api/bankzugaenge/" + id)
                .then()
                .statusCode(200)
                .body("sitzungBeendet", equalTo(true))
                .body("entfernteKonten", equalTo(0))
                .body("behalteneKonten", equalTo(1));

        assertThat(zugangsIds()).doesNotContain(id);

        given().when()
                .get("/api/bankzugaenge/konten")
                .then()
                .statusCode(200)
                .body("kennung", hasItem("stabil-eins"))
                .body("find { it.kennung == 'stabil-eins' }.bankzugangId", nullValue())
                .body("find { it.kennung == 'stabil-eins' }.salden[0].betrag", equalTo("1234.56"));
    }

    @Test
    @DisplayName("mit konten=loeschen verschwinden Konten und Salden mit")
    void entfernenNimmtDieKontenMit() {
        String id = autorisierterZugang("code-entfernen-zwei");

        given().when()
                .delete("/api/bankzugaenge/" + id + "?konten=loeschen")
                .then()
                .statusCode(200)
                .body("entfernteKonten", equalTo(1))
                .body("behalteneKonten", equalTo(0));

        given().when()
                .get("/api/bankzugaenge/konten")
                .then()
                .statusCode(200)
                .body("findAll { it.kennung == 'stabil-eins' }", empty());
    }

    @Test
    @DisplayName("ein unbekannter Wert für konten wird abgewiesen statt geraten")
    void unbekannteKontenbehandlungAbgewiesen() {
        String id = laufenderVorgang();

        given().when()
                .delete("/api/bankzugaenge/" + id + "?konten=vielleicht")
                .then()
                .statusCode(400);

        assertThat(zugangsIds())
                .as("eine abgewiesene Anfrage darf nichts entfernt haben")
                .contains(id);

        given().when().delete("/api/bankzugaenge/" + id).then().statusCode(200);
    }

    @Test
    @DisplayName("ein Zugang, den es nicht gibt, lässt sich nicht entfernen")
    void unbekannterZugangLaesstSichNichtEntfernen() {
        given().when()
                .delete("/api/bankzugaenge/00000000-0000-0000-0000-0000000000ff")
                .then()
                .statusCode(409);
    }

    /**
     * Startet einen Vorgang und liefert dessen Kennung.
     *
     * <p>Ermittelt über die Differenz der Zugangsliste und nicht über „der neueste ist der erste":
     * die anderen Tests dieser Klasse legen ebenfalls Zugänge an, und bei gleicher Anlegezeit
     * entschiede sonst die Ausführungsreihenfolge über das Ergebnis.
     */
    private String laufenderVorgang() {
        List<String> vorher = zugangsIds();

        given().contentType(ContentType.JSON)
                .body("""
                        {"institutName":"Testbank","institutLand":"DE"}""")
                .when()
                .post("/api/bankzugaenge")
                .then()
                .statusCode(200);

        List<String> nachher = zugangsIds();
        nachher.removeAll(vorher);
        assertThat(nachher).hasSize(1);
        return nachher.get(0);
    }

    /** Richtet einen Zugang vollständig ein und liefert dessen Kennung. */
    private String autorisierterZugang(String code) {
        laufenderVorgang();

        return given().contentType(ContentType.JSON)
                .body("""
                        {"zustand":"%s","code":"%s"}""".formatted(anbieter.letzterZustand(), code))
                .when()
                .post("/api/bankzugaenge/rueckleitung")
                .then()
                .statusCode(200)
                .body("status", equalTo("AUTORISIERT"))
                .extract()
                .path("id");
    }

    private List<String> zugangsIds() {
        return new ArrayList<>(given().when()
                .get("/api/bankzugaenge")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("id", String.class));
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
