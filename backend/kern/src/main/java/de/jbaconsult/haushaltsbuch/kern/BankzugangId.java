package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;
import java.util.UUID;

/** Kennung eines Bankzugangs. */
public record BankzugangId(UUID wert) {

    public BankzugangId {
        Objects.requireNonNull(wert, "wert darf nicht null sein");
    }

    public static BankzugangId neu() {
        return new BankzugangId(UUID.randomUUID());
    }

    public static BankzugangId von(String wert) {
        return new BankzugangId(UUID.fromString(wert));
    }

    @Override
    public String toString() {
        return wert.toString();
    }
}
