package de.jbaconsult.haushaltsbuch.persistenz;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Datenbanksicht einer Kategoriengruppe - die eine Ebene Gruppierung über den Kategorien.
 *
 * <p>Von Anfang an vorhanden und nicht nachgerüstet: eine flache Liste hat nach anderthalb Jahren
 * dreißig Einträge, und dann ist die Ebene eine Migration über gewachsenen Bestand. ADR-0004 führt
 * sie als dritte von drei Wartungspflichten, die beim Entwurf umsonst sind und später teuer.
 */
@Entity
@Table(name = "kategoriegruppe")
public class KategoriegruppeEntity {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String bezeichnung;

    @Column(nullable = false)
    public int sortierung;

    @Column(name = "angelegt_am", nullable = false)
    public Instant angelegtAm;
}
