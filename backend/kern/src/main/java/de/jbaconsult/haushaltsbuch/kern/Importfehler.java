package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;

/**
 * Ein Befund aus der Importprüfung.
 *
 * <p>Die Fehlerliste ist kein Protokoll, sondern das Ergebnis. Ein Auszug mit mindestens einem
 * Befund wird nicht geschrieben - {@code constraint.import-saldenvalidierung}.
 *
 * @param invariante die verletzte Invariante, ausdrücklich benannt
 * @param auszug Bezeichnung des betroffenen Auszugs, damit der Befund zuordenbar ist
 * @param meldung was konkret nicht aufging, mit Zahlen statt mit Adjektiven
 */
public record Importfehler(Invariante invariante, String auszug, String meldung) {

    public Importfehler {
        Objects.requireNonNull(invariante, "invariante darf nicht null sein");
        Objects.requireNonNull(auszug, "auszug darf nicht null sein");
        Objects.requireNonNull(meldung, "meldung darf nicht null sein");
    }

    @Override
    public String toString() {
        return invariante + " (" + invariante.beschreibung() + ") in " + auszug + ": " + meldung;
    }
}
