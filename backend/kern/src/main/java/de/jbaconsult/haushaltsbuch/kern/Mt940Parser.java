package de.jbaconsult.haushaltsbuch.kern;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest SWIFT MT940 als reine Funktion über Text.
 *
 * <p>Eingabe ist der Dateiinhalt, Ausgabe sind Auszüge und Befunde. Kein Datei-Ein/Ausgabe, kein
 * Datenbankzugriff, kein Framework - das Lesen der Datei und das Schreiben in die Datenbank liegen
 * außerhalb.
 *
 * <h2>Zwei Fallen, die dieser Parser bewusst umgeht</h2>
 *
 * <p><b>Das Zeichen nach C/D in Feld 61.</b> Es ist das dritte Zeichen des Währungscodes - bei EUR
 * also ein {@code R} - und nicht die Stornokennung. Wer {@code CR} als „Storno-Gutschrift" liest,
 * dreht jede zweite Buchung um. Ein Storno steht <b>davor</b>, als {@code RC} beziehungsweise
 * {@code RD}.
 *
 * <p><b>Das Buchungsdatum ohne Jahr.</b> Feld 61 liefert die Valuta als JJMMTT, das Buchungsdatum
 * aber nur als MMTT. Naiv das Jahr der Valuta zu übernehmen ist elf Monate im Jahr richtig und am
 * Jahreswechsel falsch: eine Buchung vom 30.12. mit Valuta 02.01. landet ein Jahr in der Zukunft.
 * Abgeleitet wird deshalb über den kleinsten Abstand zur Valuta.
 *
 * <h2>Zeilenumbrüche</h2>
 *
 * <p>MT940 bricht Zeilen bei etwa 65 Zeichen um, und der Umbruch fällt gern mitten in eine IBAN.
 * Fortsetzungszeilen gehören deshalb <b>ohne Trennzeichen</b> an die vorige Zeile - aber nur im
 * Mehrzweckfeld 86. In Feld 61 ist die zweite Zeile eine eigene Angabe; würde man sie anhängen,
 * verlängerte sie die Bankreferenz. Deshalb hält dieser Parser die physischen Zeilen je Feld
 * getrennt und lässt jedes Feld selbst entscheiden, wie es sie zusammenfügt.
 */
public final class Mt940Parser {

    /** Ein Feld beginnt mit {@code :NN:} oder {@code :NNc:}, etwa {@code :61:} oder {@code :60F:}. */
    private static final Pattern FELDBEGINN = Pattern.compile("^:(\\d{2}[A-Z]?):(.*)$");

    /**
     * Feld 61.
     *
     * <p>Aufbau: Valuta JJMMTT, optional Buchungsdatum MMTT, optional {@code R} für Storno,
     * Richtung {@code C} oder {@code D}, optional das dritte Zeichen des Währungscodes, Betrag mit
     * Komma, vierstelliger Buchungsschlüssel, Rest.
     *
     * <p>Die Reihenfolge {@code (R)?[CD]([A-Z])?} ist die ganze Lösung der ersten Falle: das
     * optionale {@code R} steht <b>vor</b> der Richtung, das optionale Währungszeichen dahinter.
     * {@code DR} ist damit Soll in Euro, {@code RD} ein Soll-Storno.
     */
    private static final Pattern FELD_61 = Pattern.compile("^(?<valuta>\\d{6})"
            + "(?<buchungMmtt>\\d{4})?"
            + "(?<storno>R)?"
            + "(?<richtung>[CD])"
            + "(?<waehrung>[A-Z])?"
            + "(?<betrag>\\d{1,15},\\d{0,2})"
            + "(?<schluessel>[A-Z][A-Z0-9]{3})"
            + "(?<rest>.*)$");

    /** Felder 60F/60M und 62F/62M: Richtung, Datum JJMMTT, Währung, Betrag. */
    private static final Pattern SALDO =
            Pattern.compile("^(?<richtung>[CD])(?<datum>\\d{6})(?<waehrung>[A-Z]{3})(?<betrag>[\\d.,]+)$");

    /** Teilfeld im Mehrzweckfeld 86: Fragezeichen und zwei Ziffern. */
    private static final Pattern TEILFELD = Pattern.compile("\\?(\\d{2})");

    /**
     * Die Kennungen im Verwendungszweck.
     *
     * <p>Sie sind der Grund, aus dem Mandatsreferenz und Gläubigerkennung überhaupt getrennt
     * vorliegen können - im Rohtext stehen sie hintereinander in denselben Teilfeldern und werden
     * erst hier auseinandergenommen.
     */
    private static final Pattern KENNUNG =
            Pattern.compile("(EREF|KREF|MREF|CRED|DEBT|COAM|OAMT|SVWZ|ABWA|ABWE|IBAN|BIC)\\+");

    /** Was aussieht, als hätte es eine IBAN werden sollen. Grundlage von I5. */
    private static final Pattern IBAN_KANDIDAT = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{6,}");

    /** Platzhalter, die Banken statt einer Bankreferenz setzen. */
    private static final List<String> KEINE_REFERENZ = List.of("NONREF", "NOTPROVIDED");

    private Mt940Parser() {}

    public static Parsebefund lies(String inhalt) {
        List<Kontoauszug> auszuege = new ArrayList<>();
        List<Importfehler> fehler = new ArrayList<>();

        for (List<Feld> block : bloecke(felder(inhalt))) {
            blockLesen(block, auszuege, fehler);
        }

        if (auszuege.isEmpty() && fehler.isEmpty()) {
            fehler.add(new Importfehler(Invariante.I3, "Datei", "Keine Auszuege gefunden - Feld :20: fehlt."));
        }
        return Parsebefund.von(auszuege, fehler);
    }

    // -------------------------------------------------------------------------------------------
    // Zerlegung in Felder und Blöcke
    // -------------------------------------------------------------------------------------------

    /** Ein Feld mit seinen physischen Zeilen. Wie sie zusammengefügt werden, entscheidet der Leser. */
    private record Feld(String tag, List<String> zeilen) {

        /** Ohne Trennzeichen zusammengefügt. Richtig für Feld 86 - so wird eine umgebrochene IBAN wieder ganz. */
        String zusammenhaengend() {
            return String.join("", zeilen);
        }

        String ersteZeile() {
            return zeilen.isEmpty() ? "" : zeilen.get(0);
        }
    }

    private static List<Feld> felder(String inhalt) {
        List<Feld> felder = new ArrayList<>();
        List<String> aktuelleZeilen = null;
        String aktuellerTag = null;

        for (String roh : inhalt.split("\\R", -1)) {
            String zeile = roh.stripTrailing();
            if (zeile.isEmpty() || "-".equals(zeile.strip())) {
                continue;
            }

            Matcher beginn = FELDBEGINN.matcher(zeile);
            if (beginn.matches()) {
                if (aktuellerTag != null) {
                    felder.add(new Feld(aktuellerTag, aktuelleZeilen));
                }
                aktuellerTag = beginn.group(1);
                aktuelleZeilen = new ArrayList<>();
                aktuelleZeilen.add(beginn.group(2));
            } else if (aktuellerTag != null) {
                // Fortsetzungszeile. Ohne Trennzeichen - siehe Klassenkommentar.
                aktuelleZeilen.add(zeile);
            }
            // Text vor dem ersten Feld ist Vorspann (etwa der SWIFT-Header) und wird verworfen.
        }

        if (aktuellerTag != null) {
            felder.add(new Feld(aktuellerTag, aktuelleZeilen));
        }
        return felder;
    }

    /** Ein neuer Block beginnt bei jedem Feld 20. */
    private static List<List<Feld>> bloecke(List<Feld> felder) {
        List<List<Feld>> bloecke = new ArrayList<>();
        List<Feld> aktuell = null;

        for (Feld feld : felder) {
            if ("20".equals(feld.tag())) {
                aktuell = new ArrayList<>();
                bloecke.add(aktuell);
            }
            if (aktuell != null) {
                aktuell.add(feld);
            }
        }
        return bloecke;
    }

    // -------------------------------------------------------------------------------------------
    // Ein Block
    // -------------------------------------------------------------------------------------------

    /** Eine Buchung vor der Vergabe der endgültigen Bankreferenz. */
    private record Rohzeile(Buchungszeile zeile, String roheReferenz) {}

    private static void blockLesen(List<Feld> block, List<Kontoauszug> auszuege, List<Importfehler> fehler) {
        String auszugsnummer = "?";
        Iban kontoIban = null;
        Saldo anfang = null;
        Saldo ende = null;
        List<Rohzeile> rohzeilen = new ArrayList<>();

        // Fehler dieses Blocks sammeln wir zunächst hier, weil die Bezeichnung des Auszugs erst
        // feststeht, wenn Feld 28C und die Salden gelesen sind.
        List<Importfehler> blockfehler = new ArrayList<>();

        for (int i = 0; i < block.size(); i++) {
            Feld feld = block.get(i);
            switch (feld.tag()) {
                case "25" -> {
                    String kennung = feld.zusammenhaengend().strip();
                    // Feld 25 trägt entweder eine IBAN oder BLZ/Kontonummer alter Bauart. Nur der
                    // erste Fall ist maschinell zuordenbar; der zweite ist kein Fehler.
                    String vorSchraegstrich =
                            kennung.contains("/") ? kennung.substring(0, kennung.indexOf('/')) : kennung;
                    kontoIban = ibanOderBefund(vorSchraegstrich, "Feld :25:", blockfehler)
                            .orElse(null);
                }
                case "28", "28C" -> {
                    String wert = feld.zusammenhaengend().strip();
                    auszugsnummer = wert.contains("/") ? wert.substring(0, wert.indexOf('/')) : wert;
                    if (auszugsnummer.isBlank()) {
                        auszugsnummer = "?";
                    }
                }
                case "60F", "60M" -> anfang = saldoLesen(feld, blockfehler);
                case "62F", "62M" -> ende = saldoLesen(feld, blockfehler);
                case "61" -> {
                    // I3: der Detailblock ist das unmittelbar folgende Feld 86. Fehlt er, ist die
                    // Buchung strukturiert nicht auswertbar - genau die Felder, wegen derer dieses
                    // Schema existiert, stehen dort drin.
                    Feld detail = (i + 1 < block.size()
                                    && "86".equals(block.get(i + 1).tag()))
                            ? block.get(i + 1)
                            : null;
                    if (detail == null) {
                        blockfehler.add(new Importfehler(
                                Invariante.I3, "?", "Buchung ohne Detailblock :86:: " + feld.ersteZeile()));
                        continue;
                    }
                    umsatzLesen(feld, detail, blockfehler).ifPresent(rohzeilen::add);
                }
                default -> {
                    // 20, 21, 86, 64, 65, 90x und alles Weitere braucht dieser Baustein nicht.
                }
            }
        }

        String bezeichnung = "Auszug " + auszugsnummer;

        if (anfang == null || ende == null) {
            blockfehler.add(new Importfehler(
                    Invariante.I1, bezeichnung, "Anfangs- oder Endsaldo fehlt (Feld :60F: bzw. :62F:)."));
            fehler.addAll(mitBezeichnung(blockfehler, bezeichnung));
            return;
        }

        fehler.addAll(mitBezeichnung(blockfehler, bezeichnung));

        auszuege.add(new Kontoauszug(
                Auszugsquelle.MT940,
                kontoIban,
                auszugsnummer,
                anfang.betrag(),
                ende.betrag(),
                anfang.datum(),
                ende.datum(),
                referenzenVergeben(rohzeilen)));
    }

    private static List<Importfehler> mitBezeichnung(List<Importfehler> fehler, String bezeichnung) {
        return fehler.stream()
                .map(f -> "?".equals(f.auszug()) ? new Importfehler(f.invariante(), bezeichnung, f.meldung()) : f)
                .toList();
    }

    private record Saldo(Betrag betrag, LocalDate datum) {}

    private static Saldo saldoLesen(Feld feld, List<Importfehler> fehler) {
        Matcher treffer = SALDO.matcher(feld.zusammenhaengend().strip());
        if (!treffer.matches()) {
            fehler.add(new Importfehler(Invariante.I1, "?", "Saldofeld nicht lesbar: " + feld.zusammenhaengend()));
            return null;
        }
        Betrag betrag = betragLesen(treffer.group("betrag"));
        if ("D".equals(treffer.group("richtung"))) {
            betrag = betrag.negiert();
        }
        return new Saldo(betrag, datumLesen(treffer.group("datum")));
    }

    // -------------------------------------------------------------------------------------------
    // Feld 61 und 86
    // -------------------------------------------------------------------------------------------

    private static Optional<Rohzeile> umsatzLesen(Feld feld61, Feld feld86, List<Importfehler> fehler) {
        // Nur die erste physische Zeile: die zweite ist eine eigene Angabe und würde, angehängt,
        // die Bankreferenz verlängern.
        Matcher treffer = FELD_61.matcher(feld61.ersteZeile().strip());
        if (!treffer.matches()) {
            fehler.add(new Importfehler(Invariante.I3, "?", "Feld :61: nicht lesbar: " + feld61.ersteZeile()));
            return Optional.empty();
        }

        LocalDate valuta = datumLesen(treffer.group("valuta"));
        String buchungMmtt = treffer.group("buchungMmtt");
        LocalDate buchungstag = buchungMmtt == null
                ? valuta
                : buchungstagAusValuta(
                        valuta,
                        Integer.parseInt(buchungMmtt.substring(0, 2)),
                        Integer.parseInt(buchungMmtt.substring(2, 4)));

        Betrag betrag = betragLesen(treffer.group("betrag"));
        if ("D".equals(treffer.group("richtung"))) {
            betrag = betrag.negiert();
        }
        boolean storno = treffer.group("storno") != null;

        String rest = treffer.group("rest");
        int trenner = rest.indexOf("//");
        String bankreferenz = trenner >= 0 ? rest.substring(trenner + 2).strip() : "";

        Detail detail = detailLesen(feld86, fehler);

        Buchungszeile zeile = new Buchungszeile(
                // Vorläufig. Die endgültige Referenz vergibt referenzenVergeben.
                "vorlaeufig",
                buchungstag,
                valuta,
                betrag,
                storno,
                detail.gegenparteiName(),
                detail.gegenparteiIban(),
                detail.mandatsreferenz(),
                detail.glaeubigerkennung(),
                detail.endeZuEndeReferenz(),
                detail.verwendungszweck(),
                detail.buchungstext());

        return Optional.of(new Rohzeile(zeile, bankreferenz));
    }

    private record Detail(
            String gegenparteiName,
            Iban gegenparteiIban,
            String mandatsreferenz,
            String glaeubigerkennung,
            String endeZuEndeReferenz,
            String verwendungszweck,
            String buchungstext) {}

    private static Detail detailLesen(Feld feld86, List<Importfehler> fehler) {
        // Hier zählt das Zusammenfügen ohne Trennzeichen: eine über den Zeilenumbruch verteilte
        // IBAN in ?31 wird genau dadurch wieder ganz.
        Map<String, String> teilfelder = teilfelder(feld86.zusammenhaengend());

        StringBuilder zweckRoh = new StringBuilder();
        for (int code = 20; code <= 29; code++) {
            zweckRoh.append(teilfelder.getOrDefault(String.format("%02d", code), ""));
        }
        for (int code = 60; code <= 63; code++) {
            zweckRoh.append(teilfelder.getOrDefault(String.format("%02d", code), ""));
        }

        Map<String, String> kennungen = kennungen(zweckRoh.toString());

        String name = (teilfelder.getOrDefault("32", "") + teilfelder.getOrDefault("33", "")).strip();
        if (name.isEmpty()) {
            name = kennungen.getOrDefault("ABWA", "").strip();
        }

        // Reihenfolge nach constraint.klassifikation-iban-mref: die strukturierte Angabe vor der
        // im Verwendungszweck mitgeschleppten.
        String ibanRoh = teilfelder.getOrDefault("31", "").strip();
        if (ibanRoh.isEmpty()) {
            ibanRoh = kennungen.getOrDefault("IBAN", "").strip();
        }
        Iban gegenpartei = ibanOderBefund(ibanRoh, "Teilfeld ?31", fehler).orElse(null);

        return new Detail(
                leerAlsNull(name),
                gegenpartei,
                leerAlsNull(kennungen.get("MREF")),
                leerAlsNull(kennungen.get("CRED")),
                leerAlsNull(kennungen.get("EREF")),
                leerAlsNull(kennungen.containsKey("SVWZ") ? kennungen.get("SVWZ") : zweckRoh.toString()),
                leerAlsNull(teilfelder.get("00")));
    }

    private static Map<String, String> teilfelder(String inhalt) {
        Map<String, String> teilfelder = new LinkedHashMap<>();
        Matcher treffer = TEILFELD.matcher(inhalt);

        int letzterCodeEnde = -1;
        String letzterCode = null;
        while (treffer.find()) {
            if (letzterCode != null) {
                teilfelder.merge(letzterCode, inhalt.substring(letzterCodeEnde, treffer.start()), String::concat);
            }
            letzterCode = treffer.group(1);
            letzterCodeEnde = treffer.end();
        }
        if (letzterCode != null) {
            teilfelder.merge(letzterCode, inhalt.substring(letzterCodeEnde), String::concat);
        }
        return teilfelder;
    }

    /**
     * Zerlegt den Verwendungszweck an seinen Kennungen.
     *
     * <p>Der Inhalt einer Kennung reicht bis zur nächsten. Ein Text ohne jede Kennung ist der
     * Verwendungszweck als Ganzes - das ältere Format, das es weiterhin gibt.
     */
    private static Map<String, String> kennungen(String zweck) {
        Map<String, String> ergebnis = new LinkedHashMap<>();
        Matcher treffer = KENNUNG.matcher(zweck);

        String letzte = null;
        int letzteEnde = -1;
        while (treffer.find()) {
            if (letzte != null) {
                ergebnis.put(
                        letzte, zweck.substring(letzteEnde, treffer.start()).strip());
            }
            letzte = treffer.group(1);
            letzteEnde = treffer.end();
        }
        if (letzte != null) {
            ergebnis.put(letzte, zweck.substring(letzteEnde).strip());
        }
        return ergebnis;
    }

    // -------------------------------------------------------------------------------------------
    // Bankreferenzen
    // -------------------------------------------------------------------------------------------

    /**
     * Vergibt die endgültigen Bankreferenzen - den Schlüssel der Deduplizierung (I4).
     *
     * <p>Zwei Fälle zwingen zu einer abgeleiteten Referenz:
     *
     * <ul>
     *   <li>Die Bank liefert {@code NONREF} oder gar nichts.
     *   <li>Dieselbe Referenz steht an mehreren Buchungen desselben Auszugs. Dann ist sie als
     *       Schlüssel unbrauchbar, und ein Import würde die zweite Buchung als Doublette der ersten
     *       verwerfen - lautlos.
     * </ul>
     *
     * <p>Die abgeleitete Referenz ist der Fingerabdruck des Inhalts plus ein Zähler über gleiche
     * Inhalte. Sie muss über Läufe hinweg stabil sein, weil sonst jeder erneute Import Doubletten
     * erzeugt. Deshalb geht ausschließlich Inhalt ein - Valuta, Buchungstag, Betrag, Zweck,
     * Gegenpartei - und keine Position in der Datei: Exportzeiträume überlappen sich an den
     * Randtagen, und dieselbe Buchung steht dann an anderer Stelle. Der Zähler bleibt trotzdem
     * stabil, weil Auszüge tagesweise geschnitten sind: ein Tag ist ganz enthalten oder gar nicht.
     */
    private static List<Buchungszeile> referenzenVergeben(List<Rohzeile> rohzeilen) {
        Map<String, Integer> haeufigkeit = new LinkedHashMap<>();
        for (Rohzeile roh : rohzeilen) {
            if (!istPlatzhalter(roh.roheReferenz())) {
                haeufigkeit.merge(roh.roheReferenz(), 1, Integer::sum);
            }
        }

        Map<String, Integer> zaehler = new LinkedHashMap<>();
        List<Buchungszeile> zeilen = new ArrayList<>(rohzeilen.size());

        for (Rohzeile roh : rohzeilen) {
            String referenz = roh.roheReferenz();
            boolean brauchbar = !istPlatzhalter(referenz) && haeufigkeit.getOrDefault(referenz, 0) == 1;

            if (!brauchbar) {
                String fingerabdruck = fingerabdruck(roh.zeile());
                int lauf = zaehler.merge(fingerabdruck, 1, Integer::sum) - 1;
                referenz = "ABGELEITET:" + fingerabdruck + ":" + lauf;
            }

            Buchungszeile z = roh.zeile();
            zeilen.add(new Buchungszeile(
                    referenz,
                    z.buchungstag(),
                    z.valuta(),
                    z.betrag(),
                    z.storno(),
                    z.gegenparteiName(),
                    z.gegenparteiIban(),
                    z.mandatsreferenz(),
                    z.glaeubigerkennung(),
                    z.endeZuEndeReferenz(),
                    z.verwendungszweck(),
                    z.buchungstext()));
        }
        return zeilen;
    }

    private static boolean istPlatzhalter(String referenz) {
        return referenz == null || referenz.isBlank() || KEINE_REFERENZ.contains(referenz.toUpperCase());
    }

    private static String fingerabdruck(Buchungszeile zeile) {
        String stoff = String.join(
                "|",
                zeile.valuta().toString(),
                zeile.buchungstag().toString(),
                zeile.betrag().wert().toPlainString(),
                String.valueOf(zeile.verwendungszweck()),
                String.valueOf(zeile.gegenparteiName()),
                String.valueOf(zeile.gegenparteiIban()),
                String.valueOf(zeile.mandatsreferenz()),
                String.valueOf(zeile.glaeubigerkennung()),
                String.valueOf(zeile.endeZuEndeReferenz()));
        try {
            byte[] abdruck = MessageDigest.getInstance("SHA-256").digest(stoff.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(abdruck, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 ist in jeder Java-Laufzeit vorhanden. Tritt das ein, ist etwas grundlegend
            // kaputt, und ein stiller Ersatzschluessel waere die schlechtere Antwort.
            throw new IllegalStateException("SHA-256 nicht verfuegbar", e);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Kleinteile
    // -------------------------------------------------------------------------------------------

    /**
     * Leitet den Buchungstag aus MMTT und der Valuta ab.
     *
     * <p>Gewählt wird das Jahr mit dem kleinsten Abstand zur Valuta - bei Gleichstand das Jahr der
     * Valuta selbst. Damit wird eine Buchung vom 30.12. mit Valuta 02.01.2027 auf den 30.12.2026
     * abgebildet und nicht auf den 30.12.2027, der ein Jahr in der Zukunft läge.
     *
     * <p>Der 29. Februar in einem Nicht-Schaltjahr wird als Kandidat übersprungen, statt eine
     * Ausnahme zu werfen.
     *
     * <p>Weiter als ein halbes Jahr darf die Ableitung nicht greifen. Der echte Jahreswechselfall
     * liegt wenige Tage neben der Valuta, ein falsches Jahr rund dreihundertsechzig - dazwischen ist
     * viel Platz. Bleibt kein Kandidat in dieser Spanne, ist die Valuta die ehrlichere Angabe als
     * ein Datum, das ein Jahr danebenliegt und trotzdem plausibel aussieht.
     */
    private static final long HOECHSTABSTAND_TAGE = 180;

    static LocalDate buchungstagAusValuta(LocalDate valuta, int monat, int tag) {
        LocalDate beste = null;
        long besterAbstand = Long.MAX_VALUE;

        for (int versatz : new int[] {0, -1, 1}) {
            LocalDate kandidat;
            try {
                kandidat = LocalDate.of(valuta.getYear() + versatz, monat, tag);
            } catch (DateTimeException nichtExistent) {
                continue;
            }
            long abstand = Math.abs(ChronoUnit.DAYS.between(kandidat, valuta));
            if (abstand < besterAbstand) {
                besterAbstand = abstand;
                beste = kandidat;
            }
        }

        return beste != null && besterAbstand <= HOECHSTABSTAND_TAGE ? beste : valuta;
    }

    private static LocalDate datumLesen(String jjmmtt) {
        int jahr = 2000 + Integer.parseInt(jjmmtt.substring(0, 2));
        return LocalDate.of(jahr, Integer.parseInt(jjmmtt.substring(2, 4)), Integer.parseInt(jjmmtt.substring(4, 6)));
    }

    private static Betrag betragLesen(String text) {
        String normalisiert = text.replace(".", "").replace(',', '.');
        if (normalisiert.endsWith(".")) {
            normalisiert = normalisiert + "0";
        }
        return new Betrag(new BigDecimal(normalisiert));
    }

    /**
     * Liest eine IBAN und meldet I5, wenn der Text nach einer IBAN aussieht, aber keine ist.
     *
     * <p>Der Unterschied ist wesentlich. Eine Kontonummer alter Bauart - nur Ziffern - ist keine
     * IBAN und auch kein Fehler. Eine Zeichenfolge aus zwei Buchstaben, zwei Ziffern und weiterem
     * Zeichenvorrat war als IBAN gemeint; scheitert sie an der Prüfsumme, ist genau der Fall
     * eingetreten, gegen den I5 antritt - typischerweise ein falsch zusammengefügter Zeilenumbruch.
     */
    private static Optional<Iban> ibanOderBefund(String text, String herkunft, List<Importfehler> fehler) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalisiert = Iban.normalisieren(text);
        Optional<Iban> iban = Iban.lesen(normalisiert);
        if (iban.isPresent()) {
            return iban;
        }
        if (IBAN_KANDIDAT.matcher(normalisiert).matches()) {
            fehler.add(new Importfehler(
                    Invariante.I5,
                    "?",
                    "IBAN-Pruefsumme falsch in " + herkunft + ": " + normalisiert
                            + " - meist ein falsch zusammengefuegter Zeilenumbruch."));
        }
        return Optional.empty();
    }

    private static String leerAlsNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
