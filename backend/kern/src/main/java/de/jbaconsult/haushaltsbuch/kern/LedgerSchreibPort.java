package de.jbaconsult.haushaltsbuch.kern;

/**
 * Der Weg eines geprüften Auszugs in den Datenbestand.
 *
 * <p>Der Port wird hier definiert und in {@code persistenz} implementiert - die Abhängigkeit zeigt
 * nach innen, {@code kern} kennt niemanden.
 *
 * <p><b>Die Zusage dieser Schnittstelle ist Ganz-oder-gar-nicht.</b> Ein Aufruf schreibt einen
 * Auszug vollständig oder hinterlässt nichts. Ein Implementierer, der neun von zehn Buchungen
 * schreibt und die zehnte meldet, verletzt sie - ein solcher Teilbestand ist schlimmer als gar
 * keiner, weil er aussieht wie ein vollständiger.
 *
 * <p>Die Prüfung der Invarianten I1 bis I3 und I5 liegt <b>vor</b> diesem Aufruf. Was hier ankommt,
 * ist geprüft. I4 dagegen gehört hierher, weil Deduplizierung nur gegen den Bestand entscheidbar
 * ist.
 */
public interface LedgerSchreibPort {

    /**
     * Schreibt einen geprüften Auszug auf ein Konto.
     *
     * <p>Bereits vorhandene Buchungen - erkannt an der Bankreferenz - werden übersprungen, nicht
     * doppelt angelegt. Das ist I4 und der Grund, aus dem zwei Läufe derselben Datei denselben
     * Datenbestand ergeben: Exportzeiträume überlappen sich an den Randtagen.
     */
    Auszugsergebnis schreibe(KontoId konto, Kontoauszug auszug);
}
