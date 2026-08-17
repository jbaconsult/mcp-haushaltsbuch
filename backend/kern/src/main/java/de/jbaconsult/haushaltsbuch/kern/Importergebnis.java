package de.jbaconsult.haushaltsbuch.kern;

import java.util.List;
import java.util.Objects;

/**
 * Ergebnis eines Importlaufs.
 *
 * <p>Zwei Listen, weil beide gebraucht werden: was geschrieben wurde und was nicht durchkam. Ein
 * Ergebnis, das nur „erfolgreich" oder „fehlgeschlagen" sagt, ist bei einer Datei mit mehreren
 * Auszügen keine Antwort.
 *
 * @param geschrieben je Auszug, der die Prüfung bestanden hat
 * @param fehler die Fehlerliste aus {@code constraint.import-saldenvalidierung}. Was hier steht,
 *     steht nicht in der Datenbank
 */
public record Importergebnis(List<Auszugsergebnis> geschrieben, List<Importfehler> fehler) {

    public Importergebnis {
        geschrieben = List.copyOf(Objects.requireNonNull(geschrieben, "geschrieben darf nicht null sein"));
        fehler = List.copyOf(Objects.requireNonNull(fehler, "fehler darf nicht null sein"));
    }

    public boolean istSauber() {
        return fehler.isEmpty();
    }

    public int neueBuchungen() {
        return geschrieben.stream().mapToInt(Auszugsergebnis::neueBuchungen).sum();
    }

    public int uebersprungeneBuchungen() {
        return geschrieben.stream()
                .mapToInt(Auszugsergebnis::uebersprungeneBuchungen)
                .sum();
    }

    /** Ob ein bestimmter Befund vorliegt. Für Aufrufer, die auf eine Invariante reagieren wollen. */
    public boolean verletzt(Invariante invariante) {
        return fehler.stream().anyMatch(f -> f.invariante() == invariante);
    }
}
