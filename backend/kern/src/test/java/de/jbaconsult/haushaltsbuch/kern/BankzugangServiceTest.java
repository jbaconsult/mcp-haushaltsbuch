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
                zugang.id(),
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
                zugang.id(),
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

    private BankzugangService dienst(AnbieterAttrappe anbieter) {
        return new BankzugangService(anbieter, speicher, benutzerkontext, Clock.fixed(JETZT, ZoneOffset.UTC));
    }

    // ------------------------------------------------------------- Attrappen

    /** Ein Anbieter, der sich so verhält, wie der jeweilige Test es braucht. */
    private static final class AnbieterAttrappe implements BankanbieterPort {

        String letzterZustand;
        String fehlerBeimEroeffnen;
        boolean sitzungBesteht = true;
        int eroeffnungenVersucht;
        int bestandsabfragen;

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
            return new Autorisierungsstart("https://institut.invalid/anmelden");
        }

        @Override
        public Zugangseroeffnung zugangEroeffnen(String autorisierungscode) {
            eroeffnungenVersucht++;
            if (fehlerBeimEroeffnen != null) {
                throw new Zugangsfehler(fehlerBeimEroeffnen);
            }
            return new Zugangseroeffnung(new Sitzungskennung("sitzung"), JETZT.plus(Duration.ofDays(180)), List.of());
        }

        @Override
        public Zugangsbestand bestand(Sitzungskennung sitzung) {
            bestandsabfragen++;
            return sitzungBesteht ? new Zugangsbestand(true, List.of()) : Zugangsbestand.nichtMehrAutorisiert();
        }

        @Override
        public List<ExternerSaldo> salden(Sitzungskennung sitzung, Kontoreferenz konto) {
            return List.of();
        }

        @Override
        public Feldabdeckung feldabdeckungMessen(Sitzungskennung sitzung, Kontoreferenz konto) {
            return new Feldabdeckung(0, List.of(), List.of());
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
        public List<ExternesKonto> kontenDesZugangs(BankzugangId zugang) {
            return konten.values().stream()
                    .filter(konto -> konto.bankzugang().equals(zugang))
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
