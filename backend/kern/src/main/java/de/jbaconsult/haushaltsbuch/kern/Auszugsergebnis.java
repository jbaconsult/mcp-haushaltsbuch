package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;

/**
 * Was das Schreiben eines Auszugs bewirkt hat.
 *
 * <p>Die Trennung zwischen {@code neueBuchungen} und {@code uebersprungeneBuchungen} ist die
 * sichtbare Seite von I4. Ein zweiter Lauf derselben Datei meldet null neue und alle übersprungen -
 * und wer das nicht unterscheiden kann, weiß nach dem zweiten Lauf nicht, ob nichts passiert ist,
 * weil alles schon da war, oder weil nichts gelesen wurde.
 *
 * @param bereitsVorhanden ob der Auszug selbst schon gespeichert war
 */
public record Auszugsergebnis(String auszug, int neueBuchungen, int uebersprungeneBuchungen, boolean bereitsVorhanden) {

    public Auszugsergebnis {
        Objects.requireNonNull(auszug, "auszug darf nicht null sein");
    }

    public int gelesenenBuchungen() {
        return neueBuchungen + uebersprungeneBuchungen;
    }
}
