package de.jbaconsult.haushaltsbuch.kern;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Konto, wie es der Bankanbieter kennt.
 *
 * <p>Liegt <b>neben</b> dem fachlichen {@link Konto} dieses Systems, nicht darin. Die Zuordnung ist
 * optional und wird von Hand gesetzt; sie zu raten würde später Buchungen auf falsche Konten
 * verteilen.
 *
 * <p>Der Schlüssel ist {@link Kontokennung} und niemals eine Sitzungskennung. Siehe die Begründung
 * dort.
 *
 * <p>Der Zugangsbezug ist <b>optional</b>. Wird ein Bankzugang entfernt, ohne seine Konten
 * mitzunehmen, bleiben sie als gemessene Vergangenheit stehen und stehen danach für sich. Ein
 * Pflichtbezug hätte an dieser Stelle nur zwei Auswege gelassen: die Konten mitzulöschen oder auf
 * einen Zugang zu zeigen, den es nicht mehr gibt.
 *
 * @param id Kennung innerhalb dieses Systems
 * @param bankzugang zu welchem Zugang dieses Konto gehört; leer, wenn der Zugang entfernt wurde
 * @param kennung stabiler Schlüssel über Sitzungen hinweg
 * @param iban IBAN, sofern der Anbieter sie liefert
 * @param waehrung Währung des Kontos als ISO-Code
 * @param kontoart Art laut Anbieter, unverändert übernommen
 * @param produktname Produktbezeichnung des Instituts
 * @param bezeichnung Name für die Oberfläche
 * @param zugeordnetesKonto fachliches Konto dieses Systems, falls von Hand zugeordnet
 */
public record ExternesKonto(
        ExternesKontoId id,
        Optional<BankzugangId> bankzugang,
        Kontokennung kennung,
        Optional<Iban> iban,
        String waehrung,
        Optional<String> kontoart,
        Optional<String> produktname,
        String bezeichnung,
        Optional<KontoId> zugeordnetesKonto) {

    public ExternesKonto {
        Objects.requireNonNull(id, "id darf nicht null sein");
        Objects.requireNonNull(bankzugang, "bankzugang darf nicht null sein - Optional.empty() statt null");
        Objects.requireNonNull(kennung, "kennung darf nicht null sein");
        Objects.requireNonNull(waehrung, "waehrung darf nicht null sein");
        Objects.requireNonNull(bezeichnung, "bezeichnung darf nicht null sein");
        Objects.requireNonNull(iban, "iban darf nicht null sein - Optional.empty() statt null");
        Objects.requireNonNull(kontoart, "kontoart darf nicht null sein - Optional.empty() statt null");
        Objects.requireNonNull(produktname, "produktname darf nicht null sein - Optional.empty() statt null");
        Objects.requireNonNull(
                zugeordnetesKonto, "zugeordnetesKonto darf nicht null sein - Optional.empty() statt null");
    }
}
