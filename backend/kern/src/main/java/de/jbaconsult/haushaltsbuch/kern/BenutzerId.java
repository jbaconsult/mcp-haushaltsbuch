package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;
import java.util.UUID;

/**
 * Fachlicher Bezeichner eines Benutzers.
 *
 * <p>Eigener Typ statt roher {@link UUID}, damit der Compiler eine Verwechslung mit
 * {@link KontoId} findet - und nicht der Steuerberater.
 */
public record BenutzerId(UUID wert) {

    public BenutzerId {
        Objects.requireNonNull(wert, "wert darf nicht null sein");
    }

    public static BenutzerId von(String wert) {
        return new BenutzerId(UUID.fromString(wert));
    }

    @Override
    public String toString() {
        return wert.toString();
    }
}
