package de.jbaconsult.haushaltsbuch.persistenz;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import de.jbaconsult.haushaltsbuch.kern.Bankzugang;
import de.jbaconsult.haushaltsbuch.kern.BankzugangId;
import de.jbaconsult.haushaltsbuch.kern.Bankzugangstatus;
import de.jbaconsult.haushaltsbuch.kern.BenutzerId;
import de.jbaconsult.haushaltsbuch.kern.InstitutKennung;
import de.jbaconsult.haushaltsbuch.kern.Sitzungskennung;

/**
 * Datenbanksicht eines Bankzugangs.
 *
 * <p>Trägt zusätzlich zum Domänenobjekt den Zustandswert des laufenden Autorisierungsvorgangs. Der
 * gehört bewusst nicht in {@link Bankzugang}: er ist ein Betriebsmittel der Einrichtung, kein
 * fachliches Merkmal des Zugangs, und er hat in keiner Antwort etwas verloren.
 */
@Entity
@Table(name = "bankzugang")
public class BankzugangEntity {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String anbieter;

    @Column(name = "institut_name", nullable = false)
    public String institutName;

    @Column(name = "institut_land", nullable = false)
    public String institutLand;

    @Column(nullable = false)
    public String institutsname;

    /**
     * Als Text gespeichert, nicht als Ordinalzahl.
     *
     * <p>{@code EnumType.ORDINAL} würde die Position im Enum schreiben; ein später in der Mitte
     * eingefügter Wert verschöbe sämtliche bestehenden Zeilen still auf die falsche Bedeutung.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Bankzugangstatus status;

    @Column(name = "gueltig_bis")
    public Instant gueltigBis;

    public String sitzung;

    public String fehlermeldung;

    public String zustand;

    @Column(name = "zustand_gueltig_bis")
    public Instant zustandGueltigBis;

    @Column(name = "zustand_verbraucht", nullable = false)
    public boolean zustandVerbraucht;

    @Column(name = "angelegt_von", nullable = false)
    public UUID angelegtVon;

    @Column(name = "angelegt_am", nullable = false)
    public Instant angelegtAm;

    public Bankzugang zuDomaene() {
        return new Bankzugang(
                new BankzugangId(id),
                anbieter,
                new InstitutKennung(institutName, institutLand),
                institutsname,
                status,
                Optional.ofNullable(gueltigBis),
                Optional.ofNullable(sitzung).map(Sitzungskennung::new),
                Optional.ofNullable(fehlermeldung),
                new BenutzerId(angelegtVon),
                angelegtAm);
    }

    /** Übernimmt die fachlichen Felder. Zustandswerte bleiben unberührt - sie gehören dem Vorgang. */
    public void ausDomaene(Bankzugang zugang) {
        this.id = zugang.id().wert();
        this.anbieter = zugang.anbieter();
        this.institutName = zugang.institut().name();
        this.institutLand = zugang.institut().land();
        this.institutsname = zugang.institutsname();
        this.status = zugang.status();
        this.gueltigBis = zugang.gueltigBis().orElse(null);
        this.sitzung = zugang.sitzung().map(Sitzungskennung::wert).orElse(null);
        this.fehlermeldung = zugang.fehlermeldung().orElse(null);
        this.angelegtVon = zugang.angelegtVon().wert();
        this.angelegtAm = zugang.angelegtAm();
    }
}
