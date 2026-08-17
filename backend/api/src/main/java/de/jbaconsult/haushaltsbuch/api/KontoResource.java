package de.jbaconsult.haushaltsbuch.api;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import de.jbaconsult.haushaltsbuch.kern.KontoId;
import de.jbaconsult.haushaltsbuch.kern.KontoService;

/** Konten für das Dashboard. */
@Path("/api/konten")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Konten")
public class KontoResource {

    private final KontoService kontoService;

    @Inject
    public KontoResource(KontoService kontoService) {
        this.kontoService = kontoService;
    }

    @GET
    public List<KontoDto> konten() {
        return kontoService.sichtbareKonten().stream().map(KontoDto::von).toList();
    }

    /**
     * Ein einzelnes Konto.
     *
     * <p>Liefert 404 sowohl für „gibt es nicht" als auch für „darfst du nicht sehen". Die
     * Ununterscheidbarkeit ist Absicht - ein 403 an dieser Stelle würde die Existenz eines fremden
     * Kontos bestätigen.
     */
    @GET
    @Path("/{id}")
    public KontoDto konto(@PathParam("id") String id) {
        return kontoService
                .konto(KontoId.von(id))
                .map(KontoDto::von)
                .orElseThrow(() -> new NotFoundException("Konto nicht gefunden: " + id));
    }
}
