package de.jbaconsult.haushaltsbuch.mcp;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

import de.jbaconsult.haushaltsbuch.kern.Bankzugang;
import de.jbaconsult.haushaltsbuch.kern.BankzugangService;
import de.jbaconsult.haushaltsbuch.kern.ExternerSaldo;
import de.jbaconsult.haushaltsbuch.kern.ExternesKonto;
import de.jbaconsult.haushaltsbuch.kern.ExternesKontoId;
import de.jbaconsult.haushaltsbuch.kern.Kontokennung;

/**
 * MCP-Werkzeuge rund um die von der Bank gemeldeten Konten.
 *
 * <p><b>Beide Werkzeuge sind Klasse 1 (Abfragen) nach ADR-0007.</b> Sie verändern nichts und
 * antworten ausschließlich aus dem gespeicherten Bestand. Ein Werkzeug, das beim Lesen ungefragt
 * eine Bankverbindung öffnet, wäre weder schnell noch vorhersagbar - und es wäre je nach Parameter
 * etwas anderes, womit die Zuordnung zur Klasse hinfällig wäre. Der Abruf ist ein eigener Vorgang
 * und läuft über das Dashboard.
 *
 * <p><b>Zur Benennung.</b> Der Auftrag nennt die Werkzeuge {@code konten_auflisten} und
 * {@code konto_details}. Der erste Name ist in {@link KontenTools} bereits vergeben - dort für die
 * fachlichen Konten dieses Systems aus V1, die etwas anderes sind als die hier gemeldeten Konten
 * eines Instituts. Zwei Werkzeuge desselben Namens kann es nicht geben, und die Namen müssen den
 * Unterschied tragen, weil ein Modell sie danach auswählt. Deshalb {@code bankkonten_*}.
 */
public class BankkontenTools {

    private final BankzugangService bankzugangService;
    private final McpBenutzerkontext benutzerkontext;

    @Inject
    public BankkontenTools(BankzugangService bankzugangService, McpBenutzerkontext benutzerkontext) {
        this.bankzugangService = bankzugangService;
        this.benutzerkontext = benutzerkontext;
    }

    @Tool(name = "bankkonten_auflisten", description = """
                    Listet die Konten auf, die eine Bank an dieses System gemeldet hat, jeweils mit
                    dem zuletzt bekannten Saldo und dem Zeitpunkt seines Abrufs.

                    Benutze dieses Tool für Fragen nach Kontoständen ("wie viel ist auf dem
                    Girokonto"). Für die fachliche Kontenstruktur dieses Haushaltsbuchs - Sphären,
                    Kontoarten, Zugriffsrechte - ist konten_auflisten zuständig; das sind zwei
                    verschiedene Dinge.

                    WICHTIG: Die Zahlen stammen aus dem gespeicherten Bestand, nicht aus einem
                    Live-Abruf. Nenne deshalb IMMER den Abrufzeitpunkt mit. Ein Saldo ohne
                    Zeitangabe ist eine Zahl ohne Aussage, und der Unterschied zwischen "von heute
                    früh" und "vom letzten Quartal" ist die ganze Information.

                    Diese Zahlen sind NICHT die Kennzahl "verfügbar". Verfügbar zieht Zahllasten,
                    Rücklagen und Fixkosten ab und wird eigens berechnet - ein Kontostand
                    beantwortet die Frage "geht das oder nicht" nicht.
                    """)
    public String bankkontenAuflisten() {
        benutzerkontext.anwenden();

        List<ExternesKonto> konten = bankzugangService.konten();
        if (konten.isEmpty()) {
            return """
                    Keine Konten von einer Bank bekannt. Das heisst nicht, dass keine existieren -
                    entweder ist noch kein Bankzugang eingerichtet, oder es ist niemand angemeldet.""";
        }

        StringBuilder ausgabe = new StringBuilder("Von der Bank gemeldete Konten:\n");
        for (ExternesKonto konto : konten) {
            ausgabe.append("- ").append(konto.bezeichnung());
            konto.iban().ifPresent(iban -> ausgabe.append(" (").append(iban).append(")"));
            ausgabe.append(", Währung ").append(konto.waehrung());
            ausgabe.append(", Kennung ").append(konto.kennung().wert()).append('\n');

            List<ExternerSaldo> salden = bankzugangService.letzteSalden(konto.id());
            if (salden.isEmpty()) {
                ausgabe.append("    kein Saldo abgerufen\n");
            } else {
                for (ExternerSaldo saldo : salden) {
                    ausgabe.append("    ").append(saldoZeile(saldo)).append('\n');
                }
            }
        }
        return ausgabe.toString();
    }

    @Tool(name = "bankkonto_details", description = """
                    Zeigt ein einzelnes von der Bank gemeldetes Konto mit allen bekannten Salden
                    und dem Zustand seines Bankzugangs.

                    Benutze dieses Tool, wenn eine Frage ein bestimmtes Konto betrifft oder wenn
                    unklar ist, wie aktuell die Zahlen sind. Die Kennung stammt aus
                    bankkonten_auflisten - erfinde sie niemals.

                    Der Zustand des Bankzugangs gehört in jede Antwort: ist er abgelaufen oder
                    fehlgeschlagen, sind die Zahlen die letzten bekannten und nicht die heutigen.
                    Sie sind deswegen nicht falsch, aber sie sind alt, und wer das nicht sagt,
                    lässt jemanden mit einer veralteten Zahl entscheiden.
                    """)
    public String bankkontoDetails(
            @ToolArg(description = "Kennung des Kontos aus bankkonten_auflisten") String kennung) {
        benutzerkontext.anwenden();

        if (kennung == null || kennung.isBlank()) {
            return "Ohne Kennung lässt sich kein Konto nachschlagen. Hole sie aus bankkonten_auflisten.";
        }

        Optional<ExternesKonto> gefunden = konto(kennung.trim());
        if (gefunden.isEmpty()) {
            return """
                    Kein Konto mit der Kennung '%s' bekannt. Entweder ist die Kennung falsch, oder
                    der angemeldete Benutzer sieht dieses Konto nicht - beides ist von aussen nicht
                    zu unterscheiden.""".formatted(kennung);
        }

        ExternesKonto konto = gefunden.get();
        StringBuilder ausgabe = new StringBuilder();
        ausgabe.append("Konto: ").append(konto.bezeichnung()).append('\n');
        konto.iban().ifPresent(iban -> ausgabe.append("IBAN: ").append(iban).append('\n'));
        ausgabe.append("Währung: ").append(konto.waehrung()).append('\n');
        konto.kontoart()
                .ifPresent(art ->
                        ausgabe.append("Kontoart laut Bank: ").append(art).append('\n'));
        konto.produktname()
                .ifPresent(name -> ausgabe.append("Produkt: ").append(name).append('\n'));
        ausgabe.append("Kennung: ").append(konto.kennung().wert()).append('\n');

        ausgabe.append('\n').append(zugangszeile(konto)).append('\n');

        List<ExternerSaldo> salden = bankzugangService.salden(konto.id());
        if (salden.isEmpty()) {
            ausgabe.append("\nEs wurde noch kein Saldo abgerufen.\n");
        } else {
            ausgabe.append("\nSalden, neueste zuerst:\n");
            for (ExternerSaldo saldo : salden) {
                ausgabe.append("- ").append(saldoZeile(saldo)).append('\n');
            }
        }

        ausgabe.append("""

                Hinweis: Das sind Kontostände, nicht die Kennzahl "verfügbar". Verfügbar zieht
                Zahllasten, Rücklagen und Fixkosten ab und wird eigens berechnet.""");
        return ausgabe.toString();
    }

    private Optional<ExternesKonto> konto(String kennung) {
        // Erst als interne Kennung, dann als stabile Kennung der Bank. Ein Modell kennt beide aus
        // der Auflistung, und ein Werkzeug, das nur eine davon annimmt, scheitert an der falschen.
        try {
            Optional<ExternesKonto> ueberId = bankzugangService.konto(ExternesKontoId.von(kennung));
            if (ueberId.isPresent()) {
                return ueberId;
            }
        } catch (IllegalArgumentException keineKennung) {
            // Kein Fehler: dann ist es eben die Kennung der Bank.
        }
        return bankzugangService.kontoNachKennung(new Kontokennung(kennung));
    }

    private String zugangszeile(ExternesKonto konto) {
        if (konto.bankzugang().isEmpty()) {
            return "Bankzugang: entfernt. Die Zahlen unten stammen aus dem letzten Abruf und werden nicht mehr "
                    + "aktualisiert.";
        }

        Optional<Bankzugang> zugang = konto.bankzugang().flatMap(bankzugangService::zugang);
        if (zugang.isEmpty()) {
            return "Bankzugang: nicht auffindbar.";
        }

        Bankzugang gefunden = zugang.get();
        Instant jetzt = Instant.now();
        StringBuilder zeile = new StringBuilder("Bankzugang: ")
                .append(gefunden.institutsname())
                .append(", Status ")
                .append(gefunden.status().name());

        gefunden.restgueltigkeit(jetzt)
                .map(Duration::toDays)
                .ifPresent(tage -> zeile.append(", noch ").append(tage).append(" Tage gültig"));
        gefunden.fehlermeldung()
                .ifPresent(meldung -> zeile.append(". Meldung: ").append(meldung));

        if (!gefunden.istNutzbar(jetzt)) {
            zeile.append("""
                    .
                    ACHTUNG: Dieser Zugang ist nicht mehr nutzbar. Die Zahlen unten sind die
                    zuletzt abgerufenen und werden nicht mehr aktualisiert.""");
        }
        return zeile.toString();
    }

    private static String saldoZeile(ExternerSaldo saldo) {
        return "%s: %s %s (Stand %s, abgerufen %s)"
                .formatted(
                        beschriftung(saldo),
                        saldo.betrag().wert().toPlainString(),
                        saldo.waehrung(),
                        saldo.referenzdatum().map(Object::toString).orElse("ohne Angabe"),
                        saldo.abgerufenAm());
    }

    private static String beschriftung(ExternerSaldo saldo) {
        return switch (saldo.art()) {
            case GEBUCHT -> "gebucht";
            case VERFUEGBAR -> "verfügbar laut Bank";
            case VORGEMERKT -> "einschliesslich vorgemerkt";
            case ABSCHLUSS -> "Periodenabschluss";
            case SONSTIGE -> "sonstiger Saldo (" + saldo.artOriginal() + ")";
        };
    }
}
