package de.jbaconsult.haushaltsbuch.bankzugang;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.jbaconsult.haushaltsbuch.kern.Zugangsfehler;

/**
 * Signiert die Aufrufe an den Bankanbieter.
 *
 * <p>Der Anbieter authentifiziert nicht über ein Geheimnis, das mitgeschickt wird, sondern über ein
 * je Aufruf signiertes JWT: privater Schlüssel hier, öffentlicher als Zertifikat bei der
 * registrierten Anwendung.
 *
 * <p><b>Nichts davon steht im Quelltext</b> - weder der Schlüssel noch der Pfad dorthin noch die
 * Anwendungs-ID. Letztere ist kein Geheimnis, sondern nur die Schlüsselkennung im JWT-Kopf; sie ist
 * trotzdem Konfiguration, weil jede Fremdinstallation eine eigene mitbringt. Ein fest verdrahteter
 * Wert wäre ein Fehler, der erst beim zweiten Nutzer auffällt.
 */
@ApplicationScoped
public class Anwendungsschluessel {

    /**
     * Laufzeit eines erzeugten Tokens.
     *
     * <p>Der Anbieter erlaubt bis zu 24 Stunden. Eine Stunde reicht für jeden Vorgang und begrenzt
     * den Schaden, falls ein Token doch einmal in einem Protokoll landet.
     */
    private static final Duration TOKEN_LAUFZEIT = Duration.ofHours(1);

    /** Abstand zum Ablauf, ab dem ein neues Token erzeugt wird. */
    private static final Duration ERNEUERUNG_VOR_ABLAUF = Duration.ofMinutes(5);

    private static final String AUSSTELLER = "enablebanking.com";
    private static final String EMPFAENGER = "api.enablebanking.com";

    private final String anwendungsId;
    private final Optional<String> schluesselPfad;

    private PrivateKey schluessel;
    private String zwischengespeichertesToken;
    private Instant tokenLaeuftAbUm = Instant.EPOCH;

    public Anwendungsschluessel(
            @ConfigProperty(name = "haushaltsbuch.bankzugang.anwendungs-id") Optional<String> anwendungsId,
            @ConfigProperty(name = "haushaltsbuch.bankzugang.schluessel-pfad") Optional<String> schluesselPfad) {
        this.anwendungsId = anwendungsId.orElse("");
        this.schluesselPfad = schluesselPfad;
    }

    /**
     * Ob der Zugang überhaupt eingerichtet ist.
     *
     * <p>Wird gebraucht, damit die Anwendung ohne Anbieterzugang startet und das Dashboard einen
     * verständlichen Hinweis zeigt, statt beim ersten Klick mit einem Stapelabzug zu antworten.
     */
    public boolean istEingerichtet() {
        return !anwendungsId.isBlank()
                && schluesselPfad.filter(pfad -> !pfad.isBlank()).isPresent();
    }

    @PostConstruct
    void schluesselLaden() {
        if (!istEingerichtet()) {
            return;
        }
        Path pfad = Path.of(schluesselPfad.orElseThrow());
        if (!Files.isReadable(pfad)) {
            // Kein Abbruch beim Start: eine Anwendung, die wegen eines fehlenden Bankzugangs nicht
            // hochkommt, nimmt auch alles andere mit - Dashboard, Ledger, Anmeldung.
            return;
        }
        try {
            this.schluessel = ausPem(Files.readString(pfad, StandardCharsets.UTF_8));
        } catch (Exception fehler) {
            throw new Zugangsfehler(
                    "Der private Schlüssel unter " + pfad + " ist nicht lesbar. Erwartet wird PKCS#8 "
                            + "(BEGIN PRIVATE KEY). Ein Schlüssel im Format PKCS#1 (BEGIN RSA PRIVATE KEY) "
                            + "lässt sich umwandeln mit: openssl pkcs8 -topk8 -nocrypt -in alt.pem -out neu.pem",
                    fehler);
        }
    }

    /** Ein gültiges Bearer-Token für den nächsten Aufruf. */
    public synchronized String token() {
        if (schluessel == null) {
            schluesselLaden();
        }
        if (schluessel == null) {
            throw new Zugangsfehler("Der Bankzugang ist nicht eingerichtet. Es fehlen Anwendungs-ID oder privater "
                    + "Schlüssel - beides ist Konfiguration, siehe .env.example.");
        }

        Instant jetzt = Instant.now();
        if (zwischengespeichertesToken != null && jetzt.isBefore(tokenLaeuftAbUm.minus(ERNEUERUNG_VOR_ABLAUF))) {
            return zwischengespeichertesToken;
        }

        Instant ablauf = jetzt.plus(TOKEN_LAUFZEIT);
        String kopf = """
                {"typ":"JWT","alg":"RS256","kid":"%s"}""".formatted(anwendungsId);
        String rumpf = """
                {"iss":"%s","aud":"%s","iat":%d,"exp":%d}""".formatted(AUSSTELLER, EMPFAENGER, jetzt.getEpochSecond(), ablauf.getEpochSecond());

        String zuSignieren = base64(kopf) + "." + base64(rumpf);
        String signatur = signieren(zuSignieren);

        zwischengespeichertesToken = zuSignieren + "." + signatur;
        tokenLaeuftAbUm = ablauf;
        return zwischengespeichertesToken;
    }

    private String signieren(String daten) {
        try {
            Signature signatur = Signature.getInstance("SHA256withRSA");
            signatur.initSign(schluessel);
            signatur.update(daten.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatur.sign());
        } catch (Exception fehler) {
            throw new Zugangsfehler("Das Zugangstoken liess sich nicht signieren.", fehler);
        }
    }

    private static String base64(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static PrivateKey ausPem(String pem) throws Exception {
        String rohwert = pem.replaceAll("-----BEGIN (RSA )?PRIVATE KEY-----", "")
                .replaceAll("-----END (RSA )?PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] bytes = Base64.getDecoder().decode(rohwert);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }
}
