package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;
import java.util.UUID;

/**
 * Eine benutzereditierbare Kategorie.
 *
 * <p>Eine Dimension am Split, <b>kein Konto</b>. Der Unterschied ist der Kern von ADR-0004: ein
 * Mechanismus sagt, dass jede Bewegung zwei Seiten hat; eine Taxonomie sagt, welche Sachkonten es
 * gibt. Dieses System braucht das Erste und will das Zweite nicht.
 *
 * @param gruppe die eine Ebene Gruppierung darüber. Von Anfang an vorhanden, weil eine flache Liste
 *     nach anderthalb Jahren dreißig Einträge hat und die Ebene dann eine Migration ist
 * @param aktiv inaktive Kategorien verschwinden aus der Auswahl, nicht aus der Historie
 */
public record Kategorie(KategorieId id, UUID gruppe, String bezeichnung, boolean aktiv) {

    public Kategorie {
        Objects.requireNonNull(id, "id darf nicht null sein");
        Objects.requireNonNull(gruppe, "gruppe darf nicht null sein");
        if (bezeichnung == null || bezeichnung.isBlank()) {
            throw new IllegalArgumentException("bezeichnung darf nicht leer sein");
        }
    }
}
