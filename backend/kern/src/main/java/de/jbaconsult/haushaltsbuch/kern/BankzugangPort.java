package de.jbaconsult.haushaltsbuch.kern;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Speicherung der Bankzugänge und ihrer Konten.
 *
 * <p>Wie bei {@link KontoPort} gilt: die Methoden liefern ausschließlich, was der aktuelle Benutzer
 * sehen darf. Das ist keine Zusage der Implementierung, sondern eine Eigenschaft der Datenbank -
 * Row-Level-Security filtert unterhalb jeder Abfrage, und ohne Benutzerkontext kommt nichts zurück.
 */
public interface BankzugangPort {

    /** Legt einen Bankzugang an. */
    void anlegen(Bankzugang zugang);

    /** Schreibt einen geänderten Bankzugang zurück. */
    void aktualisieren(Bankzugang zugang);

    Optional<Bankzugang> findeZugang(BankzugangId id);

    List<Bankzugang> alleZugaenge();

    /**
     * Hinterlegt den Zustandswert eines laufenden Autorisierungsvorgangs.
     *
     * <p>Gebunden an Zugang <b>und</b> Benutzer, mit Ablauf und Verbrauchskennzeichen. Ohne diese
     * Bindung genügt ein untergeschobener Link, um im Namen eines Angemeldeten einen fremden
     * Bankzugang einzurichten.
     */
    void zustandHinterlegen(BankzugangId zugang, String zustand, BenutzerId benutzer, Instant gueltigBis);

    /**
     * Löst einen Zustandswert ein.
     *
     * <p>Einmalig: ein zweiter Aufruf mit demselben Wert liefert nichts mehr. Liefert ebenfalls
     * nichts, wenn der Wert unbekannt, abgelaufen oder einem anderen Benutzer zugeordnet ist. Alle
     * vier Fälle sind von außen nicht unterscheidbar, und das ist Absicht.
     */
    Optional<BankzugangId> zustandEinloesen(String zustand, BenutzerId benutzer, Instant jetzt);

    /**
     * Legt ein externes Konto an oder aktualisiert das vorhandene.
     *
     * <p>Erkannt wird es an {@link Kontokennung} - niemals an einer Sitzungskennung. Eine zweite
     * Autorisierung desselben Kontos darf keinen zweiten Datensatz erzeugen.
     */
    ExternesKontoId kontoUebernehmen(ExternesKonto konto);

    List<ExternesKonto> kontenDesZugangs(BankzugangId zugang);

    List<ExternesKonto> alleKonten();

    Optional<ExternesKonto> findeKonto(ExternesKontoId id);

    Optional<ExternesKonto> findeKontoNachKennung(Kontokennung kennung);

    /** Legt einen abgerufenen Saldo ab. Alte Werte bleiben stehen; sie sind gemessene Vergangenheit. */
    void saldoAblegen(ExternesKontoId konto, ExternerSaldo saldo);

    /** Alle Salden eines Kontos, neueste zuerst. */
    List<ExternerSaldo> saldenDesKontos(ExternesKontoId konto);

    /** Der zuletzt abgerufene Saldo je Art, neueste zuerst. */
    List<ExternerSaldo> letzteSalden(ExternesKontoId konto);
}
