package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;
import java.util.UUID;

/** Fachlicher Bezeichner einer Buchung. Siehe {@link BenutzerId} zur Begründung des eigenen Typs. */
public record BuchungId(UUID wert) {

    public BuchungId {
        Objects.requireNonNull(wert, "wert darf nicht null sein");
    }

    public static BuchungId von(String wert) {
        return new BuchungId(UUID.fromString(wert));
    }

    @Override
    public String toString() {
        return wert.toString();
    }
}
