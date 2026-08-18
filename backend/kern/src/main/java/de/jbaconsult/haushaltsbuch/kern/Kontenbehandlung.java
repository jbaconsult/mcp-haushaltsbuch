package de.jbaconsult.haushaltsbuch.kern;

/**
 * Was beim Entfernen eines Bankzugangs mit dessen externen Konten geschieht.
 *
 * <p>Die Entscheidung liegt bewusst beim Menschen und hat keinen stillen Standardwert im Code. Der
 * Grund ist die Asymmetrie der Fehlerkosten: bleiben Konten stehen, die niemand mehr braucht, kostet
 * das eine Zeile in einer Liste. Verschwinden Salden, die noch gebraucht wurden, ist die gemessene
 * Vergangenheit weg - und ein Saldo von vor drei Monaten lässt sich nicht neu abrufen.
 *
 * <p>Der Standard der Oberfläche ist deshalb {@link #BEHALTEN}. Das deckt sich mit dem Grundsatz,
 * den {@link Bankzugangstatus#FEHLGESCHLAGEN} bereits formuliert: abgerufene Zahlen werden nicht
 * dadurch falsch, dass die Autorisierung endet.
 */
public enum Kontenbehandlung {

    /**
     * Konten und Salden bleiben als historischer Bestand stehen.
     *
     * <p>Sie verlieren ihren Zugangsbezug und stehen danach für sich. Eine erneute Autorisierung
     * desselben Instituts findet sie über {@link Kontokennung} wieder und knüpft sie neu an - der
     * Schlüssel ist stabil über Sitzungen und Zugänge hinweg.
     */
    BEHALTEN,

    /**
     * Konten und Salden verschwinden mit dem Zugang.
     *
     * <p>Endgültig und ohne Papierkorb. Sinnvoll für einen Fehlversuch, der nie brauchbare Zahlen
     * geliefert hat; für einen Zugang mit Historie ist es der teurere Weg.
     */
    ENTFERNEN
}
