package de.jbaconsult.haushaltsbuch.api;

import de.jbaconsult.haushaltsbuch.kern.Institut;

/** Ein wählbares Institut. */
public record InstitutDto(String name, String land, String anzeigename, long hoechsteGueltigkeitTage) {

    public static InstitutDto von(Institut institut) {
        return new InstitutDto(
                institut.kennung().name(),
                institut.kennung().land(),
                institut.anzeigename(),
                institut.hoechsteGueltigkeit().toDays());
    }
}
