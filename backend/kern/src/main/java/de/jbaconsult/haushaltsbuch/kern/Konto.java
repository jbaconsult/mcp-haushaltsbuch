package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;

/**
 * Ein Konto in der Domäne.
 *
 * <p>Trägt bewusst <b>keine</b> IBAN. Das Zielbild ist Open Source; IBANs und Kontonamen kommen
 * ausschließlich aus Konfiguration und gehören nie in die Git-Historie. Die {@code bezeichnung} ist
 * ein sprechender Name für die Anzeige, keine Bankverbindung.
 */
public record Konto(KontoId id, String bezeichnung, Kontoart art, Sphaere sphaere) {

    public Konto {
        Objects.requireNonNull(id, "id darf nicht null sein");
        Objects.requireNonNull(art, "art darf nicht null sein");
        Objects.requireNonNull(sphaere, "sphaere darf nicht null sein");
        if (bezeichnung == null || bezeichnung.isBlank()) {
            throw new IllegalArgumentException("bezeichnung darf nicht leer sein");
        }
    }

    /**
     * Ob dieses Konto ins Minus gehen kann.
     *
     * <p>Das Haushaltskonto hat keine Kreditlinie. Daraus folgt für jede Mandatsmigration:
     * Finanzierung vor Belastung, Speisung einige Tage vor dem ersten Belastungstermin, Mandate in
     * aufsteigender Schadenshöhe.
     */
    public boolean hatKreditlinie() {
        return art != Kontoart.HAUSHALTSKONTO;
    }
}
