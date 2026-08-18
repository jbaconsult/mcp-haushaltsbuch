package de.jbaconsult.haushaltsbuch.persistenz;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import de.jbaconsult.haushaltsbuch.kern.BankzugangId;
import de.jbaconsult.haushaltsbuch.kern.ExternesKonto;
import de.jbaconsult.haushaltsbuch.kern.ExternesKontoId;
import de.jbaconsult.haushaltsbuch.kern.Iban;
import de.jbaconsult.haushaltsbuch.kern.KontoId;
import de.jbaconsult.haushaltsbuch.kern.Kontokennung;

/**
 * Datenbanksicht eines externen Kontos.
 *
 * <p>Es gibt hier <b>keine</b> Spalte für die Sitzungskennung des Anbieters, und das ist der
 * wichtigste Satz dieser Klasse. Jene Kennung gilt nur, solange die Sitzung autorisiert ist; wer
 * sie speichert, baut eine Datenbank, die nach dem ersten Sitzungsablauf auf tote Verweise zeigt.
 * Der Schlüssel ist {@link #kennung}.
 */
@Entity
@Table(name = "externes_konto")
public class ExternesKontoEntity {

    @Id
    public UUID id;

    /**
     * Zugang, über den dieses Konto bekannt wurde. {@code null}, wenn er entfernt wurde.
     *
     * <p>Der Fremdschlüssel steht auf {@code SET NULL}, nicht auf {@code CASCADE}: ein entfernter
     * Zugang nimmt die abgerufenen Zahlen nicht mit. Sie sind gemessene Vergangenheit.
     */
    @Column(name = "bankzugang_id")
    public UUID bankzugangId;

    @Column(nullable = false, unique = true)
    public String kennung;

    public String iban;

    @Column(nullable = false)
    public String waehrung;

    public String kontoart;

    public String produktname;

    @Column(nullable = false)
    public String bezeichnung;

    @Column(name = "konto_id")
    public UUID kontoId;

    @Column(name = "angelegt_am", nullable = false)
    public Instant angelegtAm;

    @Column(name = "aktualisiert_am", nullable = false)
    public Instant aktualisiertAm;

    public ExternesKonto zuDomaene() {
        return new ExternesKonto(
                new ExternesKontoId(id),
                Optional.ofNullable(bankzugangId).map(BankzugangId::new),
                new Kontokennung(kennung),
                Optional.ofNullable(iban).flatMap(Iban::lesen),
                waehrung,
                Optional.ofNullable(kontoart),
                Optional.ofNullable(produktname),
                bezeichnung,
                Optional.ofNullable(kontoId).map(KontoId::new));
    }
}
