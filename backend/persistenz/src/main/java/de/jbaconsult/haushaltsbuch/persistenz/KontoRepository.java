package de.jbaconsult.haushaltsbuch.persistenz;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import de.jbaconsult.haushaltsbuch.kern.Konto;
import de.jbaconsult.haushaltsbuch.kern.KontoId;
import de.jbaconsult.haushaltsbuch.kern.KontoPort;

/**
 * Zugriff auf Konten.
 *
 * <p>Die Abfragen enthalten <b>keine</b> Zugriffsbedingung. Das ist Absicht: die Filterung liegt in
 * der Datenbank als Row-Level-Security. Eine zweite Bedingung im Java-Code wäre ein zweites
 * Regelwerk, das mit der Zeit vom ersten abweicht - und wenn beide sich widersprechen, gewinnt das
 * restriktivere, während man lange nach der Ursache verschwundener Zeilen sucht.
 *
 * <p>Jede Methode ist {@code @Transactional} und ruft zuerst {@link RlsKontext#anwenden()}. Beides
 * ist notwendig: der Kontext gilt transaktionslokal, es muss also eine Transaktion offen sein.
 */
@ApplicationScoped
public class KontoRepository implements KontoPort {

    private final EntityManager entityManager;
    private final RlsKontext rlsKontext;

    @Inject
    public KontoRepository(EntityManager entityManager, RlsKontext rlsKontext) {
        this.entityManager = entityManager;
        this.rlsKontext = rlsKontext;
    }

    @Override
    @Transactional
    public List<Konto> alleSichtbaren() {
        rlsKontext.anwenden();

        return entityManager
                .createQuery("SELECT k FROM KontoEntity k ORDER BY k.bezeichnung", KontoEntity.class)
                .getResultList()
                .stream()
                .map(KontoEntity::zuDomaene)
                .toList();
    }

    @Override
    @Transactional
    public Optional<Konto> findeNachId(KontoId id) {
        rlsKontext.anwenden();

        // Kein find() auf dem EntityManager: der kann aus dem Persistenzkontext bedienen und die
        // Datenbank gar nicht erst befragen - womit die Policies nicht greifen würden.
        return entityManager
                .createQuery("SELECT k FROM KontoEntity k WHERE k.id = :id", KontoEntity.class)
                .setParameter("id", id.wert())
                .getResultStream()
                .findFirst()
                .map(KontoEntity::zuDomaene);
    }
}
