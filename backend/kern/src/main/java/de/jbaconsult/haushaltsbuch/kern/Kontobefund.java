package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Konto, wie es der Anbieter innerhalb einer laufenden Sitzung meldet.
 *
 * <p>Der einzige Ort, an dem flüchtige und dauerhafte Kennung zusammenkommen. Alles, was von hier
 * aus gespeichert wird, nimmt die {@link Kontokennung} mit; die {@link Kontoreferenz} bleibt im
 * Vorgang.
 *
 * @param kennung stabiler Schlüssel, wird gespeichert
 * @param referenz flüchtiger Sitzungsschlüssel, wird nicht gespeichert
 */
public record Kontobefund(
        Kontokennung kennung,
        Kontoreferenz referenz,
        Optional<Iban> iban,
        String waehrung,
        Optional<String> kontoart,
        Optional<String> produktname,
        String bezeichnung) {

    public Kontobefund {
        Objects.requireNonNull(kennung, "kennung darf nicht null sein");
        Objects.requireNonNull(referenz, "referenz darf nicht null sein");
        Objects.requireNonNull(waehrung, "waehrung darf nicht null sein");
        Objects.requireNonNull(bezeichnung, "bezeichnung darf nicht null sein");
        Objects.requireNonNull(iban, "iban darf nicht null sein - Optional.empty() statt null");
        Objects.requireNonNull(kontoart, "kontoart darf nicht null sein - Optional.empty() statt null");
        Objects.requireNonNull(produktname, "produktname darf nicht null sein - Optional.empty() statt null");
    }
}
