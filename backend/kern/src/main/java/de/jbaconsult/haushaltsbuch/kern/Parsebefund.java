package de.jbaconsult.haushaltsbuch.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Was beim Lesen einer Bankdatei herauskam: Auszüge und Befunde, beides zugleich.
 *
 * <p>Ein Parser, der bei der ersten Unstimmigkeit eine Ausnahme wirft, beantwortet die falsche
 * Frage. Interessant ist nicht, <em>dass</em> etwas nicht stimmt, sondern <em>was alles</em> - sonst
 * arbeitet man sich Fehler für Fehler durch dieselbe Datei.
 */
public record Parsebefund(List<Kontoauszug> auszuege, List<Importfehler> fehler) {

    public Parsebefund {
        auszuege = List.copyOf(Objects.requireNonNull(auszuege, "auszuege darf nicht null sein"));
        fehler = List.copyOf(Objects.requireNonNull(fehler, "fehler darf nicht null sein"));
    }

    public static Parsebefund von(List<Kontoauszug> auszuege, List<Importfehler> fehler) {
        return new Parsebefund(auszuege, fehler);
    }

    public boolean istSauber() {
        return fehler.isEmpty();
    }

    /** Befunde dieses Parsebefunds plus weitere. Für die Prüfschritte, die nach dem Lesen kommen. */
    public Parsebefund mitZusaetzlichenFehlern(List<Importfehler> weitere) {
        List<Importfehler> alle = new ArrayList<>(fehler);
        alle.addAll(weitere);
        return new Parsebefund(auszuege, alle);
    }
}
