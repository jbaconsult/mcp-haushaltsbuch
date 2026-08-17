package de.jbaconsult.haushaltsbuch.persistenz;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import de.jbaconsult.haushaltsbuch.kern.Betrag;
import de.jbaconsult.haushaltsbuch.kern.BewegungId;
import de.jbaconsult.haushaltsbuch.kern.Buchung;
import de.jbaconsult.haushaltsbuch.kern.BuchungId;
import de.jbaconsult.haushaltsbuch.kern.Buchungszeile;
import de.jbaconsult.haushaltsbuch.kern.Iban;
import de.jbaconsult.haushaltsbuch.kern.KontoId;

/**
 * Datenbanksicht einer Buchung.
 *
 * <p>Die strukturierten Felder stehen <b>einzeln</b>. Das ist keine Formfrage: sie sind die
 * Eingangsdaten jeder späteren Klassifikation, und {@code constraint.klassifikation-iban-mref} hält
 * fest, warum es an ihnen hängt - eine Namensheuristik hat in der Analyse zweimal vierstellige
 * Posten verschluckt. Wer sie hier zu einem Textblob zusammenzieht, macht die Regel „IBAN vor
 * Mandatsreferenz vor Namenstext" unausführbar, und niemand merkt es, bis eine Auswertung falsch
 * ist.
 *
 * <p>Mandatsreferenz und Gläubigerkennung stehen aus demselben Grund getrennt: die Acquirer-Regel
 * aus {@code constraint.dauermandat-vs-pos} zählt Mandatsreferenzen je Gläubigerkennung. In einem
 * gemeinsamen Feld ist sie nicht berechenbar.
 */
@Entity
@Table(name = "buchung")
public class BuchungEntity {

    @Id
    public UUID id;

    @Column(name = "bewegung_id", nullable = false)
    public UUID bewegungId;

    @Column(name = "konto_id", nullable = false)
    public UUID kontoId;

    @Column(name = "kontoauszug_id")
    public UUID kontoauszugId;

    @Column(nullable = false)
    public LocalDate buchungstag;

    @Column(nullable = false)
    public LocalDate valuta;

    /** Negativ ist Abgang, positiv ist Zugang, aus Sicht des Kontos. */
    @Column(nullable = false)
    public BigDecimal betrag;

    @Column(nullable = false)
    public boolean storno;

    @Column(nullable = false)
    public String bankreferenz;

    @Column(name = "gegenpartei_name")
    public String gegenparteiName;

    @Column(name = "gegenpartei_iban")
    public String gegenparteiIban;

    @Column(name = "mandatsreferenz")
    public String mandatsreferenz;

    @Column(name = "glaeubigerkennung")
    public String glaeubigerkennung;

    @Column(name = "endezuende_referenz")
    public String endeZuEndeReferenz;

    @Column(name = "verwendungszweck")
    public String verwendungszweck;

    @Column(name = "buchungstext")
    public String buchungstext;

    @Column(name = "angelegt_am", nullable = false)
    public Instant angelegtAm;

    public Buchung zuDomaene() {
        Buchungszeile zeile = new Buchungszeile(
                bankreferenz,
                buchungstag,
                valuta,
                new Betrag(betrag),
                storno,
                gegenparteiName,
                // Eine gespeicherte IBAN hat den Import und damit I5 passiert. Ist sie trotzdem
                // ungueltig, wurde an der Anwendung vorbei geschrieben - dann ist "keine IBAN" die
                // ehrlichere Antwort als eine, die die Pruefung nie gesehen hat.
                gegenparteiIban == null ? null : Iban.lesen(gegenparteiIban).orElse(null),
                mandatsreferenz,
                glaeubigerkennung,
                endeZuEndeReferenz,
                verwendungszweck,
                buchungstext);

        return new Buchung(new BuchungId(id), new KontoId(kontoId), new BewegungId(bewegungId), zeile);
    }
}
