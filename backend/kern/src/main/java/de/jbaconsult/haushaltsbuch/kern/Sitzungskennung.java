package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;

/**
 * Kennung einer Sitzung beim Bankanbieter.
 *
 * <p>Ein opaker Wert. Das System stellt nichts darüber fest, außer dass er die Sitzung
 * wiederfindet - Aufbau und Bedeutung sind Sache des Anbieters.
 *
 * <p>Sie wird am Bankzugang gespeichert, weil ohne sie kein weiterer Abruf möglich ist. Anders als
 * {@link Kontoreferenz} überlebt sie den einzelnen Vorgang; anders als {@link Kontokennung} ist sie
 * kein fachlicher Schlüssel, sondern ein Betriebsmittel.
 */
public record Sitzungskennung(String wert) {

    public Sitzungskennung {
        Objects.requireNonNull(wert, "wert darf nicht null sein");
        if (wert.isBlank()) {
            throw new IllegalArgumentException("Sitzungskennung darf nicht leer sein");
        }
    }

    @Override
    public String toString() {
        return "Sitzungskennung[verdeckt]";
    }
}
