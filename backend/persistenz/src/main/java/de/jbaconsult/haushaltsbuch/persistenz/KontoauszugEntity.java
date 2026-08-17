package de.jbaconsult.haushaltsbuch.persistenz;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import de.jbaconsult.haushaltsbuch.kern.Auszugsquelle;

/**
 * Datenbanksicht eines eingelesenen Auszugs.
 *
 * <p>Anfangs- und Endsaldo stehen hier so, wie die Bank sie geliefert hat, und werden nie aus den
 * Buchungen abgeleitet. Genau der Vergleich zwischen beidem ist die Prüfung I1; eine berechnete
 * Angabe würde sich selbst bestätigen.
 *
 * <p>Fremdschlüssel als nackte {@link UUID} statt als {@code @ManyToOne}. Die Beziehungen dieses
 * Schemas werden ausschließlich in Abfragen gebraucht, nie als Objektgeflecht - eine Zuordnung, die
 * beim Zugriff nachlädt, ist hier nur eine Gelegenheit für Überraschungen.
 */
@Entity
@Table(name = "kontoauszug")
public class KontoauszugEntity {

    @Id
    public UUID id;

    @Column(name = "konto_id", nullable = false)
    public UUID kontoId;

    @Column(nullable = false)
    public String auszugsnummer;

    /** Als Text, nicht als Ordinalzahl - siehe Begründung in {@link KontoEntity}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Auszugsquelle quelle;

    @Column(nullable = false)
    public BigDecimal anfangssaldo;

    @Column(nullable = false)
    public BigDecimal endsaldo;

    @Column(nullable = false)
    public LocalDate von;

    @Column(nullable = false)
    public LocalDate bis;

    @Column(name = "importiert_am", nullable = false)
    public Instant importiertAm;
}
