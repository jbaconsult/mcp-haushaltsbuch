package de.jbaconsult.haushaltsbuch.kern;

import java.util.Optional;

/**
 * Auflösung einer Anmeldeidentität auf den fachlichen Benutzer.
 *
 * <p>Der Port existiert, damit die Eingangsschichten {@code api} und {@code mcp} den
 * Benutzerkontext setzen können, ohne {@code persistenz} zu kennen. Die Abhängigkeit zeigt nach
 * innen: beide kennen nur {@code kern}.
 */
public interface BenutzeraufloesungPort {

    /**
     * Findet den Benutzer zu einem OIDC-Subject.
     *
     * <p>Leer, wenn der Subject unbekannt ist. Ein gültiges Token allein reicht also nicht - die
     * Person muss in diesem System auch angelegt sein.
     */
    Optional<BenutzerId> findeNachOidcSubjekt(String oidcSubjekt);
}
