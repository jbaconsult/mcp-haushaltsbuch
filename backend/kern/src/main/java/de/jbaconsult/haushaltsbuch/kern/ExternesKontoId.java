package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;
import java.util.UUID;

/** Kennung eines externen Kontos innerhalb dieses Systems. */
public record ExternesKontoId(UUID wert) {

    public ExternesKontoId {
        Objects.requireNonNull(wert, "wert darf nicht null sein");
    }

    public static ExternesKontoId neu() {
        return new ExternesKontoId(UUID.randomUUID());
    }

    public static ExternesKontoId von(String wert) {
        return new ExternesKontoId(UUID.fromString(wert));
    }

    @Override
    public String toString() {
        return wert.toString();
    }
}
