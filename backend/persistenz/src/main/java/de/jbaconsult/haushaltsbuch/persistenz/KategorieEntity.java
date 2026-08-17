package de.jbaconsult.haushaltsbuch.persistenz;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import de.jbaconsult.haushaltsbuch.kern.Kategorie;
import de.jbaconsult.haushaltsbuch.kern.KategorieId;

/**
 * Datenbanksicht einer Kategorie.
 *
 * <p>Die Kennung ist eine UUID und nicht die Bezeichnung. Umbenennen muss folgenlos bleiben, weil
 * Regeln, Prognosen und Historie auf Kategorien zeigen - ADR-0004, erste Wartungspflicht.
 *
 * <p>Löschen läuft in der Datenbank gegen einen {@code RESTRICT}-Fremdschlüssel vom Split her:
 * solange Buchungen an einer Kategorie hängen, verschwindet sie nicht. Wer sie loswerden will,
 * bucht vorher um oder setzt {@code aktiv} auf falsch. Eine Kaskade wäre der bequeme Weg, Splits
 * verwaisen zu lassen.
 */
@Entity
@Table(name = "kategorie")
public class KategorieEntity {

    @Id
    public UUID id;

    @Column(name = "gruppe_id", nullable = false)
    public UUID gruppeId;

    @Column(nullable = false)
    public String bezeichnung;

    @Column(nullable = false)
    public boolean aktiv;

    @Column(name = "angelegt_am", nullable = false)
    public Instant angelegtAm;

    public Kategorie zuDomaene() {
        return new Kategorie(new KategorieId(id), gruppeId, bezeichnung, aktiv);
    }
}
