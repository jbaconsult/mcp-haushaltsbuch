package de.jbaconsult.haushaltsbuch.kern;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein Saldo, wie ihn der Bankanbieter geliefert hat.
 *
 * <p>Der Abrufzeitpunkt gehört zum Wert. Ein Saldo ohne ihn ist eine Zahl ohne Aussage - man weiß
 * nicht, ob sie von heute früh oder aus dem letzten Quartal stammt. Genau daran hängt auch, dass
 * gespeicherte Salden einen Fehlschlag überleben dürfen: sie werden nicht falsch, sie werden alt,
 * und das ist sichtbar.
 *
 * <p>Die Währung steht neben dem Betrag, weil {@link Betrag} den Zahlenwert führt und Euro
 * annimmt. Externe Konten können auf andere Währungen lauten; die Zahl stimmt dann trotzdem, nur
 * ihre Einheit ist eine andere.
 *
 * @param art Art des Saldos laut Anbieter
 * @param artOriginal der unveränderte Code des Anbieters, auch bei bekannter Art
 * @param betrag Zahlenwert mit zwei Nachkommastellen
 * @param waehrung ISO-Code der Währung
 * @param referenzdatum Stichtag, auf den sich der Saldo bezieht
 * @param abgerufenAm wann dieser Wert geholt wurde
 */
public record ExternerSaldo(
        Saldenart art,
        String artOriginal,
        Betrag betrag,
        String waehrung,
        Optional<LocalDate> referenzdatum,
        Instant abgerufenAm) {

    public ExternerSaldo {
        Objects.requireNonNull(art, "art darf nicht null sein");
        Objects.requireNonNull(betrag, "betrag darf nicht null sein");
        Objects.requireNonNull(waehrung, "waehrung darf nicht null sein");
        Objects.requireNonNull(abgerufenAm, "abgerufenAm darf nicht null sein");
        artOriginal = artOriginal == null ? art.name() : artOriginal;
        referenzdatum = referenzdatum == null ? Optional.empty() : referenzdatum;
    }
}
