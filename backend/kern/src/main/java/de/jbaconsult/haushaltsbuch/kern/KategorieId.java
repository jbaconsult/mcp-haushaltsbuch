package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;
import java.util.UUID;

/**
 * Fachlicher Bezeichner einer Kategorie.
 *
 * <p>Die Kennung ist der Grund, aus dem Kategorien umbenannt werden dürfen, ohne dass Regeln,
 * Prognosen und Historie brechen - ADR-0004 führt sie als erste von drei Wartungspflichten. Ein
 * Schlüssel über die Bezeichnung wäre bequemer und würde beim ersten Umbenennen die halbe Historie
 * verlieren.
 */
public record KategorieId(UUID wert) {

    public KategorieId {
        Objects.requireNonNull(wert, "wert darf nicht null sein");
    }

    public static KategorieId von(String wert) {
        return new KategorieId(UUID.fromString(wert));
    }

    @Override
    public String toString() {
        return wert.toString();
    }
}
