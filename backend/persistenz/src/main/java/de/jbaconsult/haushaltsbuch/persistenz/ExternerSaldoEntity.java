package de.jbaconsult.haushaltsbuch.persistenz;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import de.jbaconsult.haushaltsbuch.kern.Betrag;
import de.jbaconsult.haushaltsbuch.kern.ExternerSaldo;
import de.jbaconsult.haushaltsbuch.kern.Saldenart;

/**
 * Datenbanksicht eines abgerufenen Saldos.
 *
 * <p>Jeder Abruf legt eine Zeile an, es wird nichts überschrieben. Der Abrufzeitpunkt macht aus
 * einer Zahl eine Aussage - und er ist der Grund, warum gespeicherte Salden einen Fehlschlag
 * überleben dürfen: sie werden nicht falsch, sie werden alt.
 */
@Entity
@Table(name = "externer_saldo")
public class ExternerSaldoEntity {

    @Id
    public UUID id;

    @Column(name = "externes_konto_id", nullable = false)
    public UUID externesKontoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Saldenart art;

    @Column(name = "art_original", nullable = false)
    public String artOriginal;

    /** {@code numeric(15,2)}. Niemals {@code double} - siehe {@link Betrag}. */
    @Column(nullable = false)
    public BigDecimal betrag;

    @Column(nullable = false)
    public String waehrung;

    public LocalDate referenzdatum;

    @Column(name = "abgerufen_am", nullable = false)
    public Instant abgerufenAm;

    public ExternerSaldo zuDomaene() {
        return new ExternerSaldo(
                art, artOriginal, new Betrag(betrag), waehrung, Optional.ofNullable(referenzdatum), abgerufenAm);
    }
}
