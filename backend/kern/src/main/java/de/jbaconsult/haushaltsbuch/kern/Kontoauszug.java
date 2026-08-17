package de.jbaconsult.haushaltsbuch.kern;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein Auszug beziehungsweise CAMT-Report, wie er aus einer Datei gelesen wurde.
 *
 * <p>Anfangs- und Endsaldo stehen so, wie die Bank sie geliefert hat. Sie werden <b>nicht</b> aus
 * den Buchungen berechnet - genau der Vergleich zwischen Geliefertem und Gerechnetem ist die
 * Prüfung I1. Ein Auszug, dessen Salden man aus seinen eigenen Buchungen ableitet, validiert nichts,
 * sondern bestätigt sich selbst.
 *
 * @param kontoIban Konto, auf das der Auszug lautet. Leer, wenn die Datei nur eine Kontonummer alter
 *     Bauart trägt - dann ist die Zuordnung Sache des Aufrufers
 * @param auszugsnummer Nummer des Auszugs, wie von der Bank vergeben
 * @param von Datum des Anfangssaldos
 * @param bis Datum des Endsaldos
 */
public record Kontoauszug(
        Auszugsquelle quelle,
        Iban kontoIban,
        String auszugsnummer,
        Betrag anfangssaldo,
        Betrag endsaldo,
        LocalDate von,
        LocalDate bis,
        List<Buchungszeile> zeilen) {

    public Kontoauszug {
        Objects.requireNonNull(quelle, "quelle darf nicht null sein");
        Objects.requireNonNull(anfangssaldo, "anfangssaldo darf nicht null sein");
        Objects.requireNonNull(endsaldo, "endsaldo darf nicht null sein");
        Objects.requireNonNull(von, "von darf nicht null sein");
        Objects.requireNonNull(bis, "bis darf nicht null sein");
        if (auszugsnummer == null || auszugsnummer.isBlank()) {
            throw new IllegalArgumentException("auszugsnummer darf nicht leer sein");
        }
        zeilen = List.copyOf(Objects.requireNonNull(zeilen, "zeilen darf nicht null sein"));
    }

    public Optional<Iban> konto() {
        return Optional.ofNullable(kontoIban);
    }

    /** Summe der Buchungen dieses Auszugs. Die eine Hälfte von I1; die andere ist der Endsaldo. */
    public Betrag summeDerBuchungen() {
        return zeilen.stream().map(Buchungszeile::betrag).reduce(Betrag.NULL_BETRAG, Betrag::plus);
    }

    /**
     * Bezeichnung für Fehlermeldungen.
     *
     * <p>Zugleich der Schlüssel, über den der Importdienst einen Befund seinem Auszug zuordnet -
     * deshalb muss sie exakt so lauten wie die Bezeichnung, die die Parser beim Lesen vergeben. Zwei
     * Blöcke mit derselben Auszugsnummer in einer Datei teilen sich damit ihre Befunde und werden
     * gemeinsam abgelehnt. Das ist die vorsichtige Richtung: lieber ein Auszug zu viel abgelehnt als
     * einer zu wenig.
     */
    public String bezeichnung() {
        return (quelle == Auszugsquelle.CAMT052 ? "Report " : "Auszug ") + auszugsnummer;
    }

    public String zeitraum() {
        return von + " bis " + bis;
    }
}
