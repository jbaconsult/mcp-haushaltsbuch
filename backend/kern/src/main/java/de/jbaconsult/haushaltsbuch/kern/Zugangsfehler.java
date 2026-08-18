package de.jbaconsult.haushaltsbuch.kern;

/**
 * Ein Aufruf beim Bankanbieter ist gescheitert.
 *
 * <p>Trägt die Meldung des Anbieters mit, damit sie angezeigt werden kann statt verschluckt zu
 * werden. Eine Oberfläche, die „Fehler beim Abruf" sagt, während der Anbieter „Zustimmung
 * abgelaufen" gemeldet hat, kostet den Menschen davor eine halbe Stunde.
 */
public class Zugangsfehler extends RuntimeException {

    private final boolean sitzungUngueltig;

    public Zugangsfehler(String meldung) {
        this(meldung, false, null);
    }

    public Zugangsfehler(String meldung, Throwable ursache) {
        this(meldung, false, ursache);
    }

    public Zugangsfehler(String meldung, boolean sitzungUngueltig, Throwable ursache) {
        super(meldung, ursache);
        this.sitzungUngueltig = sitzungUngueltig;
    }

    /**
     * Ob die Sitzung beim Anbieter nicht mehr gilt.
     *
     * <p>Unterscheidet den behebbaren Netzfehler vom endgültigen Verlust der Autorisierung. Nur im
     * zweiten Fall wechselt der Bankzugang seinen Status - ein Netzfehler darf keinen Zugang
     * entwerten.
     */
    public boolean istSitzungUngueltig() {
        return sitzungUngueltig;
    }

    public static Zugangsfehler sitzungUngueltig(String meldung) {
        return new Zugangsfehler(meldung, true, null);
    }
}
