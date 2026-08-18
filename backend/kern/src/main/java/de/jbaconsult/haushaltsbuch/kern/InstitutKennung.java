package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;

/**
 * Kennung eines Instituts beim Anbieter.
 *
 * <p>Setzt sich aus Name und Land zusammen, weil dasselbe Institut in mehreren Ländern auftreten
 * kann. Der Aufbau ist Sache des Anbieters; das System behandelt den Wert als opak.
 */
public record InstitutKennung(String name, String land) {

    public InstitutKennung {
        Objects.requireNonNull(name, "name darf nicht null sein");
        Objects.requireNonNull(land, "land darf nicht null sein");
        if (name.isBlank() || land.isBlank()) {
            throw new IllegalArgumentException("Institutskennung braucht Name und Land");
        }
        land = land.trim().toUpperCase();
    }

    @Override
    public String toString() {
        return name + " (" + land + ")";
    }
}
