package de.jbaconsult.haushaltsbuch.kern;

/**
 * Lebenslauf eines Bankzugangs.
 *
 * <p>Ein Bankzugang ist kein Aufruf, sondern ein Zustand mit Ablaufdatum. Die Autorisierung durch
 * einen Menschen gilt eine begrenzte Zeit - bei den hier relevanten Instituten in der Größenordnung
 * von 180 Tagen -, danach ist sie erneut nötig.
 *
 * <p>Der Status wird angezeigt, nicht nur geführt. Ein Zugang, der in zwei Wochen abläuft, ohne
 * dass es jemand sieht, ist ein Ausfall mit Ankündigung.
 */
public enum Bankzugangstatus {

    /** Angelegt, aber noch nie autorisiert. */
    NICHT_AUTORISIERT,

    /**
     * Die Autorisierung läuft: der Mensch ist beim Institut, die Rückleitung steht aus.
     *
     * <p>Ein Zugang bleibt in diesem Zustand, wenn jemand den Vorgang abbricht, ohne
     * zurückzukommen. Das ist kein Fehler, sondern ein unfertiger Vorgang - und als solcher
     * sichtbar.
     */
    AUTORISIERUNG_LAEUFT,

    /** Autorisiert und innerhalb der Gültigkeit. Nur in diesem Zustand sind Abrufe möglich. */
    AUTORISIERT,

    /** Die Gültigkeit ist verstrichen. Erneute Autorisierung durch einen Menschen nötig. */
    ABGELAUFEN,

    /**
     * Der Vorgang ist gescheitert - abgelehnt beim Institut, abgebrochen, oder die Sitzung
     * existiert beim Anbieter nicht mehr.
     *
     * <p>Bereits abgerufene Konten und Salden bleiben erhalten. Ein Fehlschlag, der die letzten
     * bekannten Zahlen löscht, ist derselbe Datenverlust wie ein abgestürzter Import - nur
     * bequemer zu übersehen.
     */
    FEHLGESCHLAGEN;

    /** Ob in diesem Zustand Daten beim Anbieter abgerufen werden dürfen. */
    public boolean erlaubtAbruf() {
        return this == AUTORISIERT;
    }
}
