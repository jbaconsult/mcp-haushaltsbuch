package de.jbaconsult.haushaltsbuch.persistenz;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Datenbanksicht einer Geldbewegung.
 *
 * <p>Absichtlich fast leer. Alles Fachliche hängt an den Seiten - {@link BuchungEntity} - und die
 * Bewegung ist nur die Klammer darum. Sie trägt die Aussage „diese zwei Buchungen sind derselbe
 * Geldfluss", und die Datenbank leitet daraus zwei Bedingungen ab: die Seiten ergänzen sich zu null,
 * und keine von ihnen trägt eine Kategorie. Siehe {@code V2__ledger.sql}.
 */
@Entity
@Table(name = "bewegung")
public class BewegungEntity {

    @Id
    public UUID id;

    @Column(name = "angelegt_am", nullable = false)
    public Instant angelegtAm;
}
