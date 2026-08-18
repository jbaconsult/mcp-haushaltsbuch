package de.jbaconsult.haushaltsbuch.kern;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Was für den Start einer Autorisierung nötig ist.
 *
 * @param institut gegen welches Institut autorisiert wird
 * @param gueltigBis bis wann der Zugang gelten soll; darf die Obergrenze des Instituts nicht
 *     überschreiten, sonst lehnt es ab
 * @param rueckleitung wohin das Institut den Menschen zurückschickt; muss beim Anbieter hinterlegt
 *     sein, sonst bricht der Vorgang ohne verwertbare Meldung ab
 * @param zustand der {@code state}, über den die Rückleitung dem Vorgang zugeordnet wird
 * @param ipAdresse IP des Menschen, falls das Institut sie verlangt
 */
public record Autorisierungswunsch(
        InstitutKennung institut, Instant gueltigBis, String rueckleitung, String zustand, Optional<String> ipAdresse) {

    public Autorisierungswunsch {
        Objects.requireNonNull(institut, "institut darf nicht null sein");
        Objects.requireNonNull(gueltigBis, "gueltigBis darf nicht null sein");
        Objects.requireNonNull(rueckleitung, "rueckleitung darf nicht null sein");
        Objects.requireNonNull(zustand, "zustand darf nicht null sein");
        ipAdresse = ipAdresse == null ? Optional.empty() : ipAdresse;
    }
}
