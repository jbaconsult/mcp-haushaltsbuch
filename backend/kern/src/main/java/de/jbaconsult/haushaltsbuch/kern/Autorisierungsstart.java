package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;

/**
 * Ergebnis des Autorisierungsstarts: die Adresse, an die der Mensch geschickt wird.
 *
 * <p>Mehr kommt an dieser Stelle nicht zurück, und mehr wird auch nicht gebraucht. Alles Weitere
 * entsteht erst, wenn er zurückkommt.
 */
public record Autorisierungsstart(String weiterleitung) {

    public Autorisierungsstart {
        Objects.requireNonNull(weiterleitung, "weiterleitung darf nicht null sein");
        if (weiterleitung.isBlank()) {
            throw new IllegalArgumentException("weiterleitung darf nicht leer sein");
        }
    }
}
