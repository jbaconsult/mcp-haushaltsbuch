package de.jbaconsult.haushaltsbuch.persistenz;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Datenbanksicht eines Benutzers.
 *
 * <p>Der Anmeldebezug liegt getrennt in {@link BenutzeridentitaetEntity} - siehe die Begründung in
 * {@code V1__grundschema.sql}.
 */
@Entity
@Table(name = "benutzer")
public class BenutzerEntity {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String anzeigename;

    @Column(name = "angelegt_am", nullable = false)
    public Instant angelegtAm;
}
