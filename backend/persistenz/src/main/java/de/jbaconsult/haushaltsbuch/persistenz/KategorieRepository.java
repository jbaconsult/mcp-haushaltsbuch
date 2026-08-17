package de.jbaconsult.haushaltsbuch.persistenz;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import de.jbaconsult.haushaltsbuch.kern.Kategorie;
import de.jbaconsult.haushaltsbuch.kern.KategorieId;

/**
 * Zugriff auf die Kategorientaxonomie.
 *
 * <p>Kategorien sind haushaltsweit sichtbar - HB-05 stellt fest, dass es innerhalb der Ehe keinen
 * Geheimhaltungsbedarf gibt, und zwei Menschen mit verschiedenen Kategorienlisten können über
 * denselben Haushalt nicht reden. Die Policy in {@code V2__ledger.sql} verlangt trotzdem einen
 * gesetzten Benutzerkontext: ohne ihn ist die Liste leer, nicht vollständig.
 *
 * <p>Kein Löschen in dieser Schnittstelle. ADR-0004 verlangt Merge oder Sperre statt Kaskade;
 * {@code aktiv} auf falsch zu setzen ist der Weg, eine Kategorie aus der Auswahl zu nehmen, ohne
 * die Historie anzutasten. Ein echtes Löschen läuft in der Datenbank ohnehin gegen den
 * {@code RESTRICT}-Fremdschlüssel vom Split her.
 */
@ApplicationScoped
public class KategorieRepository {

    private final EntityManager entityManager;
    private final RlsKontext rlsKontext;

    @Inject
    public KategorieRepository(EntityManager entityManager, RlsKontext rlsKontext) {
        this.entityManager = entityManager;
        this.rlsKontext = rlsKontext;
    }

    @Transactional
    public List<Kategorie> alleAktiven() {
        rlsKontext.anwenden();
        return entityManager.createQuery("""
                        SELECT k FROM KategorieEntity k
                         WHERE k.aktiv = true
                         ORDER BY k.bezeichnung
                        """, KategorieEntity.class).getResultList().stream()
                .map(KategorieEntity::zuDomaene)
                .toList();
    }

    @Transactional
    public Optional<Kategorie> findeNachId(KategorieId id) {
        rlsKontext.anwenden();
        return entityManager
                .createQuery("SELECT k FROM KategorieEntity k WHERE k.id = :id", KategorieEntity.class)
                .setParameter("id", id.wert())
                .getResultStream()
                .findFirst()
                .map(KategorieEntity::zuDomaene);
    }
}
