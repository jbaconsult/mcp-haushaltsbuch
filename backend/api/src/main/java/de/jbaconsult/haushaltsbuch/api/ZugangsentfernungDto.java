package de.jbaconsult.haushaltsbuch.api;

import de.jbaconsult.haushaltsbuch.kern.Zugangsentfernung;

/**
 * Ergebnis eines entfernten Bankzugangs.
 *
 * <p>Die Antwort ist nicht leer, obwohl ein {@code 204 No Content} bequemer wäre. Das Entfernen
 * berührt zwei Systeme, und das zweite - die Sitzung beim Anbieter - kann fehlschlagen, ohne dass
 * der Vorgang insgesamt fehlschlägt. Ein leerer Erfolg würde diesen Fall verschweigen, und der
 * Mensch davor bliebe im Glauben, die Autorisierung sei widerrufen, während sie beim Anbieter
 * weiterläuft.
 *
 * @param sitzungBeendet ob die Autorisierung beim Anbieter widerrufen werden konnte
 * @param anbietermeldung Grund, falls nicht; sonst {@code null}
 * @param entfernteKonten Anzahl mitentfernter externer Konten
 * @param behalteneKonten Anzahl der Konten, die als Bestand stehen bleiben
 */
public record ZugangsentfernungDto(
        boolean sitzungBeendet, String anbietermeldung, int entfernteKonten, int behalteneKonten) {

    public static ZugangsentfernungDto von(Zugangsentfernung entfernung) {
        return new ZugangsentfernungDto(
                entfernung.sitzungBeendet(),
                entfernung.anbietermeldung().orElse(null),
                entfernung.entfernteKonten(),
                entfernung.geloesteKonten());
    }
}
