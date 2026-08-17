package de.jbaconsult.haushaltsbuch.kern;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Eine Buchung, wie sie aus einer Bankdatei gelesen wurde - vor jeder Deutung.
 *
 * <p>Die strukturierten Felder stehen <b>einzeln</b>, und das ist die zentrale Eigenschaft dieses
 * Typs. Sie werden nicht zu einem Textblob zusammengeklebt, auch nicht vorläufig.
 *
 * <p>Der Grund ist gemessen: {@code constraint.klassifikation-iban-mref} hält fest, dass eine
 * Namensheuristik in der Analyse zweimal vierstellige Posten verschluckt hat - eine monatliche
 * Darlehensrate, weil die Gegenpartei auf beide Kontoinhaber lautete, und eine Steuererstattung,
 * weil die Bank abgekürzt schreibt. Klassifiziert wird über IBAN, Mandatsreferenz und
 * Gläubigerkennung. Die Klassifikation ist nicht Teil dieses Bausteins; sie kann aber nur
 * funktionieren, wenn ihre Eingangsdaten den Import überleben.
 *
 * <p>Ebenso getrennt: Mandatsreferenz und Gläubigerkennung. Die Acquirer-Regel aus
 * {@code constraint.dauermandat-vs-pos} - eine Gläubigerkennung mit mehr als drei verschiedenen
 * Mandatsreferenzen ist ein Zahlungsdienstleister - ist andernfalls nicht berechenbar.
 *
 * @param bankreferenz Schlüssel der Deduplizierung (I4)
 * @param buchungstag Tag der Buchung. Im MT940 nur als MMTT geliefert und aus der Valuta abgeleitet
 * @param valuta Wertstellung
 * @param betrag negativ ist Abgang, positiv ist Zugang, aus Sicht des Kontos
 * @param storno ob die Bank die Buchung als Storno gekennzeichnet hat (MT940 RC/RD, CAMT RvslInd)
 */
public record Buchungszeile(
        String bankreferenz,
        LocalDate buchungstag,
        LocalDate valuta,
        Betrag betrag,
        boolean storno,
        String gegenparteiName,
        Iban gegenparteiIban,
        String mandatsreferenz,
        String glaeubigerkennung,
        String endeZuEndeReferenz,
        String verwendungszweck,
        String buchungstext) {

    public Buchungszeile {
        Objects.requireNonNull(bankreferenz, "bankreferenz darf nicht null sein");
        Objects.requireNonNull(buchungstag, "buchungstag darf nicht null sein");
        Objects.requireNonNull(valuta, "valuta darf nicht null sein");
        Objects.requireNonNull(betrag, "betrag darf nicht null sein");
        if (bankreferenz.isBlank()) {
            throw new IllegalArgumentException("bankreferenz darf nicht leer sein");
        }
    }

    public Optional<Iban> gegenpartei() {
        return Optional.ofNullable(gegenparteiIban);
    }

    /** Ob ein Lastschriftmandat erkennbar ist. Nicht, ob es ein <em>Dauer</em>mandat ist. */
    public boolean hatMandat() {
        return mandatsreferenz != null && !mandatsreferenz.isBlank();
    }
}
