package de.jbaconsult.haushaltsbuch.api;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import de.jbaconsult.haushaltsbuch.kern.BenutzeraufloesungPort;
import de.jbaconsult.haushaltsbuch.kern.Benutzerkontext;

/**
 * Setzt den Benutzer der Anfrage aus dem OIDC-Token.
 *
 * <p>Der Subject-Claim wird über {@link BenutzeraufloesungPort} auf den fachlichen Benutzer
 * abgebildet und in {@link Benutzerkontext} abgelegt. {@code persistenz} überträgt ihn von dort in
 * die Datenbanktransaktion.
 *
 * <p>Schlägt die Auflösung fehl, wird die Anfrage <b>nicht</b> abgewiesen. Sie läuft ohne
 * Benutzerkontext weiter - und die Policies liefern dann keine Zeile. Das ist die richtige
 * Reaktion: der Aufrufer sieht nichts, statt einer Fehlermeldung, die verrät, dass sein Token zwar
 * gültig, er hier aber unbekannt ist.
 */
@Provider
public class BenutzerkontextFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(BenutzerkontextFilter.class);

    private final SecurityIdentity identitaet;
    private final BenutzeraufloesungPort benutzeraufloesung;
    private final Benutzerkontext benutzerkontext;
    private final Optional<String> entwicklungsSubjekt;

    @Inject
    public BenutzerkontextFilter(
            SecurityIdentity identitaet,
            BenutzeraufloesungPort benutzeraufloesung,
            Benutzerkontext benutzerkontext,
            @ConfigProperty(name = "haushaltsbuch.entwicklung.benutzer-subjekt") Optional<String> entwicklungsSubjekt) {
        this.identitaet = identitaet;
        this.benutzeraufloesung = benutzeraufloesung;
        this.benutzerkontext = benutzerkontext;
        this.entwicklungsSubjekt = entwicklungsSubjekt;
    }

    @Override
    public void filter(ContainerRequestContext anfrage) {
        subjekt()
                .flatMap(benutzeraufloesung::findeNachOidcSubjekt)
                .ifPresentOrElse(
                        benutzerkontext::setzen,
                        () -> LOG.debug(
                                "Kein Benutzerkontext für diese Anfrage - die Zugriffskontrolle liefert nichts."));
    }

    private Optional<String> subjekt() {
        if (!identitaet.isAnonymous()) {
            return Optional.ofNullable(identitaet.getPrincipal().getName());
        }
        return entwicklungsBenutzer();
    }

    /**
     * Fester Benutzer ohne Anmeldung - ausschließlich für Dev Mode und Tests.
     *
     * <p>Die Prüfung auf {@link LaunchMode} ist eine harte Sperre und kein Vertrauen darauf, dass
     * die Property in Produktion ungesetzt bleibt. Eine falsch gesetzte Konfigurationsvariable
     * würde sonst die gesamte Anmeldung aushebeln - lautlos.
     */
    private Optional<String> entwicklungsBenutzer() {
        if (!LaunchMode.current().isDevOrTest()) {
            return Optional.empty();
        }
        return entwicklungsSubjekt;
    }
}
