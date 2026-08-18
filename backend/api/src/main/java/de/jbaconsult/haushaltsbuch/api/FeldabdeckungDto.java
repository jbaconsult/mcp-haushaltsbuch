package de.jbaconsult.haushaltsbuch.api;

import java.util.List;

import de.jbaconsult.haushaltsbuch.kern.Feldabdeckung;

/**
 * Ergebnis der einmaligen Feldmessung.
 *
 * <p>Ein Bericht, kein Datenbestand. Nichts davon wird gespeichert - Buchungen gelangen
 * ausschließlich über den Importdienst in dieses System, weil sie dort gegen die
 * Saldeninvarianten geprüft werden.
 */
public record FeldabdeckungDto(int anzahlBuchungen, List<FeldDto> felder, List<String> hinweise) {

    public record FeldDto(String name, String herkunft, int belegt, int gesamt, String bewertung) {}

    public static FeldabdeckungDto von(Feldabdeckung abdeckung) {
        return new FeldabdeckungDto(
                abdeckung.anzahlBuchungen(),
                abdeckung.felder().stream()
                        .map(feld -> new FeldDto(
                                feld.name(), feld.herkunft(), feld.belegt(), feld.gesamt(), feld.bewertung()))
                        .toList(),
                abdeckung.hinweise());
    }
}
