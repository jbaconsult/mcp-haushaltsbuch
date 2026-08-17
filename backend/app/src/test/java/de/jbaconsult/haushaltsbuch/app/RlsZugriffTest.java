package de.jbaconsult.haushaltsbuch.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.jbaconsult.haushaltsbuch.kern.BenutzerId;
import de.jbaconsult.haushaltsbuch.kern.Benutzerkontext;
import de.jbaconsult.haushaltsbuch.kern.Konto;
import de.jbaconsult.haushaltsbuch.kern.KontoId;
import de.jbaconsult.haushaltsbuch.kern.KontoService;

/**
 * Weist nach, dass die zeilenbasierte Zugriffskontrolle wirkt.
 *
 * <p>Das ist der Test, an dem die Existenzberechtigung dieses Systems hängt: die harte Anforderung
 * aus HB-05 lautet, dass sich beide Ehepartner anmelden können und jeder nur seine Sachen sieht.
 * Genau daran sind Firefly III und Actual Budget gescheitert.
 *
 * <p>Der Test läuft gegen echtes PostgreSQL - Quarkus Dev Services startet es. Eine
 * In-Memory-Datenbank wäre hier wertlos, weil keine davon Row-Level-Security kennt.
 *
 * <p>Grundlage sind die Demodaten aus {@code V900__demodaten.sql}: Demo Eins hat Zugriff auf vier
 * Konten, Demo Zwei auf zwei, und {@code Giro Demo Zwei} gehört ausschließlich Demo Zwei.
 */
@QuarkusTest
class RlsZugriffTest {

    private static final BenutzerId DEMO_EINS = BenutzerId.von("00000000-0000-0000-0000-000000000001");
    private static final BenutzerId DEMO_ZWEI = BenutzerId.von("00000000-0000-0000-0000-000000000002");

    private static final KontoId GIRO_DEMO_ZWEI = KontoId.von("10000000-0000-0000-0000-000000000005");
    private static final KontoId HAUSHALT_GEMEINSAM = KontoId.von("10000000-0000-0000-0000-000000000001");

    @Inject
    KontoService kontoService;

    @Inject
    Benutzerkontext benutzerkontext;

    @Test
    @DisplayName("ohne Benutzerkontext ist nichts sichtbar")
    void ohneKontextNichtsSichtbar() {
        // Kein setzen() - der Kontext bleibt leer.
        assertThat(kontoService.sichtbareKonten()).isEmpty();
        assertThat(kontoService.konto(HAUSHALT_GEMEINSAM)).isEmpty();
    }

    @Test
    @DisplayName("jeder sieht genau seine eigenen Konten")
    void jederSiehtNurSeineKonten() {
        benutzerkontext.setzen(DEMO_EINS);
        List<Konto> vonEins = kontoService.sichtbareKonten();

        assertThat(vonEins).hasSize(4);
        assertThat(vonEins).extracting(Konto::bezeichnung).doesNotContain("Giro Demo Zwei");

        benutzerkontext.setzen(DEMO_ZWEI);
        List<Konto> vonZwei = kontoService.sichtbareKonten();

        assertThat(vonZwei).hasSize(2);
        assertThat(vonZwei)
                .extracting(Konto::bezeichnung)
                .containsExactlyInAnyOrder("Haushalt gemeinsam", "Giro Demo Zwei");
    }

    @Test
    @DisplayName("ein fremdes Konto ist nicht abrufbar, auch nicht mit bekannter Kennung")
    void fremdesKontoNichtAbrufbar() {
        benutzerkontext.setzen(DEMO_EINS);

        // Die Kennung ist bekannt - das ist der interessante Fall. Wer eine Kennung errät oder
        // aus einem alten Protokoll hat, darf trotzdem nichts sehen.
        Optional<Konto> fremd = kontoService.konto(GIRO_DEMO_ZWEI);

        assertThat(fremd).isEmpty();
    }

    @Test
    @DisplayName("das gemeinsame Konto sehen beide")
    void gemeinsamesKontoSehenBeide() {
        benutzerkontext.setzen(DEMO_EINS);
        assertThat(kontoService.konto(HAUSHALT_GEMEINSAM)).isPresent();

        benutzerkontext.setzen(DEMO_ZWEI);
        assertThat(kontoService.konto(HAUSHALT_GEMEINSAM)).isPresent();
    }

    @Test
    @DisplayName("ein unbekannter Benutzer sieht nichts")
    void unbekannterBenutzerSiehtNichts() {
        // Gueltige UUID, aber kein Eintrag in kontozugriff. Fail-Closed heisst: leer, nicht Fehler.
        benutzerkontext.setzen(BenutzerId.von("99999999-9999-9999-9999-999999999999"));

        assertThat(kontoService.sichtbareKonten()).isEmpty();
    }
}
