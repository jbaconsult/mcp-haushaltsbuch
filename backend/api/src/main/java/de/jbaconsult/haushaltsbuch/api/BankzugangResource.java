package de.jbaconsult.haushaltsbuch.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import de.jbaconsult.haushaltsbuch.kern.Bankzugang;
import de.jbaconsult.haushaltsbuch.kern.BankzugangId;
import de.jbaconsult.haushaltsbuch.kern.BankzugangService;
import de.jbaconsult.haushaltsbuch.kern.ExternesKonto;
import de.jbaconsult.haushaltsbuch.kern.ExternesKontoId;
import de.jbaconsult.haushaltsbuch.kern.InstitutKennung;

/**
 * Bankzugänge, externe Konten und deren Salden für das Dashboard.
 *
 * <p>Die Rückleitung nach der Autorisierung landet <b>nicht</b> hier, sondern im Frontend. Dort ist
 * die Anmeldesitzung bekannt, dort kann eine Zwischenseite stehen, und dort endet ein Abbruch mit
 * einer verständlichen Meldung statt mit einer weißen Seite. Das Frontend gibt anschließend den
 * Autorisierungscode an {@link #rueckleitungVerarbeiten} - serverseitig, damit er nie in einen
 * Anbieteraufruf aus dem Browser gerät.
 */
@Path("/api/bankzugaenge")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Bankzugang")
public class BankzugangResource {

    private final BankzugangService bankzugangService;
    private final String rueckleitung;
    private final int gueltigkeitTage;
    private final String land;

    @Inject
    public BankzugangResource(
            BankzugangService bankzugangService,
            /*
             * Die Rückleitungsadresse ist Konfiguration und keine Umgebungserkennung im Code. Beim
             * Anbieter sind genau zwei Adressen hinterlegt und damit verbindlich; eine Abweichung
             * lässt den Ablauf abbrechen, ohne dass der Fehler auf den Pfad zeigt.
             */
            @ConfigProperty(name = "haushaltsbuch.bankzugang.rueckleitung") String rueckleitung,
            @ConfigProperty(name = "haushaltsbuch.bankzugang.gueltigkeit-tage", defaultValue = "180")
                    int gueltigkeitTage,
            @ConfigProperty(name = "haushaltsbuch.bankzugang.land", defaultValue = "DE") String land) {
        this.bankzugangService = bankzugangService;
        this.rueckleitung = rueckleitung;
        this.gueltigkeitTage = gueltigkeitTage;
        this.land = land;
    }

    @GET
    public List<BankzugangDto> zugaenge() {
        Instant jetzt = Instant.now();
        return bankzugangService.zugaenge().stream()
                .map(zugang -> BankzugangDto.von(zugang, jetzt))
                .toList();
    }

    @GET
    @Path("/institute")
    public List<InstitutDto> institute(@QueryParam("land") String landparameter) {
        String gewaehlt = landparameter == null || landparameter.isBlank() ? land : landparameter;
        return bankzugangService.institute(gewaehlt).stream()
                .map(InstitutDto::von)
                .toList();
    }

    /**
     * Startet eine Autorisierung.
     *
     * <p>Der Zustandswert entsteht dabei in der Domäne und wird an Zugang und angemeldeten Benutzer
     * gebunden. Er erscheint in keiner Antwort - er geht ausschließlich über die Weiterleitung zum
     * Institut und kommt von dort zurück.
     */
    @POST
    public AutorisierungAntwort autorisierungStarten(AutorisierungAnfrage anfrage, @Context HttpHeaders kopfzeilen) {

        if (anfrage == null
                || anfrage.institutName() == null
                || anfrage.institutName().isBlank()) {
            throw new BadRequestException("Es wurde kein Institut angegeben.");
        }
        String institutsland =
                anfrage.institutLand() == null || anfrage.institutLand().isBlank() ? land : anfrage.institutLand();

        return new AutorisierungAntwort(bankzugangService
                .autorisierungStarten(
                        new InstitutKennung(anfrage.institutName(), institutsland),
                        Duration.ofDays(gueltigkeitTage),
                        rueckleitung,
                        ipAdresse(kopfzeilen))
                .weiterleitung());
    }

    /**
     * Verarbeitet die Rückleitung.
     *
     * <p>Zwei Fälle, beide behandelt: kommt ein Code, wird er serverseitig gegen eine Sitzung
     * getauscht. Kommt stattdessen ein Fehler, geht der Zugang in einen sichtbaren Fehlzustand und
     * behält die Meldung des Anbieters - nicht in einen stillen Abbruch.
     *
     * <p>In beiden Fällen wird zuerst der Zustandswert eingelöst. Passt er nicht, endet der Vorgang
     * dort: unbekannt, verbraucht, abgelaufen oder zu einem anderen Benutzer gehörend führt zur
     * Ablehnung, nicht zu einem Einrichtungsversuch.
     */
    @POST
    @Path("/rueckleitung")
    public BankzugangDto rueckleitungVerarbeiten(RueckleitungAnfrage anfrage) {
        if (anfrage == null || anfrage.zustand() == null || anfrage.zustand().isBlank()) {
            throw new BadRequestException("Die Rückleitung enthält keinen Vorgangsbezug.");
        }

        Instant jetzt = Instant.now();

        if (anfrage.fehler() != null && !anfrage.fehler().isBlank()) {
            String meldung = anfrage.fehlerbeschreibung() == null
                            || anfrage.fehlerbeschreibung().isBlank()
                    ? anfrage.fehler()
                    : anfrage.fehler() + ": " + anfrage.fehlerbeschreibung();
            return BankzugangDto.von(bankzugangService.rueckleitungAbgebrochen(anfrage.zustand(), meldung), jetzt);
        }

        if (anfrage.code() == null || anfrage.code().isBlank()) {
            throw new BadRequestException("Die Rückleitung enthält weder einen Code noch eine Fehlermeldung.");
        }

        return BankzugangDto.von(bankzugangService.rueckleitungVerarbeiten(anfrage.zustand(), anfrage.code()), jetzt);
    }

    /**
     * Holt Konten und Salden eines Zugangs neu.
     *
     * <p>Bewusst ein eigener Vorgang und keine Nebenwirkung des Lesens: ein Abruf, der beim
     * Anzeigen der Kontenliste losläuft, ist weder schnell noch vorhersagbar - und er lässt sich
     * nicht abstellen, wenn die Bank gerade nicht antwortet.
     */
    @POST
    @Path("/{id}/abrufen")
    public BankzugangDto abrufen(@PathParam("id") String id) {
        Bankzugang zugang = bankzugangService.abrufen(BankzugangId.von(id));
        return BankzugangDto.von(zugang, Instant.now());
    }

    // ------------------------------------------------------------------ Konten

    @GET
    @Path("/konten")
    public List<ExternesKontoDto> konten() {
        return bankzugangService.konten().stream()
                .map(konto -> ExternesKontoDto.von(konto, bankzugangService.letzteSalden(konto.id())))
                .toList();
    }

    /**
     * Ein einzelnes externes Konto mit allen Salden.
     *
     * <p>Liefert 404 sowohl für „gibt es nicht" als auch für „darfst du nicht sehen". Die
     * Ununterscheidbarkeit ist Absicht - ein 403 würde die Existenz eines fremden Kontos
     * bestätigen.
     */
    @GET
    @Path("/konten/{id}")
    public ExternesKontoDto konto(@PathParam("id") String id) {
        ExternesKontoId kontoId = ExternesKontoId.von(id);
        ExternesKonto konto = bankzugangService
                .konto(kontoId)
                .orElseThrow(() -> new NotFoundException("Externes Konto nicht gefunden: " + id));

        return ExternesKontoDto.von(konto, bankzugangService.salden(kontoId));
    }

    /**
     * Die einmalige Messung der Feldabdeckung.
     *
     * <p>Ausdrücklich kein Import. Das Ergebnis wird berichtet und nicht gespeichert; im Frontend
     * erscheint es nicht. Es beantwortet eine Frage, die über den Kanal entscheidet - ob
     * Mandatsreferenz und Gläubigerkennung überhaupt vorkommen.
     */
    @POST
    @Path("/konten/{id}/feldabdeckung")
    public FeldabdeckungDto feldabdeckung(@PathParam("id") String id) {
        return FeldabdeckungDto.von(bankzugangService.feldabdeckungMessen(ExternesKontoId.von(id)));
    }

    /**
     * Die IP des Menschen, falls das Institut sie verlangt.
     *
     * <p>Hinter einem Gegenstück steht die echte Adresse in {@code X-Forwarded-For}; die erste
     * Angabe darin ist der Ursprung. Fehlt sie, wird nichts gesetzt - einige Institute verlangen
     * entweder alle geforderten Kopfzeilen oder keine, und ein Teil davon führt zu einem Fehler,
     * der auf die Kopfzeile nicht hinweist.
     */
    private Optional<String> ipAdresse(HttpHeaders kopfzeilen) {
        return Optional.ofNullable(kopfzeilen.getHeaderString("X-Forwarded-For"))
                .map(wert -> wert.split(",")[0].trim())
                .filter(wert -> !wert.isBlank());
    }
}
