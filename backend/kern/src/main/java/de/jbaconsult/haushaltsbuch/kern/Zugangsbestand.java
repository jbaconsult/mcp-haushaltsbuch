package de.jbaconsult.haushaltsbuch.kern;

import java.util.List;

/**
 * Der aktuelle Bestand einer bestehenden Sitzung.
 *
 * <p>Wird gebraucht, weil die flüchtigen Kontoreferenzen mit der Sitzung wechseln: für einen Abruf
 * Wochen nach der Einrichtung müssen sie frisch geholt werden. Genau deshalb darf die flüchtige
 * Kennung nicht gespeichert sein.
 *
 * @param nochAutorisiert ob der Anbieter die Sitzung noch als autorisiert führt
 * @param konten Konten dieser Sitzung mit frischen Referenzen
 */
public record Zugangsbestand(boolean nochAutorisiert, List<Kontobefund> konten) {

    public Zugangsbestand {
        konten = konten == null ? List.of() : List.copyOf(konten);
    }

    /**
     * Die Sitzung besteht beim Anbieter nicht mehr.
     *
     * <p>Kein Fehler im Sinne einer Panne: Sitzungen laufen ab, werden widerrufen oder gelöscht.
     * Der Aufrufer führt den Status nach und lässt die gespeicherten Daten unangetastet.
     */
    public static Zugangsbestand nichtMehrAutorisiert() {
        return new Zugangsbestand(false, List.of());
    }
}
