package de.jbaconsult.haushaltsbuch.mcp;

import java.util.List;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

import de.jbaconsult.haushaltsbuch.kern.Konto;
import de.jbaconsult.haushaltsbuch.kern.KontoService;
import de.jbaconsult.haushaltsbuch.kern.Sphaere;

/**
 * MCP-Werkzeuge rund um Konten.
 *
 * <p>Laut HB-05 ist die MCP-Oberfläche die primäre Schnittstelle des Systems und damit ein
 * erstklassiges Design-Artefakt - kein Adapter, der am Ende angeschraubt wird.
 *
 * <p>Zwei Regeln für jedes Tool in diesem Modul:
 *
 * <ol>
 *   <li><b>Keine Fachlogik.</b> Ein Tool übersetzt zwischen Gesprächsebene und {@code kern}. Wer
 *       hier rechnet, erzeugt einen zweiten Rechenweg neben REST - und zwei Implementierungen
 *       derselben Kennzahl laufen mit der Zeit auseinander.
 *   <li><b>Die Beschreibung sagt, WANN das Tool zu benutzen ist</b>, nicht nur was es tut. Ein
 *       Modell wählt Werkzeuge nach der Beschreibung aus; eine, die nur den Methodennamen
 *       wiederholt, hilft ihm nicht.
 * </ol>
 */
public class KontenTools {

    private final KontoService kontoService;
    private final McpBenutzerkontext benutzerkontext;

    @Inject
    public KontenTools(KontoService kontoService, McpBenutzerkontext benutzerkontext) {
        this.kontoService = kontoService;
        this.benutzerkontext = benutzerkontext;
    }

    @Tool(name = "konten_auflisten", description = """
                    Listet die Konten auf, auf die der angemeldete Benutzer Zugriff hat.

                    Benutze dieses Tool, BEVOR du eine kontobezogene Frage beantwortest: die
                    Zugriffsrechte unterscheiden sich je Benutzer, und ein Konto, das hier nicht
                    erscheint, existiert für diese Anfrage nicht.

                    Erfinde niemals Kontonamen oder Kennungen - nimm ausschliesslich die, die
                    dieses Tool zurückgibt.
                    """)
    public String kontenAuflisten() {
        benutzerkontext.anwenden();

        return alsListe(kontoService.sichtbareKonten());
    }

    @Tool(name = "konten_einer_sphaere", description = """
                    Listet die Konten einer einzelnen Sphäre auf.

                    Die drei Sphären sind strikt getrennt: PRIVAT (privat und gemeinsam),
                    FREIBERUFLICH und FINANZAMT. Verbunden sind sie über genau zwei Kanten,
                    die Privatentnahme und die Steuerrücklage.

                    Benutze dieses Tool, wenn eine Frage ausdrücklich nur einen Bereich betrifft
                    - etwa "wie steht es geschäftlich". Summiere NIEMALS über Sphärengrenzen
                    hinweg: das ist fachlich falsch, auch wenn die Zahlen sich addieren lassen.
                    """)
    public String kontenEinerSphaere(
            @ToolArg(description = "Eine der Sphären: PRIVAT, FREIBERUFLICH oder FINANZAMT") String sphaere) {
        benutzerkontext.anwenden();

        Sphaere gewaehlt;
        try {
            gewaehlt = Sphaere.valueOf(sphaere.trim().toUpperCase());
        } catch (IllegalArgumentException unbekannt) {
            return "Unbekannte Sphäre: '%s'. Erlaubt sind PRIVAT, FREIBERUFLICH und FINANZAMT.".formatted(sphaere);
        }

        return alsListe(kontoService.kontenDerSphaere(gewaehlt));
    }

    /**
     * Formatiert Konten für die Gesprächsebene.
     *
     * <p>Die leere Liste bekommt einen erklärenden Satz statt eines leeren Ergebnisses. Ein Modell,
     * das „keine Konten" liest, schliesst sonst auf ein leeres System - der wahrscheinlichere Grund
     * ist ein fehlender Benutzerkontext.
     */
    private static String alsListe(List<Konto> konten) {
        if (konten.isEmpty()) {
            return """
                    Keine Konten sichtbar. Das bedeutet nicht, dass keine existieren - entweder hat
                    der angemeldete Benutzer auf keines Zugriff, oder es ist keiner angemeldet.""";
        }

        StringBuilder ausgabe = new StringBuilder("Sichtbare Konten:\n");
        for (Konto konto : konten) {
            ausgabe.append("- %s (%s, Sphäre %s, Kennung %s)%n"
                    .formatted(konto.bezeichnung(), konto.art(), konto.sphaere(), konto.id()));
        }
        return ausgabe.toString();
    }
}
