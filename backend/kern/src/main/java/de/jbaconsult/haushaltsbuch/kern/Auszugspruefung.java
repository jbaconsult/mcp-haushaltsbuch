package de.jbaconsult.haushaltsbuch.kern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Saldenprüfung, die entscheidet, ob ein Auszug geschrieben werden darf.
 *
 * <p>Grundlage ist {@code constraint.import-saldenvalidierung}: ein Import, der sich nicht selbst
 * validiert, gilt als nicht erfolgt. Diese Klasse ist der Ort, an dem das „gilt als nicht erfolgt"
 * ausgesprochen wird.
 *
 * <p>Geprüft werden hier I1 und I2. I3 und I5 entstehen bereits beim Lesen, weil sie sich auf
 * Struktur und Feldinhalt beziehen und nicht auf Salden; I4 ist keine Prüfung, sondern eine
 * Eigenschaft des Schreibens.
 *
 * <p><b>Warum die Salden nicht berechnet werden:</b> Anfangs- und Endsaldo stehen so in der Datei,
 * wie die Bank sie geliefert hat. Genau der Vergleich zwischen dieser Angabe und der Summe der
 * Buchungen ist die Prüfung. Wer den Endsaldo aus den Buchungen ableitet, bekommt eine Rechnung,
 * die immer aufgeht - und merkt nie, dass eine Buchung fehlt.
 */
public final class Auszugspruefung {

    private Auszugspruefung() {}

    public static List<Importfehler> pruefe(List<Kontoauszug> auszuege) {
        List<Importfehler> fehler = new ArrayList<>();
        fehler.addAll(pruefeSaldenschluss(auszuege));
        fehler.addAll(pruefeBlockkette(auszuege));
        return fehler;
    }

    /** I1 - Anfangssaldo plus Summe der Buchungen gleich Endsaldo, je Auszug. */
    private static List<Importfehler> pruefeSaldenschluss(List<Kontoauszug> auszuege) {
        List<Importfehler> fehler = new ArrayList<>();
        for (Kontoauszug auszug : auszuege) {
            Betrag erwartet = auszug.anfangssaldo().plus(auszug.summeDerBuchungen());
            if (!erwartet.equals(auszug.endsaldo())) {
                fehler.add(new Importfehler(
                        Invariante.I1,
                        auszug.bezeichnung(),
                        "Anfangssaldo " + auszug.anfangssaldo()
                                + " plus Summe der " + auszug.zeilen().size() + " Buchungen "
                                + auszug.summeDerBuchungen()
                                + " ergibt " + erwartet
                                + ", die Bank meldet als Endsaldo " + auszug.endsaldo()
                                + ". Differenz " + auszug.endsaldo().minus(erwartet) + "."));
            }
        }
        return fehler;
    }

    /**
     * I2 - Endsaldo Block N gleich Anfangssaldo Block N+1, je Konto.
     *
     * <p>Gruppiert wird nach der IBAN aus der Datei. Trägt eine Datei keine IBAN - MT940 mit
     * Kontonummer alter Bauart -, gelten ihre Blöcke als ein Konto; das ist die einzige Annahme,
     * die ohne Zuordnung übrig bleibt, und sie stimmt für Exporte, die je Konto entstehen.
     *
     * <p>Verglichen wird nach Zeitraum sortiert, nicht in Dateireihenfolge: eine umgekehrt
     * sortierte Datei ist kein Kettenbruch, sondern eine umgekehrt sortierte Datei.
     *
     * <p>Der Befund hängt am <b>späteren</b> Block. Sein Anfangssaldo ist die Angabe, die nicht
     * passt; der frühere Block ist für sich genommen in Ordnung.
     */
    private static List<Importfehler> pruefeBlockkette(List<Kontoauszug> auszuege) {
        Map<String, List<Kontoauszug>> jeKonto = new LinkedHashMap<>();
        for (Kontoauszug auszug : auszuege) {
            String schluessel = auszug.konto().map(Iban::wert).orElse("");
            jeKonto.computeIfAbsent(schluessel, k -> new ArrayList<>()).add(auszug);
        }

        List<Importfehler> fehler = new ArrayList<>();
        for (List<Kontoauszug> kette : jeKonto.values()) {
            List<Kontoauszug> sortiert = kette.stream()
                    .sorted(Comparator.comparing(Kontoauszug::von).thenComparing(Kontoauszug::bis))
                    .toList();

            for (int i = 1; i < sortiert.size(); i++) {
                Kontoauszug vorher = sortiert.get(i - 1);
                Kontoauszug nachher = sortiert.get(i);
                if (!vorher.endsaldo().equals(nachher.anfangssaldo())) {
                    fehler.add(new Importfehler(
                            Invariante.I2,
                            nachher.bezeichnung(),
                            "Anfangssaldo " + nachher.anfangssaldo()
                                    + " passt nicht zum Endsaldo " + vorher.endsaldo()
                                    + " des vorigen Blocks (" + vorher.bezeichnung() + ")."));
                }
            }
        }
        return fehler;
    }
}
