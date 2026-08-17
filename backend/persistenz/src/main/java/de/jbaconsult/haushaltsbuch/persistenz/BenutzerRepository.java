package de.jbaconsult.haushaltsbuch.persistenz;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import de.jbaconsult.haushaltsbuch.kern.BenutzerId;
import de.jbaconsult.haushaltsbuch.kern.BenutzeraufloesungPort;

/**
 * Auflösung der Anmeldeidentität auf den fachlichen Benutzer.
 *
 * <p>Sonderfall gegenüber allen anderen Repositories: diese Abfrage läuft, <b>bevor</b> der
 * Benutzerkontext feststeht - sie stellt ihn erst her. Sie geht deshalb über
 * {@link BenutzeridentitaetEntity}, dessen Tabelle bewusst keine Zugriffskontrolle trägt. Die
 * Begründung steht in {@code V1__grundschema.sql}.
 */
@ApplicationScoped
public class BenutzerRepository implements BenutzeraufloesungPort {

    private final EntityManager entityManager;

    @Inject
    public BenutzerRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public Optional<BenutzerId> findeNachOidcSubjekt(String oidcSubjekt) {
        if (oidcSubjekt == null || oidcSubjekt.isBlank()) {
            return Optional.empty();
        }

        return entityManager
                .createQuery(
                        "SELECT i FROM BenutzeridentitaetEntity i WHERE i.oidcSubjekt = :subjekt",
                        BenutzeridentitaetEntity.class)
                .setParameter("subjekt", oidcSubjekt)
                .getResultStream()
                .findFirst()
                .map(identitaet -> new BenutzerId(identitaet.benutzerId));
    }
}
