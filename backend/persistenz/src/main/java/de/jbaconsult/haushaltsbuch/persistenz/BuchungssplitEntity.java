package de.jbaconsult.haushaltsbuch.persistenz;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Datenbanksicht eines Splits - der Positionsebene selbst, nicht einer zusätzlichen Struktur
 * darunter.
 *
 * <p>Nach dem Import hat jede Buchung genau einen Split ohne Kategorie. „Aufschlüsseln" ersetzt
 * später den einen durch mehrere; die Struktur bleibt dieselbe, nur die Anzahl Zeilen ändert sich.
 * Deshalb gibt es keine Migration von „einfach" nach „aufgeschlüsselt" - siehe ADR-0004.
 *
 * <p>{@code kategorieId} bleibt beim Import {@code null}. Das ist der Normalzustand und heißt
 * „gehört in die Review-Queue", nicht „Fehler".
 */
@Entity
@Table(name = "buchungssplit")
public class BuchungssplitEntity {

    @Id
    public UUID id;

    @Column(name = "buchung_id", nullable = false)
    public UUID buchungId;

    @Column(name = "kategorie_id")
    public UUID kategorieId;

    @Column(nullable = false)
    public BigDecimal betrag;

    @Column(name = "notiz")
    public String notiz;

    @Column(name = "angelegt_am", nullable = false)
    public Instant angelegtAm;
}
