package de.jbaconsult.haushaltsbuch.persistenz;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import de.jbaconsult.haushaltsbuch.kern.Auszugsergebnis;
import de.jbaconsult.haushaltsbuch.kern.Buchungszeile;
import de.jbaconsult.haushaltsbuch.kern.Iban;
import de.jbaconsult.haushaltsbuch.kern.KontoId;
import de.jbaconsult.haushaltsbuch.kern.Kontoauszug;
import de.jbaconsult.haushaltsbuch.kern.LedgerSchreibPort;

/**
 * Schreibt geprüfte Auszüge in den Datenbestand.
 *
 * <p>Ein Aufruf ist genau eine Transaktion. Damit gilt die Zusage aus {@link LedgerSchreibPort}
 * nicht auf Zuruf, sondern durch die Datenbank: entweder ist der Auszug vollständig da oder gar
 * nicht. Ein Import, der neun von zehn Buchungen schreibt und die zehnte meldet, ist kein
 * Teilerfolg, sondern ein Bestand, der aussieht wie ein vollständiger.
 *
 * <p>Die Prüfung der Invarianten liegt <b>vor</b> diesem Aufruf, im {@code Importdienst}. Diese
 * Klasse verlässt sich nicht darauf: die Splitsummen-Invariante und die Bedingungen an zweiseitige
 * Bewegungen stehen als Trigger in {@code V2__ledger.sql}, und die greifen auch dann, wenn jemand an
 * der Anwendung vorbei schreibt.
 */
@ApplicationScoped
public class LedgerRepository implements LedgerSchreibPort {

    private final EntityManager entityManager;
    private final RlsKontext rlsKontext;

    @Inject
    public LedgerRepository(EntityManager entityManager, RlsKontext rlsKontext) {
        this.entityManager = entityManager;
        this.rlsKontext = rlsKontext;
    }

    @Override
    @Transactional
    public Auszugsergebnis schreibe(KontoId konto, Kontoauszug auszug) {
        rlsKontext.anwenden();

        KontoauszugEntity auszugEntity = findeAuszug(konto, auszug).orElse(null);
        boolean bereitsVorhanden = auszugEntity != null;
        if (!bereitsVorhanden) {
            auszugEntity = neuerAuszug(konto, auszug);
            entityManager.persist(auszugEntity);
        }

        Set<String> vorhandeneReferenzen = vorhandeneReferenzen(
                konto, auszug.zeilen().stream().map(Buchungszeile::bankreferenz).toList());

        int neue = 0;
        int uebersprungene = 0;

        for (Buchungszeile zeile : auszug.zeilen()) {
            // I4. Der Grund ist nicht Sparsamkeit, sondern dass sich Exportzeitraeume an den
            // Randtagen ueberlappen: ohne diese Abfrage entstehen Doubletten, und zwar lautlos.
            if (!vorhandeneReferenzen.add(zeile.bankreferenz())) {
                uebersprungene++;
                continue;
            }
            schreibeZeile(konto, auszugEntity.id, zeile);
            neue++;
        }

        return new Auszugsergebnis(auszug.bezeichnung(), neue, uebersprungene, bereitsVorhanden);
    }

    /**
     * Legt Bewegung, Buchung und den einen Split an.
     *
     * <p>Jede importierte Buchung bekommt ihre eigene Bewegung mit genau einer Seite. Dass eine
     * Sammelabbuchung und ihr Ausgleich dieselbe Bewegung sind, stellt erst die Zuordnung fest -
     * und die ist Klassifikation und nicht Aufgabe des Imports. Ein Importer, der das schon hier
     * raten würde, produziert Zusammenlegungen, die niemand mehr nachprüft.
     *
     * <p>Der Split trägt den vollen Betrag und <b>keine</b> Kategorie. Genau ein Split je Buchung
     * ist der Anfangszustand aus ADR-0004, nicht ein Sonderfall.
     */
    private void schreibeZeile(KontoId konto, UUID auszugId, Buchungszeile zeile) {
        Instant jetzt = Instant.now();

        BewegungEntity bewegung = new BewegungEntity();
        bewegung.id = UUID.randomUUID();
        bewegung.angelegtAm = jetzt;
        entityManager.persist(bewegung);

        BuchungEntity buchung = new BuchungEntity();
        buchung.id = UUID.randomUUID();
        buchung.bewegungId = bewegung.id;
        buchung.kontoId = konto.wert();
        buchung.kontoauszugId = auszugId;
        buchung.buchungstag = zeile.buchungstag();
        buchung.valuta = zeile.valuta();
        buchung.betrag = zeile.betrag().wert();
        buchung.storno = zeile.storno();
        buchung.bankreferenz = zeile.bankreferenz();
        buchung.gegenparteiName = zeile.gegenparteiName();
        buchung.gegenparteiIban = zeile.gegenpartei().map(Iban::wert).orElse(null);
        buchung.mandatsreferenz = zeile.mandatsreferenz();
        buchung.glaeubigerkennung = zeile.glaeubigerkennung();
        buchung.endeZuEndeReferenz = zeile.endeZuEndeReferenz();
        buchung.verwendungszweck = zeile.verwendungszweck();
        buchung.buchungstext = zeile.buchungstext();
        buchung.angelegtAm = jetzt;
        entityManager.persist(buchung);

        BuchungssplitEntity split = new BuchungssplitEntity();
        split.id = UUID.randomUUID();
        split.buchungId = buchung.id;
        split.kategorieId = null;
        split.betrag = zeile.betrag().wert();
        split.angelegtAm = jetzt;
        entityManager.persist(split);
    }

    private Optional<KontoauszugEntity> findeAuszug(KontoId konto, Kontoauszug auszug) {
        return entityManager
                .createQuery("""
                        SELECT a FROM KontoauszugEntity a
                         WHERE a.kontoId = :konto
                           AND a.auszugsnummer = :nummer
                           AND a.von = :von
                           AND a.bis = :bis
                        """, KontoauszugEntity.class)
                .setParameter("konto", konto.wert())
                .setParameter("nummer", auszug.auszugsnummer())
                .setParameter("von", auszug.von())
                .setParameter("bis", auszug.bis())
                .getResultStream()
                .findFirst();
    }

    private KontoauszugEntity neuerAuszug(KontoId konto, Kontoauszug auszug) {
        KontoauszugEntity entity = new KontoauszugEntity();
        entity.id = UUID.randomUUID();
        entity.kontoId = konto.wert();
        entity.auszugsnummer = auszug.auszugsnummer();
        entity.quelle = auszug.quelle();
        entity.anfangssaldo = auszug.anfangssaldo().wert();
        entity.endsaldo = auszug.endsaldo().wert();
        entity.von = auszug.von();
        entity.bis = auszug.bis();
        entity.importiertAm = Instant.now();
        return entity;
    }

    /**
     * Die Bankreferenzen, die für dieses Konto bereits im Bestand stehen.
     *
     * <p>Bewusst als Menge und nicht als Abfrage je Zeile: ein Auszug hat neunzig Buchungen, und
     * neunzig Rundreisen zur Datenbank sind kein Preis, den ein Duplikatstest kosten muss.
     */
    private Set<String> vorhandeneReferenzen(KontoId konto, List<String> kandidaten) {
        if (kandidaten.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(entityManager
                .createQuery("""
                        SELECT b.bankreferenz FROM BuchungEntity b
                         WHERE b.kontoId = :konto
                           AND b.bankreferenz IN :referenzen
                        """, String.class)
                .setParameter("konto", konto.wert())
                .setParameter("referenzen", kandidaten)
                .getResultList());
    }
}
