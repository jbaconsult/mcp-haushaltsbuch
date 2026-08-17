package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;
import java.util.UUID;

/**
 * Fachlicher Bezeichner einer Geldbewegung.
 *
 * <p>Zwei Buchungen mit derselben Bewegungskennung sind die beiden Seiten <em>einer</em> Bewegung -
 * die Sammelabbuchung der Karte und ihr Ausgleich, eine Privatentnahme, die Speisung des
 * Haushaltskontos. Die Datenbank hält dafür zwei Bedingungen fest: die Seiten ergänzen sich zu null,
 * und keine von ihnen trägt eine Kategorie.
 */
public record BewegungId(UUID wert) {

    public BewegungId {
        Objects.requireNonNull(wert, "wert darf nicht null sein");
    }

    public static BewegungId von(String wert) {
        return new BewegungId(UUID.fromString(wert));
    }

    @Override
    public String toString() {
        return wert.toString();
    }
}
