package de.jbaconsult.haushaltsbuch.persistenz;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import de.jbaconsult.haushaltsbuch.kern.Bankzugang;
import de.jbaconsult.haushaltsbuch.kern.BankzugangId;
import de.jbaconsult.haushaltsbuch.kern.BankzugangPort;
import de.jbaconsult.haushaltsbuch.kern.BenutzerId;
import de.jbaconsult.haushaltsbuch.kern.ExternerSaldo;
import de.jbaconsult.haushaltsbuch.kern.ExternesKonto;
import de.jbaconsult.haushaltsbuch.kern.ExternesKontoId;
import de.jbaconsult.haushaltsbuch.kern.Iban;
import de.jbaconsult.haushaltsbuch.kern.KontoId;
import de.jbaconsult.haushaltsbuch.kern.Kontokennung;

/**
 * Zugriff auf Bankzugänge, externe Konten und Salden.
 *
 * <p>Wie in {@link KontoRepository} enthalten die Abfragen <b>keine</b> Zugriffsbedingung: die
 * Filterung liegt als Row-Level-Security in der Datenbank. Ein zweites Regelwerk im Java-Code
 * würde mit der Zeit vom ersten abweichen.
 */
@ApplicationScoped
public class BankzugangRepository implements BankzugangPort {

    /** Name des Abfrageparameters für den Bankzugang. */
    private static final String P_ZUGANG = "zugang";

    private final EntityManager entityManager;
    private final RlsKontext rlsKontext;

    @Inject
    public BankzugangRepository(EntityManager entityManager, RlsKontext rlsKontext) {
        this.entityManager = entityManager;
        this.rlsKontext = rlsKontext;
    }

    // ------------------------------------------------------------------ Zugang

    @Override
    @Transactional
    public void anlegen(Bankzugang zugang) {
        rlsKontext.anwenden();

        BankzugangEntity entity = new BankzugangEntity();
        entity.ausDomaene(zugang);
        entityManager.persist(entity);
    }

    @Override
    @Transactional
    public void aktualisieren(Bankzugang zugang) {
        rlsKontext.anwenden();

        entity(zugang.id()).ifPresent(entity -> entity.ausDomaene(zugang));
    }

    @Override
    @Transactional
    public Optional<Bankzugang> findeZugang(BankzugangId id) {
        rlsKontext.anwenden();

        return entity(id).map(BankzugangEntity::zuDomaene);
    }

    @Override
    @Transactional
    public List<Bankzugang> alleZugaenge() {
        rlsKontext.anwenden();

        return entityManager
                .createQuery("SELECT z FROM BankzugangEntity z ORDER BY z.angelegtAm DESC", BankzugangEntity.class)
                .getResultList()
                .stream()
                .map(BankzugangEntity::zuDomaene)
                .toList();
    }

    /**
     * Entfernt einen Bankzugang.
     *
     * <p>Der Zugangsbezug der Konten wird hier ausdrücklich genullt und nicht der Datenbank
     * überlassen. Zwar tut {@code ON DELETE SET NULL} dasselbe, aber Hibernate hält die Konten
     * dieser Transaktion womöglich schon im Persistenzkontext - dort stünde nach dem Löschen
     * weiterhin die alte Kennung, und der nächste Lesezugriff bekäme sie zurück. Die Datenbank
     * bleibt die Absicherung, nicht der einzige Weg.
     */
    @Override
    @Transactional
    public int entfernen(BankzugangId id) {
        rlsKontext.anwenden();

        int geloest =
                entityManager.createQuery("""
                        UPDATE ExternesKontoEntity k
                           SET k.bankzugangId = NULL
                         WHERE k.bankzugangId = :zugang
                        """).setParameter(P_ZUGANG, id.wert()).executeUpdate();

        entity(id).ifPresent(entityManager::remove);
        return geloest;
    }

    /**
     * Entfernt die externen Konten eines Zugangs samt ihrer Salden.
     *
     * <p>Die Salden werden ausdrücklich zuerst gelöscht. Der Fremdschlüssel trägt zwar
     * {@code ON DELETE CASCADE}, aber eine Bulk-Anweisung auf den Konten umgeht den
     * Persistenzkontext - die Salden blieben dann bis zum Flush als verwaiste Verweise stehen und
     * die Reihenfolge entschiede über den Erfolg. Explizit ist sie nachvollziehbar.
     */
    @Override
    @Transactional
    public int kontenEntfernen(BankzugangId id) {
        rlsKontext.anwenden();

        entityManager.createQuery("""
                        DELETE FROM ExternerSaldoEntity s
                         WHERE s.externesKontoId IN (SELECT k.id FROM ExternesKontoEntity k
                                                      WHERE k.bankzugangId = :zugang)
                        """).setParameter(P_ZUGANG, id.wert()).executeUpdate();

        return entityManager
                .createQuery("DELETE FROM ExternesKontoEntity k WHERE k.bankzugangId = :zugang")
                .setParameter(P_ZUGANG, id.wert())
                .executeUpdate();
    }

    // ----------------------------------------------------------------- Zustand

    @Override
    @Transactional
    public void zustandHinterlegen(BankzugangId zugang, String zustand, BenutzerId benutzer, Instant gueltigBis) {
        rlsKontext.anwenden();

        entity(zugang).ifPresent(entity -> {
            entity.zustand = zustand;
            entity.zustandGueltigBis = gueltigBis;
            entity.zustandVerbraucht = false;
            // Der Benutzer steht in angelegtVon und wird beim Einlösen dagegen geprüft. Eine
            // zweite Spalte dafür wäre eine zweite Wahrheit über denselben Sachverhalt.
            entity.angelegtVon = benutzer.wert();
        });
    }

    /**
     * Löst einen Zustandswert ein.
     *
     * <p>Die Prüfung sitzt in der Abfrage, nicht im Java-Code danach: Wert, Gültigkeit, Verbrauch
     * und Benutzer werden gemeinsam geprüft, und was nicht passt, kommt gar nicht erst zurück. Ein
     * nachgelagertes {@code if} wäre eine Prüfung, die man beim nächsten Umbau versehentlich
     * verschiebt.
     *
     * <p>Alle Ablehnungsgründe sind von außen ununterscheidbar. Ein „abgelaufen" statt „unbekannt"
     * würde bestätigen, dass es den Vorgang gibt.
     */
    @Override
    @Transactional
    public Optional<BankzugangId> zustandEinloesen(String zustand, BenutzerId benutzer, Instant jetzt) {
        rlsKontext.anwenden();

        if (zustand == null || zustand.isBlank()) {
            return Optional.empty();
        }

        Optional<BankzugangEntity> treffer = entityManager
                .createQuery("""
                        SELECT z FROM BankzugangEntity z
                         WHERE z.zustand = :zustand
                           AND z.zustandVerbraucht = false
                           AND z.zustandGueltigBis > :jetzt
                           AND z.angelegtVon = :benutzer
                        """, BankzugangEntity.class)
                .setParameter("zustand", zustand)
                .setParameter("jetzt", jetzt)
                .setParameter("benutzer", benutzer.wert())
                .getResultStream()
                .findFirst();

        // Einmalig: der Verbrauch wird sofort gesetzt, nicht erst nach erfolgreichem Eintausch.
        // Sonst könnte derselbe Wert zweimal eingelöst werden, solange der erste Vorgang noch
        // läuft.
        treffer.ifPresent(entity -> entity.zustandVerbraucht = true);

        return treffer.map(entity -> new BankzugangId(entity.id));
    }

    // ------------------------------------------------------------------ Konten

    /**
     * Legt ein externes Konto an oder aktualisiert das vorhandene.
     *
     * <p>Erkannt wird es an der stabilen Kennung. Genau hier entscheidet sich, ob eine zweite
     * Autorisierung desselben Kontos einen zweiten Datensatz erzeugt - sie tut es nicht.
     *
     * @return die Kennung des angelegten oder aktualisierten Kontos
     */
    @Override
    @Transactional
    public ExternesKontoId kontoUebernehmen(ExternesKonto konto) {
        rlsKontext.anwenden();

        Optional<ExternesKontoEntity> vorhanden = kontoEntityNachKennung(konto.kennung());
        Instant jetzt = Instant.now();

        ExternesKontoEntity entity = vorhanden.orElseGet(ExternesKontoEntity::new);
        boolean istNeu = vorhanden.isEmpty();
        if (istNeu) {
            entity.id = konto.id().wert();
            entity.kennung = konto.kennung().wert();
            entity.angelegtAm = jetzt;
        }

        entity.bankzugangId = konto.bankzugang().map(BankzugangId::wert).orElse(null);
        entity.iban = konto.iban().map(Iban::wert).orElse(null);
        entity.waehrung = konto.waehrung();
        entity.kontoart = konto.kontoart().orElse(null);
        entity.produktname = konto.produktname().orElse(null);
        entity.bezeichnung = konto.bezeichnung();
        entity.aktualisiertAm = jetzt;

        // Eine bestehende Zuordnung auf ein fachliches Konto wird nicht überschrieben. Sie ist von
        // Hand gesetzt worden; ein Abruf darf eine menschliche Entscheidung nicht zurücknehmen.
        if (entity.kontoId == null) {
            entity.kontoId = konto.zugeordnetesKonto().map(KontoId::wert).orElse(null);
        }

        // Erst persistieren, wenn alle Pflichtfelder stehen. Ein persist() auf einer halb
        // gefuellten Entitaet schlaegt beim naechsten Flush fehl - und zwar mit einer Meldung,
        // die auf die Reihenfolge nicht hinweist.
        if (istNeu) {
            entityManager.persist(entity);
        }

        return new ExternesKontoId(entity.id);
    }

    @Override
    @Transactional
    public List<ExternesKonto> kontenDesZugangs(BankzugangId zugang) {
        rlsKontext.anwenden();

        return entityManager
                .createQuery("""
                        SELECT k FROM ExternesKontoEntity k
                         WHERE k.bankzugangId = :zugang
                         ORDER BY k.bezeichnung
                        """, ExternesKontoEntity.class)
                .setParameter(P_ZUGANG, zugang.wert())
                .getResultList()
                .stream()
                .map(ExternesKontoEntity::zuDomaene)
                .toList();
    }

    @Override
    @Transactional
    public List<ExternesKonto> alleKonten() {
        rlsKontext.anwenden();

        return entityManager
                .createQuery("SELECT k FROM ExternesKontoEntity k ORDER BY k.bezeichnung", ExternesKontoEntity.class)
                .getResultList()
                .stream()
                .map(ExternesKontoEntity::zuDomaene)
                .toList();
    }

    @Override
    @Transactional
    public Optional<ExternesKonto> findeKonto(ExternesKontoId id) {
        rlsKontext.anwenden();

        return entityManager
                .createQuery("SELECT k FROM ExternesKontoEntity k WHERE k.id = :id", ExternesKontoEntity.class)
                .setParameter("id", id.wert())
                .getResultStream()
                .findFirst()
                .map(ExternesKontoEntity::zuDomaene);
    }

    @Override
    @Transactional
    public Optional<ExternesKonto> findeKontoNachKennung(Kontokennung kennung) {
        rlsKontext.anwenden();

        return kontoEntityNachKennung(kennung).map(ExternesKontoEntity::zuDomaene);
    }

    // ------------------------------------------------------------------ Salden

    @Override
    @Transactional
    public void saldoAblegen(ExternesKontoId konto, ExternerSaldo saldo) {
        rlsKontext.anwenden();

        ExternerSaldoEntity entity = new ExternerSaldoEntity();
        entity.id = UUID.randomUUID();
        entity.externesKontoId = konto.wert();
        entity.art = saldo.art();
        entity.artOriginal = saldo.artOriginal();
        entity.betrag = saldo.betrag().wert();
        entity.waehrung = saldo.waehrung();
        entity.referenzdatum = saldo.referenzdatum().orElse(null);
        entity.abgerufenAm = saldo.abgerufenAm();
        entityManager.persist(entity);
    }

    @Override
    @Transactional
    public List<ExternerSaldo> saldenDesKontos(ExternesKontoId konto) {
        rlsKontext.anwenden();

        return entityManager
                .createQuery("""
                        SELECT s FROM ExternerSaldoEntity s
                         WHERE s.externesKontoId = :konto
                         ORDER BY s.abgerufenAm DESC, s.art
                        """, ExternerSaldoEntity.class)
                .setParameter("konto", konto.wert())
                .getResultList()
                .stream()
                .map(ExternerSaldoEntity::zuDomaene)
                .toList();
    }

    /**
     * Der jeweils zuletzt abgerufene Saldo je Art.
     *
     * <p>Die Auswahl geschieht in der Datenbank und nicht durch Filtern der Gesamthistorie in Java:
     * die Historie wächst mit jedem Abruf, und ein Konto, das ein Jahr lang täglich abgerufen wird,
     * hätte sonst tausend Zeilen im Speicher, um vier davon zu behalten.
     */
    @Override
    @Transactional
    public List<ExternerSaldo> letzteSalden(ExternesKontoId konto) {
        rlsKontext.anwenden();

        return entityManager
                .createQuery("""
                        SELECT s FROM ExternerSaldoEntity s
                         WHERE s.externesKontoId = :konto
                           AND s.abgerufenAm = (SELECT MAX(j.abgerufenAm) FROM ExternerSaldoEntity j
                                                 WHERE j.externesKontoId = s.externesKontoId
                                                   AND j.art = s.art)
                         ORDER BY s.art
                        """, ExternerSaldoEntity.class)
                .setParameter("konto", konto.wert())
                .getResultList()
                .stream()
                .map(ExternerSaldoEntity::zuDomaene)
                .toList();
    }

    // ------------------------------------------------------------------ Hilfen

    private Optional<BankzugangEntity> entity(BankzugangId id) {
        return entityManager
                .createQuery("SELECT z FROM BankzugangEntity z WHERE z.id = :id", BankzugangEntity.class)
                .setParameter("id", id.wert())
                .getResultStream()
                .findFirst();
    }

    private Optional<ExternesKontoEntity> kontoEntityNachKennung(Kontokennung kennung) {
        return entityManager
                .createQuery(
                        "SELECT k FROM ExternesKontoEntity k WHERE k.kennung = :kennung", ExternesKontoEntity.class)
                .setParameter("kennung", kennung.wert())
                .getResultStream()
                .findFirst();
    }
}
