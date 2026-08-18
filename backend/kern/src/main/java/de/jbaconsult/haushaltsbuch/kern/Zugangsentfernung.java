package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;
import java.util.Optional;

/**
 * Was das Entfernen eines Bankzugangs bewirkt hat.
 *
 * <p>Ein einfaches {@code void} wäre hier zu wenig. Das Entfernen berührt zwei Systeme - den eigenen
 * Bestand und die Sitzung beim Anbieter -, und der zweite Teil kann scheitern, ohne dass der erste
 * scheitern darf. Wer nur „erledigt" zurückmeldet, verschweigt in genau diesem Fall, dass beim
 * Anbieter weiterhin eine gültige Autorisierung auf die Konten dieses Menschen zeigt.
 *
 * @param sitzungBeendet ob die Sitzung beim Anbieter beendet werden konnte; {@code false} auch dann,
 *     wenn gar keine bestand
 * @param anbietermeldung Grund, falls das Beenden der Sitzung fehlschlug
 * @param entfernteKonten Anzahl der mitentfernten externen Konten; {@code 0} bei
 *     {@link Kontenbehandlung#BEHALTEN}
 * @param geloesteKonten Anzahl der Konten, die ihren Zugangsbezug verloren haben und als Bestand
 *     stehen bleiben
 */
public record Zugangsentfernung(
        boolean sitzungBeendet, Optional<String> anbietermeldung, int entfernteKonten, int geloesteKonten) {

    public Zugangsentfernung {
        Objects.requireNonNull(anbietermeldung, "anbietermeldung darf nicht null sein - Optional.empty() statt null");
    }

    /**
     * Ob der Mensch davor einen Hinweis braucht.
     *
     * <p>Nur dann, wenn eine Sitzung bestand und ihr Ende nicht bestätigt ist. Ein Zugang, der nie
     * autorisiert war, hat beim Anbieter nichts hinterlassen - dort ist Schweigen richtig.
     */
    public boolean brauchtHinweis() {
        return anbietermeldung.isPresent();
    }
}
