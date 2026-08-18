package de.jbaconsult.haushaltsbuch.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

import de.jbaconsult.haushaltsbuch.kern.Zugangsfehler;

/**
 * Übersetzt einen {@link Zugangsfehler} in eine Antwort mit lesbarer Meldung.
 *
 * <p>Die Meldung des Anbieters wird durchgereicht statt durch ein eigenes „Fehler beim Abruf"
 * ersetzt. Eine Oberfläche, die den Grund verschweigt, während der Anbieter „Zustimmung
 * abgelaufen" gemeldet hat, kostet den Menschen davor eine halbe Stunde.
 *
 * <p>Status 409 und nicht 500: der Vorgang ist gescheitert, nicht die Anwendung. Ein 500 würde in
 * jeder Überwachung als Störung auftauchen, obwohl ein abgelehnter Autorisierungscode der
 * Normalfall eines abgebrochenen Vorgangs ist.
 */
@Provider
public class ZugangsfehlerMapper implements ExceptionMapper<Zugangsfehler> {

    private static final Logger LOG = Logger.getLogger(ZugangsfehlerMapper.class);

    @Override
    public Response toResponse(Zugangsfehler fehler) {
        LOG.debugf(fehler, "Bankzugang: %s", fehler.getMessage());

        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(new Fehlerantwort(fehler.getMessage()))
                .build();
    }

    public record Fehlerantwort(String meldung) {}
}
