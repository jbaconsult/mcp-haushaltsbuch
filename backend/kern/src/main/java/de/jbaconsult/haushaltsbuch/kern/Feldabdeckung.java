package de.jbaconsult.haushaltsbuch.kern;

import java.util.List;
import java.util.Objects;

/**
 * Was ein Anbieter je Buchung tatsächlich liefert.
 *
 * <p>Diese Messung entscheidet mehr als die Bequemlichkeit eines Kanals. Der FinTS-Weg desselben
 * Instituts liefert Mandatsreferenzen, aber in 340 von 340 gemessenen Buchungen keine
 * Gläubigerkennung - damit ist die Klassifikation über IBAN, Mandatsreferenz und Gläubigerkennung
 * dort nur teilweise möglich und die Acquirer-Regel nicht anwendbar. Ob ein anderer Kanal beides
 * führt, ist keine Frage der Dokumentation, sondern eine Messung an Daten.
 *
 * <p>Das Ergebnis ist flüchtig: es wird berichtet und nicht gespeichert.
 *
 * @param anzahlBuchungen wie viele Buchungen die Stichprobe umfasste
 * @param felder je Feld, in wie vielen davon es belegt war
 * @param hinweise Beobachtungen, die keine Zählung sind
 */
public record Feldabdeckung(int anzahlBuchungen, List<Feldbefund> felder, List<String> hinweise) {

    public Feldabdeckung {
        felder = felder == null ? List.of() : List.copyOf(felder);
        hinweise = hinweise == null ? List.of() : List.copyOf(hinweise);
    }

    /**
     * Ein einzelnes Feld der Messung.
     *
     * @param name fachlicher Name des Feldes
     * @param herkunft wo es im Datensatz des Anbieters steht
     * @param belegt in wie vielen Buchungen es einen Wert hatte
     * @param gesamt Größe der Stichprobe
     */
    public record Feldbefund(String name, String herkunft, int belegt, int gesamt) {

        public Feldbefund {
            Objects.requireNonNull(name, "name darf nicht null sein");
            Objects.requireNonNull(herkunft, "herkunft darf nicht null sein");
        }

        /**
         * Bewertung in Worten.
         *
         * <p>„Nicht im Datensatz" ist bewusst von „bei keiner Buchung belegt" unterschieden: das
         * erste ist eine Eigenschaft des Kanals, das zweite könnte an der Stichprobe liegen. Diese
         * Methode kann nur das zweite feststellen und sagt deshalb genau das.
         */
        public String bewertung() {
            if (gesamt == 0) {
                return "keine Buchungen in der Stichprobe";
            }
            if (belegt == 0) {
                return "in keiner Buchung der Stichprobe belegt";
            }
            if (belegt == gesamt) {
                return "durchgehend vorhanden";
            }
            return "teilweise vorhanden (%d von %d)".formatted(belegt, gesamt);
        }
    }
}
