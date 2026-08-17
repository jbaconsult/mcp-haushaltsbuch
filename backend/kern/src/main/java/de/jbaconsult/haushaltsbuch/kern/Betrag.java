package de.jbaconsult.haushaltsbuch.kern;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Ein Geldbetrag in Euro.
 *
 * <p>Immer {@link BigDecimal} mit Skalierung 2 und {@link RoundingMode#HALF_UP}, niemals
 * {@code double} oder {@code float}. Der Typ existiert, damit die Skalierung an einer Stelle richtig
 * gemacht wird statt an jeder Rechenstelle einzeln - was bedeuten würde, dass sie irgendwo einzeln
 * falsch gemacht wird.
 *
 * <p>In der Datenbank entspricht dem {@code numeric(14,2)}.
 */
public record Betrag(BigDecimal wert) implements Comparable<Betrag> {

    public static final int SKALIERUNG = 2;
    public static final RoundingMode RUNDUNG = RoundingMode.HALF_UP;

    public static final Betrag NULL_BETRAG = new Betrag(BigDecimal.ZERO);

    public Betrag {
        Objects.requireNonNull(wert, "wert darf nicht null sein");
        wert = wert.setScale(SKALIERUNG, RUNDUNG);
    }

    public static Betrag von(String wert) {
        return new Betrag(new BigDecimal(wert));
    }

    public static Betrag von(long euro, int cent) {
        if (cent < 0 || cent > 99) {
            throw new IllegalArgumentException("cent muss zwischen 0 und 99 liegen: " + cent);
        }
        BigDecimal betrag = BigDecimal.valueOf(euro).add(BigDecimal.valueOf(cent, SKALIERUNG));
        return new Betrag(betrag);
    }

    public Betrag plus(Betrag anderer) {
        return new Betrag(wert.add(anderer.wert));
    }

    public Betrag minus(Betrag anderer) {
        return new Betrag(wert.subtract(anderer.wert));
    }

    public Betrag negiert() {
        return new Betrag(wert.negate());
    }

    public boolean istNegativ() {
        return wert.signum() < 0;
    }

    public boolean istNull() {
        return wert.signum() == 0;
    }

    public boolean istPositiv() {
        return wert.signum() > 0;
    }

    @Override
    public int compareTo(Betrag anderer) {
        return wert.compareTo(anderer.wert);
    }

    /**
     * Vergleich nach Wert.
     *
     * <p>{@code BigDecimal.equals} vergleicht auch die Skalierung, weshalb {@code 1.0} und
     * {@code 1.00} dort ungleich sind. Weil der Konstruktor auf Skalierung 2 normalisiert, kann das
     * hier nicht auftreten - der explizite Vergleich per {@code compareTo} stellt sicher, dass es
     * auch dann nicht auftritt, wenn die Normalisierung einmal geändert wird.
     */
    @Override
    public boolean equals(Object anderes) {
        if (this == anderes) {
            return true;
        }
        return anderes instanceof Betrag anderer && wert.compareTo(anderer.wert) == 0;
    }

    @Override
    public int hashCode() {
        return wert.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return wert.toPlainString() + " EUR";
    }
}
