package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;

/**
 * Der stabile Schlüssel eines externen Kontos über Sitzungen hinweg.
 *
 * <p>Bei Enable Banking heißt das Feld {@code identification_hash}. Es bleibt gleich, solange das
 * Konto dasselbe ist - über abgelaufene Autorisierungen, neue Sitzungen und erneute Einrichtung
 * hinweg.
 *
 * <p><b>Nicht zu verwechseln mit {@link Kontoreferenz}.</b> Die dort geführte Kennung ist ein
 * Sitzungsschlüssel und stirbt mit der Sitzung. Wer sie speichert, baut eine Datenbank, die nach
 * dem ersten Sitzungsablauf auf tote Kennungen zeigt - und der Fehler tritt erst Monate später auf,
 * wenn niemand mehr an die Einrichtung denkt.
 */
public record Kontokennung(String wert) {

    public Kontokennung {
        Objects.requireNonNull(wert, "wert darf nicht null sein");
        wert = wert.trim();
        if (wert.isEmpty()) {
            throw new IllegalArgumentException("Kontokennung darf nicht leer sein");
        }
    }

    @Override
    public String toString() {
        return wert;
    }
}
