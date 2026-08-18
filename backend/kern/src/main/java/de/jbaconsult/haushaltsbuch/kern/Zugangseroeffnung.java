package de.jbaconsult.haushaltsbuch.kern;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Ergebnis des Eintauschs eines Autorisierungscodes.
 *
 * <p>Diese Antwort kommt <b>nur einmal</b>. Was daraus gebraucht wird, muss sofort abgelegt werden
 * - ein zweiter Versuch mit demselben Code schlägt fehl.
 *
 * @param sitzung Kennung der eröffneten Sitzung
 * @param gueltigBis wann die Autorisierung verfällt
 * @param konten die Konten dieser Sitzung, je mit stabiler und flüchtiger Kennung
 */
public record Zugangseroeffnung(Sitzungskennung sitzung, Instant gueltigBis, List<Kontobefund> konten) {

    public Zugangseroeffnung {
        Objects.requireNonNull(sitzung, "sitzung darf nicht null sein");
        Objects.requireNonNull(gueltigBis, "gueltigBis darf nicht null sein");
        konten = konten == null ? List.of() : List.copyOf(konten);
    }
}
