package de.jbaconsult.haushaltsbuch.kern;

import java.util.Optional;

import jakarta.enterprise.context.RequestScoped;

/**
 * Der Benutzer der laufenden Anfrage.
 *
 * <p>Wird von der jeweiligen Eingangsschicht gesetzt - {@code api} aus dem OIDC-Subject des Tokens,
 * {@code mcp} aus der Identität der MCP-Verbindung - und von {@code persistenz} gelesen, um den
 * Datenbankkontext für Row-Level-Security zu setzen.
 *
 * <p>Der Kontext liegt in {@code kern}, weil er der gemeinsame Nenner beider Eingangsschichten ist.
 * Er kennt bewusst weder Quarkus Security noch HTTP.
 *
 * <p><b>Nicht gesetzt heißt nicht sichtbar.</b> Fehlt der Benutzer, liefern die Policies in der
 * Datenbank keine Zeile. Das ist die richtige Fehlerrichtung: der Fehler wird sofort sichtbar und
 * ist harmlos, statt still zu viele Daten freizugeben.
 */
@RequestScoped
public class Benutzerkontext {

    private BenutzerId benutzerId;

    public void setzen(BenutzerId benutzerId) {
        this.benutzerId = benutzerId;
    }

    public Optional<BenutzerId> benutzerId() {
        return Optional.ofNullable(benutzerId);
    }

    public boolean istGesetzt() {
        return benutzerId != null;
    }
}
