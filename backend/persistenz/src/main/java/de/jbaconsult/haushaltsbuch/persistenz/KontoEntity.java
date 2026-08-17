package de.jbaconsult.haushaltsbuch.persistenz;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import de.jbaconsult.haushaltsbuch.kern.Konto;
import de.jbaconsult.haushaltsbuch.kern.KontoId;
import de.jbaconsult.haushaltsbuch.kern.Kontoart;
import de.jbaconsult.haushaltsbuch.kern.Sphaere;

/**
 * Datenbanksicht eines Kontos.
 *
 * <p>Bewusst nicht identisch mit {@link Konto} aus {@code kern}. Die Entität trägt technische
 * Belange - Spaltenabbildung, Zeitstempel - das Domänenobjekt fachliche. Die Abbildung dazwischen
 * ist Aufgabe des Repositories.
 */
@Entity
@Table(name = "konto")
public class KontoEntity {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String bezeichnung;

    /**
     * Als Text gespeichert, nicht als Ordinalzahl.
     *
     * <p>{@code EnumType.ORDINAL} würde die Position im Enum in die Datenbank schreiben. Fügt
     * jemand später einen Wert in der Mitte ein, verschieben sich sämtliche bestehenden Zeilen
     * still auf die falsche Bedeutung. Der Check-Constraint in {@code V1} prüft die erlaubten
     * Textwerte zusätzlich.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Kontoart art;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Sphaere sphaere;

    @Column(name = "angelegt_am", nullable = false)
    public Instant angelegtAm;

    public Konto zuDomaene() {
        return new Konto(new KontoId(id), bezeichnung, art, sphaere);
    }
}
