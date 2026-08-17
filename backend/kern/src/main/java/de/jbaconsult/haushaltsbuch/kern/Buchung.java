package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;

/**
 * Eine gespeicherte Buchung.
 *
 * <p>Trägt {@link Buchungszeile} unverändert weiter, statt deren Felder zu wiederholen. Zwei
 * beinahe gleiche Datensätze wären eine Einladung, sie auseinanderlaufen zu lassen - und die
 * strukturierten Felder sind genau die, bei denen das teuer wird.
 *
 * @param bewegung die Geldbewegung, zu der diese Buchung eine Seite ist. Zwei Buchungen mit
 *     derselben Bewegung sind eine Umbuchung zwischen eigenen Konten und kein doppelter Aufwand
 */
public record Buchung(BuchungId id, KontoId konto, BewegungId bewegung, Buchungszeile zeile) {

    public Buchung {
        Objects.requireNonNull(id, "id darf nicht null sein");
        Objects.requireNonNull(konto, "konto darf nicht null sein");
        Objects.requireNonNull(bewegung, "bewegung darf nicht null sein");
        Objects.requireNonNull(zeile, "zeile darf nicht null sein");
    }

    public Betrag betrag() {
        return zeile.betrag();
    }
}
