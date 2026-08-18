package de.jbaconsult.haushaltsbuch.api;

import java.util.List;

import de.jbaconsult.haushaltsbuch.kern.ExternerSaldo;
import de.jbaconsult.haushaltsbuch.kern.ExternesKonto;
import de.jbaconsult.haushaltsbuch.kern.Iban;
import de.jbaconsult.haushaltsbuch.kern.KontoId;

/**
 * Externes Konto in der Darstellung für das Dashboard.
 *
 * <p>Die Kennung ist die stabile Kennung des Anbieters, nicht die Sitzungskennung. Letztere
 * existiert in diesem System nur innerhalb eines Vorgangs und erscheint in keiner Antwort.
 */
public record ExternesKontoDto(
        String id,
        String kennung,
        String bezeichnung,
        String iban,
        String waehrung,
        String kontoart,
        String produktname,
        String bankzugangId,
        String zugeordnetesKonto,
        List<SaldoDto> salden) {

    public static ExternesKontoDto von(ExternesKonto konto, List<ExternerSaldo> salden) {
        return new ExternesKontoDto(
                konto.id().toString(),
                konto.kennung().wert(),
                konto.bezeichnung(),
                konto.iban().map(Iban::toString).orElse(null),
                konto.waehrung(),
                konto.kontoart().orElse(null),
                konto.produktname().orElse(null),
                konto.bankzugang().toString(),
                konto.zugeordnetesKonto().map(KontoId::toString).orElse(null),
                salden.stream().map(SaldoDto::von).toList());
    }
}
