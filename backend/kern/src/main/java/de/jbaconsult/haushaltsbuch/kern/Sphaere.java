package de.jbaconsult.haushaltsbuch.kern;

/**
 * Die drei strikt getrennten Sphären.
 *
 * <p>Gekoppelt über genau zwei Kanten: die Privatentnahme (freiberuflich nach privat) und die
 * Steuerrücklage (freiberuflich nach Finanzamt). Bewusstes Vermischen ist ausgeschlossen - es
 * zerstört die Messbarkeit der Kennzahl {@code verfuegbar}.
 *
 * <p>Merke: nicht das Zahlungskonto entscheidet über die Zuordnung einer Ausgabe, sondern
 * Rechnungsempfänger und betriebliche Veranlassung. Eine privat gezahlte Betriebsausgabe bleibt
 * steuerlich abzugsfähig.
 */
public enum Sphaere {

    /** Privat und gemeinsam. System of Record ist dieses System. */
    PRIVAT,

    /** Freiberuflich. System of Record ist Lexware Office, weil GoBD-relevant. */
    FREIBERUFLICH,

    /** Verbindlichkeiten gegenüber dem Finanzamt. Gespeist aus beiden anderen Sphären. */
    FINANZAMT
}
