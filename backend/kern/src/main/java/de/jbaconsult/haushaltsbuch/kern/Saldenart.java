package de.jbaconsult.haushaltsbuch.kern;

/**
 * Art eines Saldos.
 *
 * <p>Ein Konto hat nicht einen Saldo, sondern mehrere gleichzeitig - gebucht, verfügbar,
 * vorgemerkt. Sie zu einem einzigen Wert zusammenzuziehen wäre bequem und falsch: der gebuchte
 * Saldo beantwortet eine andere Frage als der verfügbare, und die Kennzahl {@code verfuegbar}
 * dieses Systems ist noch einmal etwas anderes als beide.
 *
 * <p>Der Anbieter liefert die Art als Code. Unbekannte Codes werden als {@link #SONSTIGE} geführt
 * und behalten ihren Originalwert - eine Zuordnung zu raten wäre schlimmer als sie offenzulassen.
 */
public enum Saldenart {

    /** Gebuchter Saldo: alles, was tatsächlich verbucht ist. */
    GEBUCHT,

    /** Verfügbarer Betrag laut Institut, meist gebucht plus Kreditlinie minus Vormerkungen. */
    VERFUEGBAR,

    /** Saldo einschließlich vorgemerkter, noch nicht gebuchter Posten. */
    VORGEMERKT,

    /** Saldo zum Abschluss einer Periode. */
    ABSCHLUSS,

    /** Vom Anbieter geliefert, aber keiner der bekannten Arten zuzuordnen. */
    SONSTIGE
}
