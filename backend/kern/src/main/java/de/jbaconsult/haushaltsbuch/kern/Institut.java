package de.jbaconsult.haushaltsbuch.kern;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Ein Institut, gegen das ein Bankzugang eingerichtet werden kann.
 *
 * @param kennung eindeutige Kennung beim Anbieter
 * @param anzeigename Name für die Oberfläche
 * @param hoechsteGueltigkeit wie lange eine Autorisierung höchstens gelten darf; eine längere
 *     Anfrage lehnt das Institut ab
 * @param benoetigteAngaben Angaben, die dieses Institut zusätzlich verlangt - entweder alle oder
 *     keine, ein Teil davon führt zu einem Fehler
 */
public record Institut(
        InstitutKennung kennung, String anzeigename, Duration hoechsteGueltigkeit, List<String> benoetigteAngaben) {

    public Institut {
        Objects.requireNonNull(kennung, "kennung darf nicht null sein");
        Objects.requireNonNull(anzeigename, "anzeigename darf nicht null sein");
        Objects.requireNonNull(hoechsteGueltigkeit, "hoechsteGueltigkeit darf nicht null sein");
        benoetigteAngaben = benoetigteAngaben == null ? List.of() : List.copyOf(benoetigteAngaben);
    }
}
