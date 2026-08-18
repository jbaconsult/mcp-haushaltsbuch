package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;

/**
 * Flüchtiger Verweis auf ein Konto innerhalb einer laufenden Anbietersitzung.
 *
 * <p>Bei Enable Banking ist das die {@code uid}. Sie ist laut Dokumentation nur gültig, solange die
 * Sitzung autorisiert ist, und sie ändert sich mit der nächsten.
 *
 * <p><b>Diese Kennung wird niemals persistiert.</b> Sie entsteht beim Öffnen der Sitzung, wird für
 * Abrufe innerhalb desselben Vorgangs verwendet und verfällt danach. Der dauerhafte Schlüssel ist
 * {@link Kontokennung}.
 *
 * <p>Der Typ existiert genau deshalb: ein {@code String} an dieser Stelle wäre von einem
 * dauerhaften Schlüssel nicht zu unterscheiden, und irgendwann landet er in einer Spalte.
 */
public record Kontoreferenz(String fluechtigeKennung) {

    public Kontoreferenz {
        Objects.requireNonNull(fluechtigeKennung, "fluechtigeKennung darf nicht null sein");
        if (fluechtigeKennung.isBlank()) {
            throw new IllegalArgumentException("fluechtigeKennung darf nicht leer sein");
        }
    }

    /**
     * Bewusst ohne den Wert.
     *
     * <p>Eine flüchtige Kennung, die in Protokollen auftaucht, wird dort gelesen, kopiert und
     * irgendwann verwendet - lange nachdem sie ungültig geworden ist.
     */
    @Override
    public String toString() {
        return "Kontoreferenz[fluechtig]";
    }
}
