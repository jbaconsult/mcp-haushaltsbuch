package de.jbaconsult.haushaltsbuch.persistenz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import de.jbaconsult.haushaltsbuch.kern.BenutzerId;
import de.jbaconsult.haushaltsbuch.kern.Benutzerkontext;

/**
 * Versetzt die laufende Datenbanktransaktion in den Zugriffskontext des angemeldeten Benutzers.
 *
 * <p>Zwei Dinge geschehen dabei, und beide sind nötig:
 *
 * <ol>
 *   <li><b>Rollenwechsel</b> in die Anwendungsrolle. Sie ist weder Eigentümer noch Superuser, also
 *       greifen die Policies überhaupt.
 *   <li><b>Benutzerkennung</b> in {@code app.benutzer_id}. Danach richten sich die Policies aus
 *       {@code V1__grundschema.sql}.
 * </ol>
 */
@ApplicationScoped
public class RlsKontext {

    /**
     * Die Rolle, unter der jede fachliche Abfrage läuft.
     *
     * <p>Fest im Code und nicht konfigurierbar: eine per Konfiguration austauschbare Rolle wäre eine
     * per Konfiguration abschaltbare Zugriffskontrolle.
     */
    private static final String ANWENDUNGSROLLE = "haushaltsbuch_app";

    private final EntityManager entityManager;
    private final Benutzerkontext benutzerkontext;

    @Inject
    public RlsKontext(EntityManager entityManager, Benutzerkontext benutzerkontext) {
        this.entityManager = entityManager;
        this.benutzerkontext = benutzerkontext;
    }

    /**
     * Setzt Rolle und Benutzerkennung für die laufende Transaktion.
     *
     * <p>Muss <b>innerhalb</b> einer Transaktion aufgerufen werden - beide Anweisungen gelten
     * transaktionslokal.
     *
     * <p>Warum transaktionslokal und nicht einfach an der Verbindung: Verbindungen kommen aus einem
     * Pool. Ein nicht-lokales {@code SET} bliebe an der Verbindung hängen und ginge mit ihr an den
     * nächsten Benutzer über, der sie bekommt. Dieser Fehler tritt im Test mit einer einzigen
     * Verbindung nie auf und unter Last sofort.
     *
     * <p>Warum der Rollenwechsel auch dann, wenn die Verbindung schon die richtige Rolle hat: im
     * Dev Mode und in Tests baut Quarkus die Verbindung über Dev Services als Superuser auf, und
     * ein Superuser umgeht Row-Level-Security immer - auch {@code FORCE} hilft dagegen nicht. Ohne
     * diesen Wechsel wäre die Zugriffskontrolle ausgerechnet dort wirkungslos, wo man sie beim
     * Entwickeln bemerken würde. Läuft die Verbindung bereits als {@code haushaltsbuch_app}, ist
     * der Wechsel ein Nichts-Tun.
     */
    public void anwenden() {
        // Kein Parameter moeglich: SET ROLE nimmt einen Bezeichner, keinen Wert. Der Name ist
        // deshalb eine Konstante und kommt nie von aussen.
        entityManager.createNativeQuery("SET LOCAL ROLE " + ANWENDUNGSROLLE).executeUpdate();

        String benutzer = benutzerkontext.benutzerId().map(BenutzerId::toString).orElse("");

        // set_config statt SET LOCAL, weil SET keine Parameterbindung erlaubt - und der Wert
        // stammt mittelbar aus einem Token.
        entityManager
                .createNativeQuery("SELECT set_config('app.benutzer_id', ?1, true)")
                .setParameter(1, benutzer)
                .getSingleResult();
    }
}
