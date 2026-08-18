package de.jbaconsult.haushaltsbuch.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import de.jbaconsult.haushaltsbuch.kern.Benutzerkontext;

/**
 * Auskunft über die eigene Anmeldung.
 *
 * <p>Es gibt genau einen Zustand, den dieser Endpunkt sichtbar machen soll und den sonst niemand
 * sichtbar macht: <b>angemeldet, aber keinem fachlichen Benutzer zugeordnet</b>. Wer sich am Realm
 * anmelden kann, ist damit noch nicht in {@code benutzeridentitaet} eingetragen; der
 * {@link BenutzerkontextFilter} findet dann nichts, der Benutzerkontext bleibt leer, und die
 * Row-Level-Security liefert fail-closed null Zeilen.
 *
 * <p>Das Ergebnis ist eine leere Kontenliste, die wie ein Rechteproblem aussieht und keines ist.
 * Der Unterschied zwischen „du hast keine Konten" und „du bist nicht zugeordnet" ist der
 * Unterschied zwischen einer Stunde Suche und keiner.
 *
 * <p>Bewusst <b>keine</b> Selbstregistrierung: Dieser Endpunkt legt nichts an. Ein Token ohne
 * Zuordnung erzeugt keinen fachlichen Benutzer. Die Zuordnung ist ein bewusster Akt und bleibt
 * einer - siehe {@code doc/betrieb/anmeldung.md}.
 */
@Path("/api/ich")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Anmeldung")
public class AnmeldungResource {

    private final SecurityIdentity identitaet;
    private final Benutzerkontext benutzerkontext;

    @Inject
    public AnmeldungResource(SecurityIdentity identitaet, Benutzerkontext benutzerkontext) {
        this.identitaet = identitaet;
        this.benutzerkontext = benutzerkontext;
    }

    /**
     * Der Anmeldezustand des Aufrufers.
     *
     * <p>Der Subject wird zurückgegeben, damit er sich eintragen lässt, ohne ihn aus einem
     * Protokoll fischen zu müssen. Er ist die eigene Kennung des Aufrufers und diesem gegenüber
     * kein Geheimnis.
     */
    @GET
    public AnmeldungDto ich() {
        boolean zugeordnet = benutzerkontext.istGesetzt();

        // Zwei Quellen, weil es zwei Profile gibt. In prod kommt die Identität aus dem Token. In
        // dev und test ist OIDC abgeschaltet und der Benutzer stammt aus der Entwicklungsproperty
        // - dort ist die Identität anonym, der Kontext aber gesetzt. Ohne den zweiten Zweig
        // meldete dieser Endpunkt im Entwicklungsprofil „nicht angemeldet", während alles läuft.
        boolean angemeldet = !identitaet.isAnonymous() || zugeordnet;

        String subjekt =
                identitaet.isAnonymous() ? null : identitaet.getPrincipal().getName();

        return new AnmeldungDto(angemeldet, zugeordnet, subjekt);
    }

    /**
     * Anmeldezustand in der Darstellung für das Dashboard.
     *
     * @param angemeldet ob eine Identität feststeht
     * @param zugeordnet ob diese Identität einem fachlichen Benutzer zugeordnet ist
     * @param subjekt der {@code sub}-Claim, damit er sich eintragen lässt; {@code null} ohne
     *     Anmeldung
     */
    public record AnmeldungDto(boolean angemeldet, boolean zugeordnet, String subjekt) {}
}
