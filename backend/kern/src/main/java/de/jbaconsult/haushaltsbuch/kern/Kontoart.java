package de.jbaconsult.haushaltsbuch.kern;

/**
 * Art eines Kontos. Bestimmt mit, wie die Kennzahl {@code verfuegbar} berechnet wird.
 *
 * <p>Bewusst nicht abgeleitet aus IBAN oder Kontoname: das Zielbild ist Open Source, IBANs und
 * Kontonamen kommen ausschließlich aus Konfiguration und stehen nie im Code.
 */
public enum Kontoart {

    /** Gemeinsames Haushaltskonto. Hat keine Kreditlinie und kann nicht ins Minus. */
    HAUSHALTSKONTO,

    /** Privates Girokonto. */
    GIROKONTO,

    /** Freiberufliches Geschäftskonto. Trägt die USt-Zahllast als Verbindlichkeit. */
    GESCHAEFTSKONTO,

    /** Physisches Konto, auf dem die Töpfe virtuell liegen. */
    RUECKLAGENKONTO,

    /** Kreditkonto, etwa eine Hausfinanzierung. */
    KREDITKONTO
}
