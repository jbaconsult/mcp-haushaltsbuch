package de.jbaconsult.haushaltsbuch.kern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Der Weg von einer Bankdatei in den Datenbestand - und das Tor davor.
 *
 * <p>Die Reihenfolge ist die ganze Aussage dieser Klasse: <b>lesen, prüfen, dann erst schreiben</b>.
 * Kein Auszug wird angefasst, bevor alle Invarianten für ihn beantwortet sind.
 *
 * <p>Das ist kein Stilfrage. Ein Importer, der schreibt und bei einer Unstimmigkeit zurückrollt,
 * hängt davon ab, dass das Zurückrollen genau die richtige Menge trifft - und ein Rollback, der zu
 * weit zurückgeht, ist derselbe Datenverlust wie ein fehlender, nur schwerer zu bemerken. Wer vorher
 * prüft, braucht das Zurückrollen für den erwarteten Fall gar nicht.
 *
 * <p>Granularität ist der einzelne Auszug: eine Datei mit drei Auszügen, von denen einer nicht
 * aufgeht, schreibt die anderen beiden. Der dritte landet vollständig in der Fehlerliste.
 *
 * <p><b>Was dieser Dienst nicht tut:</b> kategorisieren. Jede importierte Buchung bekommt genau
 * einen Split ohne Kategorie. Ein Importer, der unterwegs schon zuordnet, macht den späteren
 * Trockenlauf-Modus unmöglich, den {@code constraint.regelvorschlag-reichweite} für die
 * Reichweitenanzeige einer Regel braucht.
 */
@ApplicationScoped
public class Importdienst {

    private final LedgerSchreibPort schreibPort;

    @Inject
    public Importdienst(LedgerSchreibPort schreibPort) {
        this.schreibPort = schreibPort;
    }

    /**
     * Liest eine Bankdatei und schreibt, was durchkommt.
     *
     * @param konto Zielkonto. Die Zuordnung IBAN zu Konto ist bewusst <b>nicht</b> Sache dieses
     *     Dienstes: Konten tragen keine IBAN, weil das Zielbild Open Source ist und Bankverbindungen
     *     ausschließlich aus Konfiguration kommen
     * @param erwarteteIban IBAN, auf die die Datei lauten muss. {@code null} verzichtet auf die
     *     Prüfung - dann liegt es beim Aufrufer, die Datei nicht auf das falsche Konto zu legen
     */
    public Importergebnis importiere(KontoId konto, Iban erwarteteIban, Auszugsquelle quelle, String inhalt) {

        Parsebefund befund =
                switch (quelle) {
                    case MT940 -> Mt940Parser.lies(inhalt);
                    case CAMT052 -> Camt052Parser.lies(inhalt);
                };

        List<Importfehler> fehler = new ArrayList<>(befund.fehler());
        fehler.addAll(Auszugspruefung.pruefe(befund.auszuege()));
        fehler.addAll(pruefeKontozuordnung(befund.auszuege(), erwarteteIban));

        // Ein Befund ohne Auszugsbezug - eine unlesbare Datei - belastet alles. Sonst würde eine
        // kaputte Datei teilweise durchlaufen.
        boolean dateiweiterBefund = fehler.stream().anyMatch(f -> "Datei".equals(f.auszug()));
        Set<String> belastet =
                new HashSet<>(fehler.stream().map(Importfehler::auszug).toList());

        List<Auszugsergebnis> geschrieben = new ArrayList<>();
        if (!dateiweiterBefund) {
            for (Kontoauszug auszug : befund.auszuege()) {
                if (belastet.contains(auszug.bezeichnung())) {
                    continue;
                }
                geschrieben.add(schreibPort.schreibe(konto, auszug));
            }
        }

        return new Importergebnis(geschrieben, fehler);
    }

    /** Kurzform ohne IBAN-Abgleich. */
    public Importergebnis importiere(KontoId konto, Auszugsquelle quelle, String inhalt) {
        return importiere(konto, null, quelle, inhalt);
    }

    /**
     * Vergleicht die IBAN aus der Datei mit der erwarteten.
     *
     * <p>Als I5 geführt, weil es dieselbe Frage ist: passt diese IBAN. Ein Auszug, der auf ein
     * anderes Konto lautet als das Ziel, ist der Fehler mit den unangenehmsten Folgen - er läuft
     * durch, die Salden gehen auf, und der Bestand ist trotzdem falsch.
     */
    private static List<Importfehler> pruefeKontozuordnung(List<Kontoauszug> auszuege, Iban erwartet) {
        if (erwartet == null) {
            return List.of();
        }
        List<Importfehler> fehler = new ArrayList<>();
        for (Kontoauszug auszug : auszuege) {
            auszug.konto()
                    .filter(gelesen -> !gelesen.equals(erwartet))
                    .ifPresent(gelesen -> fehler.add(new Importfehler(
                            Invariante.I5,
                            auszug.bezeichnung(),
                            "Die Datei lautet auf " + gelesen + ", erwartet war " + erwartet + ".")));
        }
        return fehler;
    }
}
