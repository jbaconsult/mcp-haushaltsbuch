package de.jbaconsult.haushaltsbuch.kern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prüft die Vorgangslogik eines Bankzugangs ohne Datenbank und ohne Netz.
 *
 * <p>Zwei Zusagen stehen hier im Mittelpunkt, beide Abnahmekriterien des Auftrags:
 *
 * <ul>
 *   <li>Ein gescheiterter Vorgang endet in einem <b>gespeicherten</b> Fehlzustand mit der Meldung
 *       des Anbieters - nicht in einer Ausnahme. Eine Ausnahme würde die Transaktion zurückrollen,
 *       und die Oberfläche zeigte einen ewig laufenden Vorgang.
 *   <li>Ein abgelaufener Zugang ist als solcher sichtbar, und die zuvor abgerufenen Zahlen bleiben
 *       erhalten.
 * </ul>
 */
class BankzugangServiceTest {

    private static final Instant JETZT = Instant.parse("2026-08-18T10:00:00Z");
    private static final BenutzerId ICH = BenutzerId.von("00000000-0000-0000-0000-000000000001");
    private static final BenutzerId JEMAND_ANDERS = BenutzerId.von("00000000-0000-0000-0000-000000000002");

    private final SpeicherAttrappe speicher = new SpeicherAttrappe();
    private final Benutzerkontext benutzerkontext = new Benutzerkontext();

    @Test
    @DisplayName("ein abgelehnter Autorisierungscode führt zu einem sichtbaren Fehlzustand")
    void abgelehnterCodeFuehrtZuFehlzustand() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        anbieter.fehlerBeimEroeffnen = "Die Zustimmung wurde beim Institut abgelehnt.";
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        dienst.autorisierungStarten(
                new InstitutKennung("Testbank", "DE"),
                Duration.ofDays(180),
                "https://beispiel.invalid/rueck",
                Optional.empty());

        Bankzugang ergebnis = dienst.rueckleitungVerarbeiten(anbieter.letzterZustand, "code-egal");

        assertThat(ergebnis.status()).isEqualTo(Bankzugangstatus.FEHLGESCHLAGEN);
        assertThat(ergebnis.fehlermeldung()).contains("Die Zustimmung wurde beim Institut abgelehnt.");

        // Entscheidend: der Zustand ist gespeichert, nicht nur zurückgegeben.
        assertThat(speicher.findeZugang(ergebnis.id()).orElseThrow().status())
                .isEqualTo(Bankzugangstatus.FEHLGESCHLAGEN);
    }

    @Test
    @DisplayName("ein Abbruch beim Institut übernimmt dessen Meldung")
    void abbruchUebernimmtMeldung() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        dienst.autorisierungStarten(
                new InstitutKennung("Testbank", "DE"),
                Duration.ofDays(180),
                "https://beispiel.invalid/rueck",
                Optional.empty());

        Bankzugang ergebnis =
                dienst.rueckleitungAbgebrochen(anbieter.letzterZustand, "access_denied: vom Kunden abgebrochen");

        assertThat(ergebnis.status()).isEqualTo(Bankzugangstatus.FEHLGESCHLAGEN);
        assertThat(ergebnis.fehlermeldung())
                .hasValueSatisfying(meldung -> assertThat(meldung).contains("vom Kunden abgebrochen"));
    }

    @Test
    @DisplayName("ein Zustandswert eines anderen Benutzers wird abgelehnt")
    void fremderZustandWirdAbgelehnt() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        dienst.autorisierungStarten(
                new InstitutKennung("Testbank", "DE"),
                Duration.ofDays(180),
                "https://beispiel.invalid/rueck",
                Optional.empty());

        benutzerkontext.setzen(JEMAND_ANDERS);

        assertThatThrownBy(() -> dienst.rueckleitungVerarbeiten(anbieter.letzterZustand, "code-egal"))
                .isInstanceOf(Zugangsfehler.class);

        assertThat(anbieter.eroeffnungenVersucht)
                .as("der Autorisierungscode darf gar nicht erst eingetauscht werden")
                .isZero();
    }

    @Test
    @DisplayName("ein abgelaufener Zugang wird als abgelaufen geführt und behält seine Daten")
    void abgelaufenerZugangBleibtSichtbar() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        Bankzugang zugang = new Bankzugang(
                BankzugangId.neu(),
                "Testanbieter",
                new InstitutKennung("Testbank", "DE"),
                "Testbank",
                Bankzugangstatus.AUTORISIERT,
                Optional.of(JETZT.minusSeconds(60)),
                Optional.of(new Sitzungskennung("sitzung")),
                Optional.empty(),
                ICH,
                JETZT.minusSeconds(3600));
        speicher.anlegen(zugang);

        ExternesKontoId kontoId = speicher.kontoUebernehmen(new ExternesKonto(
                ExternesKontoId.neu(),
                Optional.of(zugang.id()),
                new Kontokennung("hash"),
                Optional.empty(),
                "EUR",
                Optional.empty(),
                Optional.empty(),
                "Konto",
                Optional.empty()));
        speicher.saldoAblegen(
                kontoId,
                new ExternerSaldo(
                        Saldenart.GEBUCHT,
                        "CLBD",
                        Betrag.von("500.00"),
                        "EUR",
                        Optional.empty(),
                        JETZT.minusSeconds(3600)));

        Bankzugang nachAbruf = dienst.abrufen(zugang.id());

        assertThat(nachAbruf.status()).isEqualTo(Bankzugangstatus.ABGELAUFEN);
        assertThat(anbieter.bestandsabfragen)
                .as("gegen einen abgelaufenen Zugang wird gar nicht erst gefragt")
                .isZero();
        assertThat(speicher.saldenDesKontos(kontoId))
                .as("die zuletzt bekannten Zahlen bleiben erhalten")
                .hasSize(1);
    }

    @Test
    @DisplayName("eine erloschene Sitzung entwertet den Zugang, nicht die Daten")
    void erlosceneSitzungLaesstDatenStehen() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        anbieter.sitzungBesteht = false;
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        Bankzugang zugang = new Bankzugang(
                BankzugangId.neu(),
                "Testanbieter",
                new InstitutKennung("Testbank", "DE"),
                "Testbank",
                Bankzugangstatus.AUTORISIERT,
                Optional.of(JETZT.plusSeconds(3600)),
                Optional.of(new Sitzungskennung("sitzung")),
                Optional.empty(),
                ICH,
                JETZT.minusSeconds(3600));
        speicher.anlegen(zugang);

        ExternesKontoId kontoId = speicher.kontoUebernehmen(new ExternesKonto(
                ExternesKontoId.neu(),
                Optional.of(zugang.id()),
                new Kontokennung("hash"),
                Optional.empty(),
                "EUR",
                Optional.empty(),
                Optional.empty(),
                "Konto",
                Optional.empty()));
        speicher.saldoAblegen(
                kontoId,
                new ExternerSaldo(
                        Saldenart.GEBUCHT,
                        "CLBD",
                        Betrag.von("500.00"),
                        "EUR",
                        Optional.empty(),
                        JETZT.minusSeconds(3600)));

        Bankzugang nachAbruf = dienst.abrufen(zugang.id());

        assertThat(nachAbruf.status()).isEqualTo(Bankzugangstatus.FEHLGESCHLAGEN);
        assertThat(nachAbruf.fehlermeldung()).isPresent();
        assertThat(speicher.saldenDesKontos(kontoId)).hasSize(1);
        assertThat(speicher.alleKonten()).hasSize(1);
    }

    @Test
    @DisplayName("die gewünschte Gültigkeit wird an der Obergrenze des Instituts gekappt")
    void gueltigkeitWirdGekappt() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        dienst.autorisierungStarten(
                new InstitutKennung("Testbank", "DE"),
                Duration.ofDays(365),
                "https://beispiel.invalid/rueck",
                Optional.empty());

        // Das Institut erlaubt 180 Tage. Eine längere Anfrage würde abgelehnt, und zwar mit einer
        // Meldung, die auf den Zeitraum nicht hinweist.
        assertThat(anbieter.letzterWunsch.gueltigBis()).isEqualTo(JETZT.plus(Duration.ofDays(180)));
    }

    @Test
    @DisplayName("ein unbekanntes Institut wird abgelehnt, bevor etwas angelegt wird")
    void unbekanntesInstitutWirdAbgelehnt() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        assertThatThrownBy(() -> dienst.autorisierungStarten(
                        new InstitutKennung("Gibtsnicht", "DE"),
                        Duration.ofDays(180),
                        "https://beispiel.invalid/rueck",
                        Optional.empty()))
                .isInstanceOf(Zugangsfehler.class);

        assertThat(dienst.zugaenge()).isEmpty();
    }

    @Test
    @DisplayName("ohne angemeldeten Benutzer wird kein Zugang eingerichtet")
    void ohneBenutzerKeinZugang() {
        BankzugangService dienst = dienst(new AnbieterAttrappe());

        // Kein setzen() - Bankzugänge werden immer für einen Menschen eingerichtet.
        assertThatThrownBy(() -> dienst.autorisierungStarten(
                        new InstitutKennung("Testbank", "DE"),
                        Duration.ofDays(180),
                        "https://beispiel.invalid/rueck",
                        Optional.empty()))
                .isInstanceOf(Zugangsfehler.class)
                .hasMessageContaining("angemeldeter Benutzer");
    }

    @Test
    @DisplayName("ein Abruf übernimmt Konten und Salden aus der laufenden Sitzung")
    void abrufUebernimmtKonten() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        anbieter.meldetKonto = true;
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        Bankzugang zugang = autorisierterZugang();
        speicher.anlegen(zugang);

        dienst.abrufen(zugang.id());

        assertThat(dienst.konten()).hasSize(1);
        ExternesKontoId kontoId = dienst.konten().get(0).id();
        assertThat(dienst.letzteSalden(kontoId)).hasSize(1);

        // Ein zweiter Abruf darf kein zweites Konto erzeugen - erkannt an der stabilen Kennung.
        dienst.abrufen(zugang.id());
        assertThat(dienst.konten()).hasSize(1);
    }

    @Test
    @DisplayName("die Feldmessung braucht einen nutzbaren Zugang und ein Konto in der Sitzung")
    void feldmessung() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        anbieter.meldetKonto = true;
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        Bankzugang zugang = autorisierterZugang();
        speicher.anlegen(zugang);
        dienst.abrufen(zugang.id());

        ExternesKontoId kontoId = dienst.konten().get(0).id();
        assertThat(dienst.feldabdeckungMessen(kontoId).anzahlBuchungen()).isEqualTo(3);

        // Ein unbekanntes Konto endet mit einer Auskunft, nicht mit einer leeren Messung.
        assertThatThrownBy(() -> dienst.feldabdeckungMessen(ExternesKontoId.neu()))
                .isInstanceOf(Zugangsfehler.class);
    }

    @Test
    @DisplayName("Institute werden durchgereicht")
    void instituteDurchgereicht() {
        assertThat(dienst(new AnbieterAttrappe()).institute("DE")).hasSize(1);
    }

    private Bankzugang autorisierterZugang() {
        return new Bankzugang(
                BankzugangId.neu(),
                "Testanbieter",
                new InstitutKennung("Testbank", "DE"),
                "Testbank",
                Bankzugangstatus.AUTORISIERT,
                Optional.of(JETZT.plusSeconds(3600)),
                Optional.of(new Sitzungskennung("sitzung")),
                Optional.empty(),
                ICH,
                JETZT.minusSeconds(3600));
    }

    // ------------------------------------------------------- Zugang entfernen

    @Test
    @DisplayName("ein abgebrochener Autorisierungsvorgang lässt sich restlos entfernen")
    void laufenderVorgangLaesstSichEntfernen() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        dienst.autorisierungStarten(
                new InstitutKennung("Testbank", "DE"),
                Duration.ofDays(180),
                "https://beispiel.invalid/rueck",
                Optional.empty());

        BankzugangId id = speicher.alleZugaenge().get(0).id();
        assertThat(speicher.alleZugaenge()).hasSize(1);

        Zugangsentfernung ergebnis = dienst.entfernen(id, Kontenbehandlung.BEHALTEN);

        assertThat(speicher.alleZugaenge()).isEmpty();
        assertThat(anbieter.sitzungenBeendet)
                .as("ein Vorgang ohne Sitzung hat beim Anbieter nichts zu widerrufen")
                .isZero();
        assertThat(ergebnis.brauchtHinweis()).isFalse();

        // Und die Rückleitung, die vielleicht noch unterwegs ist, läuft ins Leere statt einen
        // gelöschten Zugang wiederzubeleben.
        assertThatThrownBy(() -> dienst.rueckleitungVerarbeiten(anbieter.letzterZustand, "code-egal"))
                .isInstanceOf(Zugangsfehler.class);
    }

    @Test
    @DisplayName("ein entfernter Zugang widerruft die Autorisierung beim Anbieter")
    void entfernenWiderruftSitzung() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        Bankzugang zugang = autorisierterZugang(dienst, anbieter);

        dienst.entfernen(zugang.id(), Kontenbehandlung.BEHALTEN);

        assertThat(anbieter.sitzungenBeendet).isEqualTo(1);
        assertThat(anbieter.zuletztBeendeteSitzung).isEqualTo(new Sitzungskennung("sitzung"));
    }

    @Test
    @DisplayName("bei BEHALTEN bleiben Konten und Salden als Bestand stehen")
    void behaltenLaesstDieZahlenStehen() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        anbieter.meldetKonto = true;
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        Bankzugang zugang = autorisierterZugang(dienst, anbieter);
        ExternesKonto vorher = speicher.alleKonten().get(0);
        assertThat(speicher.saldenDesKontos(vorher.id())).isNotEmpty();

        Zugangsentfernung ergebnis = dienst.entfernen(zugang.id(), Kontenbehandlung.BEHALTEN);

        assertThat(ergebnis.entfernteKonten()).isZero();
        assertThat(ergebnis.geloesteKonten()).isEqualTo(1);

        ExternesKonto nachher = speicher.alleKonten().get(0);
        assertThat(nachher.bankzugang())
                .as("der Zugangsbezug ist gelöst, das Konto selbst bleibt")
                .isEmpty();
        assertThat(speicher.saldenDesKontos(nachher.id()))
                .as("gemessene Vergangenheit wird nicht dadurch falsch, dass die Autorisierung endet")
                .isNotEmpty();
    }

    @Test
    @DisplayName("bei ENTFERNEN verschwinden Konten und Salden mit dem Zugang")
    void entfernenNimmtDieKontenMit() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        anbieter.meldetKonto = true;
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        Bankzugang zugang = autorisierterZugang(dienst, anbieter);
        ExternesKontoId kontoId = speicher.alleKonten().get(0).id();

        Zugangsentfernung ergebnis = dienst.entfernen(zugang.id(), Kontenbehandlung.ENTFERNEN);

        assertThat(ergebnis.entfernteKonten()).isEqualTo(1);
        assertThat(speicher.alleKonten()).isEmpty();
        assertThat(speicher.saldenDesKontos(kontoId)).isEmpty();
    }

    @Test
    @DisplayName("ein Anbieter, der den Widerruf verweigert, verhindert das Entfernen nicht")
    void fehlschlagBeimWiderrufBlockiertNicht() {
        AnbieterAttrappe anbieter = new AnbieterAttrappe();
        anbieter.fehlerBeimBeenden = "Der Anbieter ist nicht erreichbar.";
        BankzugangService dienst = dienst(anbieter);
        benutzerkontext.setzen(ICH);

        Bankzugang zugang = autorisierterZugang(dienst, anbieter);

        Zugangsentfernung ergebnis = dienst.entfernen(zugang.id(), Kontenbehandlung.BEHALTEN);

        assertThat(speicher.findeZugang(zugang.id()))
                .as("sonst wäre ein Zugang, dessen Anbieter schweigt, überhaupt nicht loszuwerden")
                .isEmpty();
        assertThat(ergebnis.sitzungBeendet()).isFalse();
        assertThat(ergebnis.anbietermeldung())
                .as("der ausstehende Widerruf gehört gesagt, nicht geschluckt")
                .hasValueSatisfying(meldung -> assertThat(meldung).contains("nicht erreichbar"));
    }

    @Test
    @DisplayName("ein unbekannter Zugang lässt sich nicht entfernen")
    void unbekannterZugangWirdAbgelehnt() {
        BankzugangService dienst = dienst(new AnbieterAttrappe());
        benutzerkontext.setzen(ICH);
        BankzugangId unbekannt = BankzugangId.neu();

        assertThatThrownBy(() -> dienst.entfernen(unbekannt, Kontenbehandlung.BEHALTEN))
                .isInstanceOf(Zugangsfehler.class);
    }

    /** Richtet einen vollständig autorisierten Zugang über den regulären Weg ein. */
    private Bankzugang autorisierterZugang(BankzugangService dienst, AnbieterAttrappe anbieter) {
        dienst.autorisierungStarten(
                new InstitutKennung("Testbank", "DE"),
                Duration.ofDays(180),
                "https://beispiel.invalid/rueck",
                Optional.empty());
        return dienst.rueckleitungVerarbeiten(anbieter.letzterZustand, "code-gut");
    }

    private BankzugangService dienst(AnbieterAttrappe anbieter) {
        return new BankzugangService(anbieter, speicher, benutzerkontext, Clock.fixed(JETZT, ZoneOffset.UTC));
    }

    // ------------------------------------------------------------- Attrappen

    /** Ein Anbieter, der sich so verhält, wie der jeweilige Test es braucht. */
    private static final class AnbieterAttrappe implements BankanbieterPort {

        String letzterZustand;
        Autorisierungswunsch letzterWunsch;
        String fehlerBeimEroeffnen;
        boolean sitzungBesteht = true;
        boolean meldetKonto;
        int eroeffnungenVersucht;
        int bestandsabfragen;
        int sitzungenBeendet;
        String fehlerBeimBeenden;
        Sitzungskennung zuletztBeendeteSitzung;

        @Override
        public String anbieter() {
            return "Testanbieter";
        }

        @Override
        public List<Institut> institute(String land) {
            return List.of(
                    new Institut(new InstitutKennung("Testbank", land), "Testbank", Duration.ofDays(180), List.of()));
        }

        @Override
        public Autorisierungsstart autorisierungStarten(Autorisierungswunsch wunsch) {
            letzterZustand = wunsch.zustand();
            letzterWunsch = wunsch;
            return new Autorisierungsstart("https://institut.invalid/anmelden");
        }

        @Override
        public Zugangseroeffnung zugangEroeffnen(String autorisierungscode) {
            eroeffnungenVersucht++;
            if (fehlerBeimEroeffnen != null) {
                throw new Zugangsfehler(fehlerBeimEroeffnen);
            }
            // Wie beim echten Anbieter: die Sitzungseröffnung liefert die Konten bereits mit.
            return new Zugangseroeffnung(
                    new Sitzungskennung("sitzung"),
                    JETZT.plus(Duration.ofDays(180)),
                    meldetKonto ? List.of(befund()) : List.of());
        }

        @Override
        public void sitzungBeenden(Sitzungskennung sitzung) {
            sitzungenBeendet++;
            zuletztBeendeteSitzung = sitzung;
            if (fehlerBeimBeenden != null) {
                throw new Zugangsfehler(fehlerBeimBeenden);
            }
        }

        @Override
        public Zugangsbestand bestand(Sitzungskennung sitzung) {
            bestandsabfragen++;
            if (!sitzungBesteht) {
                return Zugangsbestand.nichtMehrAutorisiert();
            }
            return new Zugangsbestand(true, meldetKonto ? List.of(befund()) : List.of());
        }

        private Kontobefund befund() {
            return new Kontobefund(
                    new Kontokennung("stabil-eins"),
                    new Kontoreferenz("fluechtig-eins"),
                    Optional.empty(),
                    "EUR",
                    Optional.empty(),
                    Optional.empty(),
                    "Testkonto");
        }

        @Override
        public List<ExternerSaldo> salden(Sitzungskennung sitzung, Kontoreferenz konto) {
            return List.of(
                    new ExternerSaldo(Saldenart.GEBUCHT, "CLBD", Betrag.von("42.00"), "EUR", Optional.empty(), JETZT));
        }

        @Override
        public Feldabdeckung feldabdeckungMessen(Sitzungskennung sitzung, Kontoreferenz konto) {
            return new Feldabdeckung(3, List.of(), List.of());
        }
    }

    /** Ein Speicher im Arbeitsspeicher. Bildet nur ab, was die Vorgangslogik braucht. */
    private static final class SpeicherAttrappe implements BankzugangPort {

        private final Map<BankzugangId, Bankzugang> zugaenge = new LinkedHashMap<>();
        private final Map<String, Zustandseintrag> zustaende = new LinkedHashMap<>();
        private final Map<Kontokennung, ExternesKonto> konten = new LinkedHashMap<>();
        private final Map<ExternesKontoId, List<ExternerSaldo>> salden = new LinkedHashMap<>();

        private record Zustandseintrag(
                BankzugangId zugang, BenutzerId benutzer, Instant gueltigBis, boolean verbraucht) {}

        @Override
        public void anlegen(Bankzugang zugang) {
            zugaenge.put(zugang.id(), zugang);
        }

        @Override
        public void aktualisieren(Bankzugang zugang) {
            zugaenge.put(zugang.id(), zugang);
        }

        @Override
        public Optional<Bankzugang> findeZugang(BankzugangId id) {
            return Optional.ofNullable(zugaenge.get(id));
        }

        @Override
        public List<Bankzugang> alleZugaenge() {
            return List.copyOf(zugaenge.values());
        }

        @Override
        public void zustandHinterlegen(BankzugangId zugang, String zustand, BenutzerId benutzer, Instant gueltigBis) {
            zustaende.put(zustand, new Zustandseintrag(zugang, benutzer, gueltigBis, false));
        }

        @Override
        public Optional<BankzugangId> zustandEinloesen(String zustand, BenutzerId benutzer, Instant jetzt) {
            Zustandseintrag eintrag = zustaende.get(zustand);
            if (eintrag == null
                    || eintrag.verbraucht()
                    || !eintrag.benutzer().equals(benutzer)
                    || !eintrag.gueltigBis().isAfter(jetzt)) {
                return Optional.empty();
            }
            zustaende.put(
                    zustand, new Zustandseintrag(eintrag.zugang(), eintrag.benutzer(), eintrag.gueltigBis(), true));
            return Optional.of(eintrag.zugang());
        }

        @Override
        public ExternesKontoId kontoUebernehmen(ExternesKonto konto) {
            ExternesKonto vorhanden = konten.get(konto.kennung());
            ExternesKontoId id = vorhanden == null ? konto.id() : vorhanden.id();
            konten.put(
                    konto.kennung(),
                    new ExternesKonto(
                            id,
                            konto.bankzugang(),
                            konto.kennung(),
                            konto.iban(),
                            konto.waehrung(),
                            konto.kontoart(),
                            konto.produktname(),
                            konto.bezeichnung(),
                            konto.zugeordnetesKonto()));
            return id;
        }

        @Override
        public int entfernen(BankzugangId id) {
            zugaenge.remove(id);
            zustaende.values().removeIf(eintrag -> eintrag.zugang().equals(id));

            List<Kontokennung> betroffen = konten.entrySet().stream()
                    .filter(eintrag ->
                            eintrag.getValue().bankzugang().filter(id::equals).isPresent())
                    .map(Map.Entry::getKey)
                    .toList();

            for (Kontokennung kennung : betroffen) {
                ExternesKonto konto = konten.get(kennung);
                konten.put(
                        kennung,
                        new ExternesKonto(
                                konto.id(),
                                Optional.empty(),
                                konto.kennung(),
                                konto.iban(),
                                konto.waehrung(),
                                konto.kontoart(),
                                konto.produktname(),
                                konto.bezeichnung(),
                                konto.zugeordnetesKonto()));
            }
            return betroffen.size();
        }

        @Override
        public int kontenEntfernen(BankzugangId id) {
            List<ExternesKonto> betroffen = konten.values().stream()
                    .filter(konto -> konto.bankzugang().filter(id::equals).isPresent())
                    .toList();

            for (ExternesKonto konto : betroffen) {
                konten.remove(konto.kennung());
                salden.remove(konto.id());
            }
            return betroffen.size();
        }

        @Override
        public List<ExternesKonto> kontenDesZugangs(BankzugangId zugang) {
            return konten.values().stream()
                    .filter(konto -> konto.bankzugang().filter(zugang::equals).isPresent())
                    .toList();
        }

        @Override
        public List<ExternesKonto> alleKonten() {
            return List.copyOf(konten.values());
        }

        @Override
        public Optional<ExternesKonto> findeKonto(ExternesKontoId id) {
            return konten.values().stream()
                    .filter(konto -> konto.id().equals(id))
                    .findFirst();
        }

        @Override
        public Optional<ExternesKonto> findeKontoNachKennung(Kontokennung kennung) {
            return Optional.ofNullable(konten.get(kennung));
        }

        @Override
        public void saldoAblegen(ExternesKontoId konto, ExternerSaldo saldo) {
            salden.computeIfAbsent(konto, schluessel -> new ArrayList<>()).add(saldo);
        }

        @Override
        public List<ExternerSaldo> saldenDesKontos(ExternesKontoId konto) {
            return List.copyOf(salden.getOrDefault(konto, List.of()));
        }

        @Override
        public List<ExternerSaldo> letzteSalden(ExternesKontoId konto) {
            return saldenDesKontos(konto);
        }
    }
}
