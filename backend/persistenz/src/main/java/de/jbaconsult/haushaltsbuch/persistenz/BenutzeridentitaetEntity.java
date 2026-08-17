package de.jbaconsult.haushaltsbuch.persistenz;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Abbildung des OIDC-Subjects auf den fachlichen Benutzer.
 *
 * <p>Die zugehörige Tabelle trägt bewusst keine Zugriffskontrolle: die Abfrage läuft, bevor der
 * Benutzerkontext feststeht. Die ausführliche Begründung steht in {@code V1__grundschema.sql}.
 */
@Entity
@Table(name = "benutzeridentitaet")
public class BenutzeridentitaetEntity {

    @Id
    @Column(name = "oidc_subjekt")
    public String oidcSubjekt;

    @Column(name = "benutzer_id", nullable = false)
    public UUID benutzerId;
}
