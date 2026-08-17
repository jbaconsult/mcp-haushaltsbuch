package de.jbaconsult.haushaltsbuch.kern;

import java.util.List;
import java.util.Optional;

/**
 * Zugang zu Konten aus Sicht der Domäne.
 *
 * <p>Der Port wird hier definiert und in {@code persistenz} implementiert. Damit zeigt die
 * Abhängigkeit nach innen: {@code kern} kennt niemanden.
 *
 * <p><b>Wichtig zur Zugriffskontrolle:</b> Die Methoden liefern ausschließlich Konten, auf die der
 * aktuelle Benutzer Zugriff hat. Das ist keine Zusage der Implementierung, sondern eine Eigenschaft
 * der Datenbank - Row-Level-Security filtert unterhalb jeder Abfrage. Ist kein Benutzerkontext
 * gesetzt, kommt nichts zurück.
 */
public interface KontoPort {

    /** Alle für den aktuellen Benutzer sichtbaren Konten. */
    List<Konto> alleSichtbaren();

    /**
     * Ein Konto, sofern es existiert <b>und</b> der aktuelle Benutzer es sehen darf.
     *
     * <p>Beides ist von außen nicht unterscheidbar, und das ist Absicht: ein „existiert, aber du
     * darfst nicht" verrät die Existenz eines fremden Kontos.
     */
    Optional<Konto> findeNachId(KontoId id);
}
