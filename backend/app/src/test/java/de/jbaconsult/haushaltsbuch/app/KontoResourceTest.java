package de.jbaconsult.haushaltsbuch.app;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prüft die REST-Schicht über den ganzen Stapel.
 *
 * <p>Im Testprofil ist OIDC abgeschaltet und {@code haushaltsbuch.entwicklung.benutzer-subjekt}
 * steht auf {@code demo-benutzer-eins} - die Anfragen laufen also als Demo Eins. Dass dieser
 * Umweg ausschließlich in dev und test möglich ist, sperrt {@code BenutzerkontextFilter} über
 * {@code LaunchMode} ab.
 */
@QuarkusTest
class KontoResourceTest {

    @Test
    @DisplayName("liefert die Konten des angemeldeten Benutzers")
    void liefertKontenDesBenutzers() {
        given().when()
                .get("/api/konten")
                .then()
                .statusCode(200)
                .body("$", hasSize(4))
                .body("bezeichnung", hasItem("Haushalt gemeinsam"))
                // Der eigentliche Punkt: das fremde Konto taucht auch hier nicht auf.
                .body("bezeichnung", not(hasItem("Giro Demo Zwei")));
    }

    @Test
    @DisplayName("liefert 404 fuer ein fremdes Konto")
    void fremdesKontoErgibt404() {
        // Nicht 403: ein 403 wuerde bestaetigen, dass dieses Konto existiert.
        given().when()
                .get("/api/konten/10000000-0000-0000-0000-000000000005")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("liefert ein eigenes Konto")
    void eigenesKontoAbrufbar() {
        given().when()
                .get("/api/konten/10000000-0000-0000-0000-000000000001")
                .then()
                .statusCode(200)
                .body("art", org.hamcrest.Matchers.is("HAUSHALTSKONTO"))
                .body("sphaere", org.hamcrest.Matchers.is("PRIVAT"));
    }
}
