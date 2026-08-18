package de.jbaconsult.haushaltsbuch.app;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.test.Mock;

import de.jbaconsult.haushaltsbuch.kern.Autorisierungsstart;
import de.jbaconsult.haushaltsbuch.kern.Autorisierungswunsch;
import de.jbaconsult.haushaltsbuch.kern.BankanbieterPort;
import de.jbaconsult.haushaltsbuch.kern.Betrag;
import de.jbaconsult.haushaltsbuch.kern.ExternerSaldo;
import de.jbaconsult.haushaltsbuch.kern.Feldabdeckung;
import de.jbaconsult.haushaltsbuch.kern.Iban;
import de.jbaconsult.haushaltsbuch.kern.Institut;
import de.jbaconsult.haushaltsbuch.kern.InstitutKennung;
import de.jbaconsult.haushaltsbuch.kern.Kontobefund;
import de.jbaconsult.haushaltsbuch.kern.Kontokennung;
import de.jbaconsult.haushaltsbuch.kern.Kontoreferenz;
import de.jbaconsult.haushaltsbuch.kern.Saldenart;
import de.jbaconsult.haushaltsbuch.kern.Sitzungskennung;
import de.jbaconsult.haushaltsbuch.kern.Zugangsbestand;
import de.jbaconsult.haushaltsbuch.kern.Zugangseroeffnung;
import de.jbaconsult.haushaltsbuch.kern.Zugangsfehler;

/**
 * Ein Anbieter, der ohne Netz auskommt.
 *
 * <p>Ersetzt den echten Adapter im Test. Ein Test, der gegen die Sandbox des Anbieters läuft,
 * prüft dessen Verfügbarkeit mit - und schlägt fehl, wenn dort gerade gewartet wird. Was am
 * Adapter selbst zu prüfen ist, steht in {@code EnableBankingAdapterTest} und läuft gegen einen
 * Attrappen-Server.
 */
@Mock
@ApplicationScoped
public class AnbieterAttrappe implements BankanbieterPort {

    /*
     * Zugriff ausschliesslich ueber Methoden, nicht ueber Felder.
     *
     * Ein @ApplicationScoped-Bean wird als Proxy injiziert. Ein Feldzugriff darauf liest das Feld
     * des Proxys - das ist immer null -, waehrend der Bean daneben den richtigen Wert traegt. Der
     * Test schlaegt dann mit "expected not blank but was null" fehl, und die Ursache steht
     * nirgends. Methodenaufrufe gehen durch den Proxy hindurch.
     */
    private String letzterZustand;
    private String fehlerBeimEroeffnen;
    private final List<String> kontokennungen = new ArrayList<>(List.of("stabil-eins"));

    /** Der zuletzt erzeugte Zustandswert - damit ein Test die Rückleitung nachstellen kann. */
    public String letzterZustand() {
        return letzterZustand;
    }

    /** Lässt den Eintausch des Autorisierungscodes mit dieser Meldung scheitern. */
    public void fehlerBeimEroeffnen(String meldung) {
        this.fehlerBeimEroeffnen = meldung;
    }

    public void zuruecksetzen() {
        this.letzterZustand = null;
        this.fehlerBeimEroeffnen = null;
    }

    @Override
    public String anbieter() {
        return "Attrappe";
    }

    @Override
    public List<Institut> institute(String land) {
        return List.of(
                new Institut(new InstitutKennung("Testbank", land), "Testbank", Duration.ofDays(180), List.of()));
    }

    @Override
    public Autorisierungsstart autorisierungStarten(Autorisierungswunsch wunsch) {
        letzterZustand = wunsch.zustand();
        return new Autorisierungsstart("https://institut.invalid/anmelden?state=" + wunsch.zustand());
    }

    @Override
    public Zugangseroeffnung zugangEroeffnen(String autorisierungscode) {
        if (fehlerBeimEroeffnen != null) {
            throw new Zugangsfehler(fehlerBeimEroeffnen);
        }
        return new Zugangseroeffnung(
                new Sitzungskennung("sitzung-attrappe"), Instant.now().plus(Duration.ofDays(180)), konten());
    }

    @Override
    public Zugangsbestand bestand(Sitzungskennung sitzung) {
        return new Zugangsbestand(true, konten());
    }

    @Override
    public List<ExternerSaldo> salden(Sitzungskennung sitzung, Kontoreferenz konto) {
        return List.of(new ExternerSaldo(
                Saldenart.GEBUCHT, "CLBD", Betrag.von("1234.56"), "EUR", Optional.empty(), Instant.now()));
    }

    @Override
    public Feldabdeckung feldabdeckungMessen(Sitzungskennung sitzung, Kontoreferenz konto) {
        return new Feldabdeckung(
                1,
                List.of(new Feldabdeckung.Feldbefund("entry_reference", "Feld der Buchung", 1, 1)),
                List.of("Attrappe"));
    }

    private List<Kontobefund> konten() {
        return kontokennungen.stream()
                .map(kennung -> new Kontobefund(
                        new Kontokennung(kennung),
                        new Kontoreferenz("fluechtig-" + kennung),
                        Iban.lesen("DE02120300000000202051"),
                        "EUR",
                        Optional.of("CACC"),
                        Optional.of("Testprodukt"),
                        "Konto " + kennung))
                .toList();
    }
}
