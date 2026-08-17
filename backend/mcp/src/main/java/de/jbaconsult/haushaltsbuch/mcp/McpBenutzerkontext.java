package de.jbaconsult.haushaltsbuch.mcp;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.jbaconsult.haushaltsbuch.kern.BenutzeraufloesungPort;
import de.jbaconsult.haushaltsbuch.kern.Benutzerkontext;

/**
 * Setzt den Benutzer für einen MCP-Werkzeugaufruf.
 *
 * <p>Wird von jedem Tool zu Beginn aufgerufen. Das ist bewusst sichtbar und nicht in einen
 * Interceptor versteckt: MCP-Aufrufe laufen nicht durch die JAX-RS-Filterkette, und ein
 * Zugriffskontext, der scheinbar von selbst entsteht, ist bei einem System mit Kontodaten das
 * Falsche.
 *
 * <p>Vergisst ein Tool den Aufruf, liefert es nichts - die Policies in der Datenbank sind
 * fail-closed. Der Fehler ist damit sofort sichtbar und harmlos.
 *
 * <p><b>Ausbaupunkt:</b> Die Extension-Linie 2.x bringt {@code quarkus-mcp-server-oidc} mit, das
 * die Identität für MCP-Verbindungen selbst auflöst. Sobald sie final ist, ersetzt sie diese
 * Klasse. Siehe ADR-0001.
 */
@ApplicationScoped
public class McpBenutzerkontext {

    private final SecurityIdentity identitaet;
    private final BenutzeraufloesungPort benutzeraufloesung;
    private final Benutzerkontext benutzerkontext;
    private final Optional<String> entwicklungsSubjekt;

    @Inject
    public McpBenutzerkontext(
            SecurityIdentity identitaet,
            BenutzeraufloesungPort benutzeraufloesung,
            Benutzerkontext benutzerkontext,
            @ConfigProperty(name = "haushaltsbuch.entwicklung.benutzer-subjekt") Optional<String> entwicklungsSubjekt) {
        this.identitaet = identitaet;
        this.benutzeraufloesung = benutzeraufloesung;
        this.benutzerkontext = benutzerkontext;
        this.entwicklungsSubjekt = entwicklungsSubjekt;
    }

    public void anwenden() {
        subjekt().flatMap(benutzeraufloesung::findeNachOidcSubjekt).ifPresent(benutzerkontext::setzen);
    }

    private Optional<String> subjekt() {
        if (!identitaet.isAnonymous()) {
            return Optional.ofNullable(identitaet.getPrincipal().getName());
        }
        // Harte Sperre statt Vertrauen darauf, dass die Property in Produktion ungesetzt bleibt.
        if (LaunchMode.current().isDevOrTest()) {
            return entwicklungsSubjekt;
        }
        return Optional.empty();
    }
}
