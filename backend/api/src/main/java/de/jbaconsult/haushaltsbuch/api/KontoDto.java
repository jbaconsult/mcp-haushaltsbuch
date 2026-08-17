package de.jbaconsult.haushaltsbuch.api;

import de.jbaconsult.haushaltsbuch.kern.Konto;

/**
 * Konto in der Darstellung für das Dashboard.
 *
 * <p>Eigener Typ, nicht das Domänenobjekt selbst. Sonst zöge jede Änderung an der Domäne
 * stillschweigend eine Änderung am öffentlichen Vertrag nach sich - und ein Feld, das intern
 * umbenannt wird, bräche das Frontend.
 */
public record KontoDto(String id, String bezeichnung, String art, String sphaere) {

    public static KontoDto von(Konto konto) {
        return new KontoDto(
                konto.id().toString(),
                konto.bezeichnung(),
                konto.art().name(),
                konto.sphaere().name());
    }
}
