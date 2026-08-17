package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;
import java.util.UUID;

/** Fachlicher Bezeichner eines Kontos. Siehe {@link BenutzerId} zur Begründung des eigenen Typs. */
public record KontoId(UUID wert) {

    public KontoId {
        Objects.requireNonNull(wert, "wert darf nicht null sein");
    }

    public static KontoId von(String wert) {
        return new KontoId(UUID.fromString(wert));
    }

    @Override
    public String toString() {
        return wert.toString();
    }
}
