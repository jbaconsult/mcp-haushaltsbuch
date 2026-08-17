package de.jbaconsult.haushaltsbuch.kern;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/**
 * Eine IBAN mit geprüfter Prüfsumme.
 *
 * <p>Der Typ existiert wegen einer sehr konkreten Eigenschaft des MT940-Formats: Zeilen brechen bei
 * etwa 65 Zeichen um, und der Umbruch fällt gern mitten in eine IBAN. Wer die Fortsetzungszeile
 * falsch anfügt - mit Leerzeichen, mit Zeilenumbruch, gar nicht -, bekommt eine IBAN, die aussieht
 * wie eine IBAN. Die Prüfsumme ist das einzige, was den Unterschied bemerkt. Das ist Invariante I5.
 *
 * <p>Deshalb ist der einzige Weg in diesen Typ eine geprüfte Fabrikmethode. Ein Konstruktor, den man
 * mit beliebigem Text füttern kann, würde die Prüfung optional machen - und optional heißt hier:
 * fehlt genau dort, wo sie zählt.
 */
public record Iban(String wert) {

    /**
     * Zwei Buchstaben Land, zwei Ziffern Prüfsumme, danach 11 bis 30 alphanumerische Zeichen.
     *
     * <p>Die Obergrenze ist die längste vergebene IBAN-Länge und keine Schätzung; die Untergrenze
     * schließt die kürzeste aus. Eine reine Kontonummer aus dem Feld {@code ?31} eines alten
     * MT940-Belegs fällt hier durch, und das ist die Absicht - sie ist keine IBAN.
     */
    private static final String MUSTER = "[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}";

    private static final BigInteger SIEBENUNDNEUNZIG = BigInteger.valueOf(97);

    public Iban {
        Objects.requireNonNull(wert, "wert darf nicht null sein");
        if (!istGueltig(wert)) {
            throw new IllegalArgumentException("keine gueltige IBAN: " + wert);
        }
    }

    /**
     * Liest eine IBAN aus Text, der aus einem Bankformat stammt.
     *
     * <p>Leer, wenn der Text keine gültige IBAN ist. Bewusst kein Fehler: an vielen Stellen im
     * MT940 steht in demselben Feld mal eine IBAN und mal eine alte Kontonummer, und letztere ist
     * kein Fehlerfall, sondern nur keine IBAN.
     */
    public static Optional<Iban> lesen(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String normalisiert = normalisieren(text);
        return istGueltig(normalisiert) ? Optional.of(new Iban(normalisiert)) : Optional.empty();
    }

    /** Entfernt Leerraum und hebt Buchstaben an. Beides kommt in Exportdateien vor. */
    public static String normalisieren(String text) {
        return text.replaceAll("\\s", "").toUpperCase();
    }

    /**
     * Prüfsumme nach ISO 7064 Mod 97-10.
     *
     * <p>Die ersten vier Zeichen wandern ans Ende, Buchstaben werden zu zweistelligen Zahlen (A=10
     * bis Z=35), der Rest der Division durch 97 muss eins sein.
     */
    public static boolean istGueltig(String kandidat) {
        if (kandidat == null || !kandidat.matches(MUSTER)) {
            return false;
        }

        String umgestellt = kandidat.substring(4) + kandidat.substring(0, 4);

        StringBuilder ziffern = new StringBuilder(umgestellt.length() * 2);
        for (int i = 0; i < umgestellt.length(); i++) {
            char zeichen = umgestellt.charAt(i);
            if (zeichen >= '0' && zeichen <= '9') {
                ziffern.append(zeichen);
            } else {
                ziffern.append(zeichen - 'A' + 10);
            }
        }

        return new BigInteger(ziffern.toString()).mod(SIEBENUNDNEUNZIG).intValue() == 1;
    }

    @Override
    public String toString() {
        return wert;
    }
}
