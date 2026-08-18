package de.jbaconsult.haushaltsbuch.kern;

import java.util.List;

/**
 * Zugang zu einem Anbieter von Kontoinformationen.
 *
 * <p>Der Port wird hier definiert und außerhalb implementiert. Er spricht ausschließlich in
 * Begriffen dieses Systems - Institut, Bankzugang, externes Konto, Saldo. Kein Feldname, kein
 * Kopfzeilenname und kein Statuscode eines Anbieters erscheint in dieser Schnittstelle.
 *
 * <p><b>Warum diese Strenge:</b> der zuvor vorgesehene Anbieter dieser Kategorie hat seinen
 * Selbstbedienungszugang eingestellt, und jede Fremdinstallation dieses Produkts wird einen eigenen
 * Zugang mitbringen. Der Anbieter ist Konfiguration, nicht Abhängigkeit. Ein Port, durch den
 * Anbieterbegriffe sickern, macht den Wechsel zu einer Umschreibung des halben Systems.
 *
 * <p><b>Ausschließlich Kontoinformation.</b> Zahlungsauslösung ist bei der verwendeten
 * Registrierung technisch möglich und trotzdem verboten - hier steht deshalb keine Methode dafür,
 * auch keine ungenutzte.
 */
public interface BankanbieterPort {

    /** Name des Anbieters, wie er am Bankzugang gespeichert wird. */
    String anbieter();

    /**
     * Die Institute, gegen die autorisiert werden kann.
     *
     * @param land ISO-Ländercode, etwa {@code DE}
     */
    List<Institut> institute(String land);

    /**
     * Startet eine Autorisierung und liefert die Adresse, an die der Mensch geschickt wird.
     *
     * <p>Der Zustand aus dem Wunsch kommt bei der Rückleitung zurück und bindet sie an diesen
     * Vorgang. Der Port erzeugt ihn nicht selbst - das ist Sache der Domäne, weil er dort auch
     * gegen den angemeldeten Benutzer gebunden wird.
     */
    Autorisierungsstart autorisierungStarten(Autorisierungswunsch wunsch);

    /**
     * Tauscht den Autorisierungscode gegen eine Sitzung.
     *
     * <p>Nur einmal möglich. Das Ergebnis enthält Angaben, die kein zweiter Aufruf liefert.
     *
     * @throws Zugangsfehler wenn der Code abgelehnt wird
     */
    Zugangseroeffnung zugangEroeffnen(String autorisierungscode);

    /**
     * Liest den aktuellen Bestand einer Sitzung samt frischer Kontoreferenzen.
     *
     * <p>Liefert {@link Zugangsbestand#nichtMehrAutorisiert()}, wenn die Sitzung beim Anbieter
     * nicht mehr besteht - das ist ein Befund, kein Ausnahmefall.
     */
    Zugangsbestand bestand(Sitzungskennung sitzung);

    /**
     * Die Salden eines Kontos innerhalb einer laufenden Sitzung.
     *
     * @throws Zugangsfehler wenn der Abruf scheitert
     */
    List<ExternerSaldo> salden(Sitzungskennung sitzung, Kontoreferenz konto);

    /**
     * Einmalige Messung der Feldabdeckung von Buchungen.
     *
     * <p>Ausdrücklich kein Import: das Ergebnis wird berichtet und nicht gespeichert. Buchungen
     * gelangen ausschließlich über den Importdienst in dieses System, weil sie dort gegen die
     * Saldeninvarianten geprüft werden. Ein Nebenweg, der daran vorbei schreibt, hebelt die
     * Selbstvalidierung aus.
     */
    Feldabdeckung feldabdeckungMessen(Sitzungskennung sitzung, Kontoreferenz konto);
}
