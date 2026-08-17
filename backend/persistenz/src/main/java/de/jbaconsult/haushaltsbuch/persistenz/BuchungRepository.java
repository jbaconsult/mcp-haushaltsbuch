package de.jbaconsult.haushaltsbuch.persistenz;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import de.jbaconsult.haushaltsbuch.kern.Betrag;
import de.jbaconsult.haushaltsbuch.kern.Buchung;
import de.jbaconsult.haushaltsbuch.kern.BuchungId;
import de.jbaconsult.haushaltsbuch.kern.Iban;
import de.jbaconsult.haushaltsbuch.kern.KontoId;

/**
 * Lesender Zugriff auf Buchungen.
 *
 * <p>Wie {@link KontoRepository} enthalten die Abfragen <b>keine</b> Zugriffsbedingung. Gefiltert
 * wird in der Datenbank durch Row-Level-Security; ein zweites Regelwerk im Java-Code würde mit der
 * Zeit vom ersten abweichen, und dann sucht man lange nach der Ursache verschwundener Zeilen.
 *
 * <p>Die Abfragen nach Gegenpartei-IBAN, Gläubigerkennung und Mandatsreferenz sind nicht Beiwerk.
 * Sie sind der Beleg dafür, dass diese Angaben nach dem Import als eigene, abfragbare Spalten
 * vorliegen und nicht im Verwendungszweck vergraben sind - die Voraussetzung dafür, dass eine
 * Klassifikation überhaupt der Regel „IBAN vor Mandatsreferenz vor Namenstext" folgen kann.
 * Klassifiziert wird hier nichts; das ist ein eigener Sub-Sprint.
 */
@ApplicationScoped
public class BuchungRepository {

    private final EntityManager entityManager;
    private final RlsKontext rlsKontext;

    @Inject
    public BuchungRepository(EntityManager entityManager, RlsKontext rlsKontext) {
        this.entityManager = entityManager;
        this.rlsKontext = rlsKontext;
    }

    @Transactional
    public List<Buchung> alleSichtbaren() {
        rlsKontext.anwenden();
        return entityManager
                .createQuery("SELECT b FROM BuchungEntity b ORDER BY b.valuta, b.bankreferenz", BuchungEntity.class)
                .getResultList()
                .stream()
                .map(BuchungEntity::zuDomaene)
                .toList();
    }

    @Transactional
    public List<Buchung> zuKonto(KontoId konto) {
        rlsKontext.anwenden();
        return entityManager
                .createQuery("""
                        SELECT b FROM BuchungEntity b
                         WHERE b.kontoId = :konto
                         ORDER BY b.valuta, b.bankreferenz
                        """, BuchungEntity.class)
                .setParameter("konto", konto.wert())
                .getResultList()
                .stream()
                .map(BuchungEntity::zuDomaene)
                .toList();
    }

    /** Wie viele Buchungen zu einem Auszug im Bestand stehen. Die Frage der Roten Probe. */
    @Transactional
    public long anzahlZuAuszug(UUID kontoauszugId) {
        rlsKontext.anwenden();
        return entityManager
                .createQuery("SELECT count(b) FROM BuchungEntity b WHERE b.kontoauszugId = :auszug", Long.class)
                .setParameter("auszug", kontoauszugId)
                .getSingleResult();
    }

    @Transactional
    public Optional<Buchung> findeNachBankreferenz(KontoId konto, String bankreferenz) {
        rlsKontext.anwenden();
        return entityManager
                .createQuery("""
                        SELECT b FROM BuchungEntity b
                         WHERE b.kontoId = :konto AND b.bankreferenz = :referenz
                        """, BuchungEntity.class)
                .setParameter("konto", konto.wert())
                .setParameter("referenz", bankreferenz)
                .getResultStream()
                .findFirst()
                .map(BuchungEntity::zuDomaene);
    }

    /** Erstes Kriterium jeder Klassifikation - vor Mandatsreferenz, lange vor Namenstext. */
    @Transactional
    public List<Buchung> mitGegenparteiIban(Iban iban) {
        return nachSpalte("gegenparteiIban", iban.wert());
    }

    /** Grundlage der Acquirer-Unterscheidung: Mandate hängen an einer Gläubigerkennung. */
    @Transactional
    public List<Buchung> mitGlaeubigerkennung(String glaeubigerkennung) {
        return nachSpalte("glaeubigerkennung", glaeubigerkennung);
    }

    @Transactional
    public List<Buchung> mitMandatsreferenz(String mandatsreferenz) {
        return nachSpalte("mandatsreferenz", mandatsreferenz);
    }

    /** Summe der Splits einer Buchung. Die Datenbank hält sie gleich dem Buchungsbetrag. */
    @Transactional
    public Betrag splitsumme(BuchungId buchung) {
        rlsKontext.anwenden();
        BigDecimal summe = entityManager
                .createQuery(
                        "SELECT sum(s.betrag) FROM BuchungssplitEntity s WHERE s.buchungId = :buchung",
                        BigDecimal.class)
                .setParameter("buchung", buchung.wert())
                .getSingleResult();
        // Ohne Splits liefert die Summe NULL. Das kann nur eine Buchung sein, die der Aufrufer nicht
        // sehen darf - eine sichtbare hat immer mindestens einen Split, dafuer sorgt der Trigger.
        return summe == null ? Betrag.NULL_BETRAG : new Betrag(summe);
    }

    @Transactional
    public long anzahlSplits(BuchungId buchung) {
        rlsKontext.anwenden();
        return entityManager
                .createQuery("SELECT count(s) FROM BuchungssplitEntity s WHERE s.buchungId = :buchung", Long.class)
                .setParameter("buchung", buchung.wert())
                .getSingleResult();
    }

    /**
     * Abfrage über eine der strukturierten Spalten.
     *
     * <p>Der Spaltenname stammt ausschließlich aus den Aufrufern oben und nie von außen - er ist
     * hier eine Konstante des Codes, kein Parameter der Anwendung.
     */
    private List<Buchung> nachSpalte(String spalte, String wert) {
        rlsKontext.anwenden();
        return entityManager
                .createQuery(
                        "SELECT b FROM BuchungEntity b WHERE b." + spalte + " = :wert ORDER BY b.valuta",
                        BuchungEntity.class)
                .setParameter("wert", wert)
                .getResultList()
                .stream()
                .map(BuchungEntity::zuDomaene)
                .toList();
    }
}
