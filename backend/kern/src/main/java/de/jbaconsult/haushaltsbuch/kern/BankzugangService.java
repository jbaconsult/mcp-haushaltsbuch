package de.jbaconsult.haushaltsbuch.kern;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Fachliche Operationen auf Bankzugängen.
 *
 * <p>Der einzige Rechenweg: REST und MCP rufen beide hierher. Zwei Implementierungen desselben
 * Vorgangs würden auseinanderlaufen, und der Unterschied fiele erst auf, wenn eine von beiden einen
 * Zugang falsch bewertet.
 */
@ApplicationScoped
public class BankzugangService {

    /**
     * Wie lange ein Autorisierungsvorgang offen bleiben darf.
     *
     * <p>Kurz, weil der Zustandswert in dieser Zeit gültig ist und ein untergeschobener Link genau
     * so lange wirkt. Fünfzehn Minuten reichen für eine Anmeldung beim Institut samt
     * Zwei-Faktor-Bestätigung und sind knapp genug, dass ein abgefangener Link selten noch trägt.
     */
    private static final Duration ZUSTAND_GUELTIGKEIT = Duration.ofMinutes(15);

    /** 32 Byte Zufall. Ein ratbarer Zustandswert wäre dasselbe wie gar keiner. */
    private static final int ZUSTAND_BYTES = 32;

    /**
     * Der Anbieter wird als {@link Instance} gehalten und nicht unmittelbar injiziert.
     *
     * <p>Grund: nicht jede Zusammenstellung dieses Systems bringt einen Adapter mit - die Tests der
     * Persistenzschicht etwa kommen ohne aus. Eine harte Abhängigkeit lässt dort das gesamte
     * CDI-Deployment scheitern, und der Fehler zeigt dann auf einen Test, der mit Bankzugängen nichts
     * zu tun hat. So fehlt stattdessen genau das, was fehlt - und es sagt das beim Aufruf.
     */
    private final Instance<BankanbieterPort> anbieterQuelle;

    private final BankanbieterPort anbieterDirekt;
    private final BankzugangPort speicher;
    private final Benutzerkontext benutzerkontext;
    private final Clock uhr;
    private final SecureRandom zufall = new SecureRandom();

    @Inject
    public BankzugangService(
            Instance<BankanbieterPort> anbieterQuelle, BankzugangPort speicher, Benutzerkontext benutzerkontext) {
        this.anbieterQuelle = anbieterQuelle;
        this.anbieterDirekt = null;
        this.speicher = speicher;
        this.benutzerkontext = benutzerkontext;
        this.uhr = Clock.systemUTC();
    }

    BankzugangService(BankanbieterPort anbieter, BankzugangPort speicher, Benutzerkontext benutzerkontext, Clock uhr) {
        this.anbieterQuelle = null;
        this.anbieterDirekt = anbieter;
        this.speicher = speicher;
        this.benutzerkontext = benutzerkontext;
        this.uhr = uhr;
    }

    /**
     * Der eingebundene Anbieter.
     *
     * <p>Fehlt er, ist das kein Absturz, sondern eine Auskunft: dieses System läuft ohne
     * Bankanbieter vollständig weiter, es kann dann nur keine Zugänge einrichten.
     */
    private BankanbieterPort anbieter() {
        if (anbieterDirekt != null) {
            return anbieterDirekt;
        }
        if (anbieterQuelle == null || anbieterQuelle.isUnsatisfied()) {
            throw new Zugangsfehler("Es ist kein Bankanbieter eingebunden. Der Adapter wird im Runner gebunden - "
                    + "siehe die Abhängigkeit auf das Modul bankzugang.");
        }
        return anbieterQuelle.get();
    }

    public List<Institut> institute(String land) {
        return anbieter().institute(land);
    }

    /**
     * Legt einen Bankzugang an und startet die Autorisierung.
     *
     * <p>Die gewünschte Gültigkeit wird an der Obergrenze des Instituts gekappt. Eine längere
     * Anfrage würde abgelehnt, und zwar mit einer Meldung, die auf den Zeitraum nicht hinweist.
     *
     * @return die Adresse, an die der Mensch zu schicken ist
     */
    public Autorisierungsstart autorisierungStarten(
            InstitutKennung institutskennung,
            Duration gewuenschteGueltigkeit,
            String rueckleitung,
            Optional<String> ipAdresse) {

        BenutzerId benutzer = angemeldeterBenutzer();

        Institut institut = anbieter().institute(institutskennung.land()).stream()
                .filter(kandidat -> kandidat.kennung().equals(institutskennung))
                .findFirst()
                .orElseThrow(() -> new Zugangsfehler("Unbekanntes Institut: " + institutskennung));

        Duration gueltigkeit = gewuenschteGueltigkeit.compareTo(institut.hoechsteGueltigkeit()) > 0
                ? institut.hoechsteGueltigkeit()
                : gewuenschteGueltigkeit;

        Instant jetzt = uhr.instant();
        Bankzugang zugang = new Bankzugang(
                BankzugangId.neu(),
                anbieter().anbieter(),
                institutskennung,
                institut.anzeigename(),
                Bankzugangstatus.AUTORISIERUNG_LAEUFT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                benutzer,
                jetzt);
        speicher.anlegen(zugang);

        String zustand = neuerZustand();
        speicher.zustandHinterlegen(zugang.id(), zustand, benutzer, jetzt.plus(ZUSTAND_GUELTIGKEIT));

        return anbieter()
                .autorisierungStarten(new Autorisierungswunsch(
                        institutskennung, jetzt.plus(gueltigkeit), rueckleitung, zustand, ipAdresse));
    }

    /**
     * Verarbeitet eine erfolgreiche Rückleitung.
     *
     * <p>Der Zustandswert wird zuerst eingelöst. Ist er unbekannt, verbraucht, abgelaufen oder
     * gehört er einem anderen Benutzer, endet der Vorgang hier - ohne dass ein Zugang eingerichtet
     * wird und ohne dass der Autorisierungscode überhaupt angefasst wird.
     *
     * <p>Scheitert der Eintausch beim Anbieter, geht der Zugang in einen sichtbaren Fehlzustand und
     * wird zurückgegeben. Bewusst keine Ausnahme: eine Ausnahme würde die Transaktion zurückrollen,
     * und der Fehlzustand wäre nicht gespeichert - die Oberfläche zeigte einen ewig laufenden
     * Vorgang.
     */
    public Bankzugang rueckleitungVerarbeiten(String zustand, String autorisierungscode) {
        BenutzerId benutzer = angemeldeterBenutzer();
        Instant jetzt = uhr.instant();

        BankzugangId id = speicher.zustandEinloesen(zustand, benutzer, jetzt)
                .orElseThrow(() -> new Zugangsfehler(
                        "Der Autorisierungsvorgang ist unbekannt, abgelaufen oder wurde bereits abgeschlossen."));

        Bankzugang zugang =
                speicher.findeZugang(id).orElseThrow(() -> new Zugangsfehler("Bankzugang nicht gefunden: " + id));

        Zugangseroeffnung eroeffnung;
        try {
            eroeffnung = anbieter().zugangEroeffnen(autorisierungscode);
        } catch (Zugangsfehler fehler) {
            Bankzugang gescheitert = zugang.fehlgeschlagen(fehler.getMessage());
            speicher.aktualisieren(gescheitert);
            return gescheitert;
        }

        Bankzugang autorisiert = zugang.autorisiert(eroeffnung.sitzung(), eroeffnung.gueltigBis());
        speicher.aktualisieren(autorisiert);

        uebernehmen(autorisiert, eroeffnung.konten(), eroeffnung.sitzung(), jetzt);
        return autorisiert;
    }

    /**
     * Verarbeitet eine abgebrochene oder abgelehnte Rückleitung.
     *
     * <p>Die Meldung des Anbieters wird übernommen und angezeigt, statt sie durch ein eigenes
     * „Fehler bei der Autorisierung" zu ersetzen.
     */
    public Bankzugang rueckleitungAbgebrochen(String zustand, String meldung) {
        BenutzerId benutzer = angemeldeterBenutzer();
        Instant jetzt = uhr.instant();

        BankzugangId id = speicher.zustandEinloesen(zustand, benutzer, jetzt)
                .orElseThrow(() -> new Zugangsfehler(
                        "Der Autorisierungsvorgang ist unbekannt, abgelaufen oder wurde bereits abgeschlossen."));

        Bankzugang zugang =
                speicher.findeZugang(id).orElseThrow(() -> new Zugangsfehler("Bankzugang nicht gefunden: " + id));

        Bankzugang gescheitert = zugang.fehlgeschlagen(meldung);
        speicher.aktualisieren(gescheitert);
        return gescheitert;
    }

    /**
     * Holt Konten und Salden eines bestehenden Zugangs neu.
     *
     * <p>Die flüchtigen Kontoreferenzen werden dabei frisch aus der Sitzung gelesen - gespeichert
     * sind sie nicht, und das ist der Grund, warum dieser Aufruf überhaupt beim Anbieter nachfragt
     * statt aus der Datenbank zu arbeiten.
     *
     * <p>Besteht die Sitzung nicht mehr, wechselt der Zugang in einen sichtbaren Zustand und die
     * <b>gespeicherten Konten und Salden bleiben unverändert</b>. Sie sind gemessene Vergangenheit
     * und werden nicht dadurch falsch, dass die Autorisierung endet.
     */
    public Bankzugang abrufen(BankzugangId id) {
        Instant jetzt = uhr.instant();

        Bankzugang zugang =
                speicher.findeZugang(id).orElseThrow(() -> new Zugangsfehler("Bankzugang nicht gefunden: " + id));

        Bankzugang geprueft = zugang.mitAblaufGeprueft(jetzt);
        if (geprueft != zugang) {
            speicher.aktualisieren(geprueft);
        }
        if (!geprueft.istNutzbar(jetzt)) {
            return geprueft;
        }

        Sitzungskennung sitzung = geprueft.sitzung()
                .orElseThrow(() -> new Zugangsfehler("Zugang gilt als autorisiert, hat aber keine Sitzung."));

        Zugangsbestand bestand;
        try {
            bestand = anbieter().bestand(sitzung);
        } catch (Zugangsfehler fehler) {
            if (!fehler.istSitzungUngueltig()) {
                throw fehler;
            }
            bestand = Zugangsbestand.nichtMehrAutorisiert();
        }

        if (!bestand.nochAutorisiert()) {
            Bankzugang beendet = geprueft.fehlgeschlagen(
                    "Die Sitzung besteht beim Anbieter nicht mehr. Die zuletzt abgerufenen Zahlen bleiben erhalten.");
            speicher.aktualisieren(beendet);
            return beendet;
        }

        uebernehmen(geprueft, bestand.konten(), sitzung, jetzt);
        return geprueft;
    }

    /**
     * Entfernt einen Bankzugang.
     *
     * <p>Deckt beide Fälle ab, die an der Oberfläche verschieden heißen und derselbe Vorgang sind:
     * den Abbruch eines laufenden Autorisierungsvorgangs und das Entfernen eines eingerichteten
     * Zugangs. Der Unterschied liegt allein darin, was es zu entfernen gibt - ein Vorgang, der nie
     * zu einer Autorisierung geführt hat, hat weder Sitzung noch Konten. Zwei Endpunkte für dieselbe
     * Bedeutung würden mit der Zeit auseinanderlaufen.
     *
     * <p>Reihenfolge und ihre Begründung:
     *
     * <ol>
     *   <li><b>Sitzung beim Anbieter beenden.</b> Zuerst, weil danach die Sitzungskennung verloren
     *       ist. Wer den eigenen Datensatz zuerst löscht, kann die Autorisierung beim Anbieter nie
     *       mehr widerrufen - sie läuft dann bis zu 180 Tage weiter, ohne dass irgendetwas in
     *       diesem System noch darauf zeigt.
     *   <li><b>Konten entfernen</b>, falls verlangt. Vor dem Zugang, weil der Fremdschlüssel auf
     *       {@code SET NULL} steht und die Konten sonst zurückblieben.
     *   <li><b>Zugang entfernen</b> samt Zustandswert.
     * </ol>
     *
     * <p>Scheitert Schritt 1, laufen die Schritte 2 und 3 trotzdem. Sonst wäre ein Zugang, dessen
     * Anbieter gerade nicht antwortet, überhaupt nicht loszuwerden - und der Mensch säße vor einer
     * Liste, die sich nicht aufräumen lässt. Das Ergebnis trägt die Meldung des Anbieters, damit die
     * Oberfläche sagen kann, dass der Widerruf dort noch aussteht.
     */
    public Zugangsentfernung entfernen(BankzugangId id, Kontenbehandlung kontenbehandlung) {
        Bankzugang zugang =
                speicher.findeZugang(id).orElseThrow(() -> new Zugangsfehler("Bankzugang nicht gefunden: " + id));

        boolean sitzungBeendet = false;
        Optional<String> anbietermeldung = Optional.empty();

        if (zugang.sitzung().isPresent()) {
            try {
                anbieter().sitzungBeenden(zugang.sitzung().get());
                sitzungBeendet = true;
            } catch (Zugangsfehler fehler) {
                anbietermeldung = Optional.of(fehler.getMessage());
            }
        }

        int entfernteKonten = kontenbehandlung == Kontenbehandlung.ENTFERNEN ? speicher.kontenEntfernen(id) : 0;
        int geloesteKonten = speicher.entfernen(id);

        return new Zugangsentfernung(sitzungBeendet, anbietermeldung, entfernteKonten, geloesteKonten);
    }

    public List<Bankzugang> zugaenge() {
        Instant jetzt = uhr.instant();
        return speicher.alleZugaenge().stream()
                .map(zugang -> zugang.mitAblaufGeprueft(jetzt))
                .toList();
    }

    public Optional<Bankzugang> zugang(BankzugangId id) {
        Instant jetzt = uhr.instant();
        return speicher.findeZugang(id).map(zugang -> zugang.mitAblaufGeprueft(jetzt));
    }

    public List<ExternesKonto> konten() {
        return speicher.alleKonten();
    }

    public Optional<ExternesKonto> konto(ExternesKontoId id) {
        return speicher.findeKonto(id);
    }

    public Optional<ExternesKonto> kontoNachKennung(Kontokennung kennung) {
        return speicher.findeKontoNachKennung(kennung);
    }

    public List<ExternerSaldo> letzteSalden(ExternesKontoId konto) {
        return speicher.letzteSalden(konto);
    }

    public List<ExternerSaldo> salden(ExternesKontoId konto) {
        return speicher.saldenDesKontos(konto);
    }

    /**
     * Die einmalige Messung der Feldabdeckung.
     *
     * <p>Nichts davon wird gespeichert. Buchungen gelangen ausschließlich über den Importdienst in
     * dieses System, weil sie dort gegen die Saldeninvarianten geprüft werden.
     */
    public Feldabdeckung feldabdeckungMessen(ExternesKontoId kontoId) {
        Instant jetzt = uhr.instant();

        ExternesKonto konto = speicher.findeKonto(kontoId)
                .orElseThrow(() -> new Zugangsfehler("Externes Konto nicht gefunden: " + kontoId));

        Bankzugang zugang = konto.bankzugang()
                .flatMap(speicher::findeZugang)
                .orElseThrow(() -> new Zugangsfehler("Zu diesem Konto besteht kein Bankzugang mehr. "
                        + "Ohne Autorisierung ist beim Anbieter nichts abzurufen."))
                .mitAblaufGeprueft(jetzt);

        if (!zugang.istNutzbar(jetzt)) {
            throw new Zugangsfehler("Der Bankzugang ist nicht nutzbar (Status " + zugang.status() + ").");
        }
        Sitzungskennung sitzung = zugang.sitzung()
                .orElseThrow(() -> new Zugangsfehler("Zugang gilt als autorisiert, hat aber keine Sitzung."));

        Kontoreferenz referenz = anbieter().bestand(sitzung).konten().stream()
                .filter(befund -> befund.kennung().equals(konto.kennung()))
                .findFirst()
                .map(Kontobefund::referenz)
                .orElseThrow(() -> new Zugangsfehler("Das Konto ist in der laufenden Sitzung nicht enthalten."));

        return anbieter().feldabdeckungMessen(sitzung, referenz);
    }

    /**
     * Übernimmt Konten und ihre Salden in den Bestand.
     *
     * <p>Erkennung über {@link Kontokennung}: eine zweite Autorisierung desselben Kontos
     * aktualisiert den vorhandenen Datensatz, statt einen zweiten anzulegen.
     */
    private void uebernehmen(Bankzugang zugang, List<Kontobefund> befunde, Sitzungskennung sitzung, Instant jetzt) {

        for (Kontobefund befund : befunde) {
            ExternesKontoId kontoId = speicher.kontoUebernehmen(new ExternesKonto(
                    ExternesKontoId.neu(),
                    Optional.of(zugang.id()),
                    befund.kennung(),
                    befund.iban(),
                    befund.waehrung(),
                    befund.kontoart(),
                    befund.produktname(),
                    befund.bezeichnung(),
                    Optional.empty()));

            for (ExternerSaldo saldo : anbieter().salden(sitzung, befund.referenz())) {
                speicher.saldoAblegen(kontoId, saldo);
            }
        }
    }

    private BenutzerId angemeldeterBenutzer() {
        return benutzerkontext
                .benutzerId()
                .orElseThrow(() -> new Zugangsfehler(
                        "Kein angemeldeter Benutzer. Bankzugänge werden immer für einen Menschen eingerichtet."));
    }

    private String neuerZustand() {
        byte[] rohwert = new byte[ZUSTAND_BYTES];
        zufall.nextBytes(rohwert);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rohwert);
    }
}
