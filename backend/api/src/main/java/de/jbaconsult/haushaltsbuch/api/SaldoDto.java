package de.jbaconsult.haushaltsbuch.api;

import java.time.LocalDate;

import de.jbaconsult.haushaltsbuch.kern.ExternerSaldo;

/**
 * Ein Saldo in der Darstellung für das Dashboard.
 *
 * <p>Der Abrufzeitpunkt geht immer mit. Ohne ihn ist die Zahl nicht einzuordnen - und gerade bei
 * einem Zugang, dessen Autorisierung abgelaufen ist, ist der Unterschied zwischen „von heute früh"
 * und „vom letzten Quartal" die ganze Aussage.
 */
public record SaldoDto(
        String art, String artOriginal, String betrag, String waehrung, String referenzdatum, String abgerufenAm) {

    public static SaldoDto von(ExternerSaldo saldo) {
        return new SaldoDto(
                saldo.art().name(),
                saldo.artOriginal(),
                saldo.betrag().wert().toPlainString(),
                saldo.waehrung(),
                saldo.referenzdatum().map(LocalDate::toString).orElse(null),
                saldo.abgerufenAm().toString());
    }
}
