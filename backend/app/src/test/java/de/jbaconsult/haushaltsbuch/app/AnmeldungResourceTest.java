package de.jbaconsult.haushaltsbuch.app;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prüft die Auskunft über die eigene Anmeldung.
 *
 * <p>Der Zustand, um den es geht, ist Abnahmekriterium 3 des Auftrags: <b>angemeldet, aber keinem
 * fachlichen Benutzer zugeordnet</b>. Er ist der teuerste in der ganzen Anmeldung, weil alles
 * funktioniert und trotzdem nichts zu sehen ist - die Row-Level-Security liefert fail-closed null
 * Zeilen, und das sieht aus wie ein Rechteproblem.
 *
 * <p>{@code @TestSecurity} setzt dafür eine Identität, ohne dass ein Identity Provider läuft. Ohne
 * sie wäre im Testprofil jede Identität entweder anonym oder über die Entwicklungsproperty bereits
 * zugeordnet - genau der interessante Fall käme nie vor.
 */
@QuarkusTest
class AnmeldungResourceTest {

    @Test
    @DisplayName("ohne Anmeldung gilt der Demo-Benutzer des Entwicklungsprofils als zugeordnet")
    void entwicklungsprofilIstZugeordnet() {
        // Das Testprofil arbeitet ohne OIDC mit haushaltsbuch.entwicklung.benutzer-subjekt.
        // Dieser Test hält fest, dass die Auskunft dort nicht faelschlich "nicht angemeldet"
        // meldet, obwohl die ganze Anwendung laeuft.
        given().when()
                .get("/api/ich")
                .then()
                .statusCode(200)
                .body("angemeldet", equalTo(true))
                .body("zugeordnet", equalTo(true));
    }

    @Test
    @TestSecurity(user = "ein-fremdes-oidc-subjekt")
    @DisplayName("ein angemeldeter, nicht zugeordneter Benutzer wird als solcher gemeldet")
    void angemeldetAberNichtZugeordnet() {
        given().when()
                .get("/api/ich")
                .then()
                .statusCode(200)
                .body("angemeldet", equalTo(true))
                .body("zugeordnet", equalTo(false))
                // Der Subject kommt zurück, damit er sich eintragen lässt, statt ihn aus einem
                // Protokoll fischen zu müssen.
                .body("subjekt", equalTo("ein-fremdes-oidc-subjekt"));
    }

    @Test
    @TestSecurity(user = "ein-fremdes-oidc-subjekt")
    @DisplayName("eine nicht zugeordnete Anmeldung sieht keine Konten - und legt auch keine an")
    void nichtZugeordnetSiehtNichts() {
        // Die andere Hälfte desselben Zustands: Der Hinweis ist nötig, WEIL hier nichts kommt.
        given().when().get("/api/konten").then().statusCode(200).body("$", hasSize(0));

        // Keine Selbstregistrierung: Der Aufruf oben hat keinen fachlichen Benutzer erzeugt.
        // Waere das anders, bekaeme jeder, der sich am Realm anmelden kann, damit Zugriff.
        given().when().get("/api/ich").then().statusCode(200).body("zugeordnet", equalTo(false));
    }

    @Test
    @TestSecurity(user = "demo-benutzer-eins")
    @DisplayName("ein zugeordnetes Subjekt sieht seine Konten")
    void zugeordnetSiehtKonten() {
        given().when()
                .get("/api/ich")
                .then()
                .statusCode(200)
                .body("angemeldet", equalTo(true))
                .body("zugeordnet", equalTo(true))
                .body("subjekt", equalTo("demo-benutzer-eins"));

        given().when().get("/api/konten").then().statusCode(200).body("$.size()", org.hamcrest.Matchers.greaterThan(0));
    }

    @Test
    @DisplayName("die Auskunft verrät ohne Anmeldung kein Subjekt")
    void ohneAnmeldungKeinSubjekt() {
        // Im Testprofil ist die Identitaet anonym; das Subjekt stammt aus der
        // Entwicklungsproperty und gehoert nicht in die Antwort.
        given().when().get("/api/ich").then().statusCode(200).body("subjekt", nullValue());
    }
}
