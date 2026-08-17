package de.jbaconsult.haushaltsbuch.persistenz;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Kennungen aus dem Demo-Datensatz und die immer gleichen Einfügungen.
 *
 * <p>Alle Werte sind synthetisch und stammen aus {@code V900__demodaten.sql} beziehungsweise
 * {@code V901__ledger_demodaten.sql}. Es gibt keinen Zeitpunkt, zu dem hier eine echte Kontonummer,
 * ein echter Betrag oder eine echte Mandatsreferenz stehen darf - das Zielbild ist Open Source, und
 * ein einmal committeter Wert bleibt auch nach {@code git rm} in der Historie.
 */
final class Ledgerdaten {

    static final UUID DEMO_EINS = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID DEMO_ZWEI = UUID.fromString("00000000-0000-0000-0000-000000000002");

    static final UUID HAUSHALT_GEMEINSAM = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID GIRO_DEMO_EINS = UUID.fromString("10000000-0000-0000-0000-000000000002");
    static final UUID GESCHAEFT_DEMO_EINS = UUID.fromString("10000000-0000-0000-0000-000000000003");
    static final UUID RUECKLAGE = UUID.fromString("10000000-0000-0000-0000-000000000004");
    static final UUID GIRO_DEMO_ZWEI = UUID.fromString("10000000-0000-0000-0000-000000000005");

    static final UUID KATEGORIE_LEBENSMITTEL = UUID.fromString("30000000-0000-0000-0000-000000000001");

    private Ledgerdaten() {}

    /** Eine Bewegung mit genau einer Seite - der Normalfall nach dem Import. */
    static UUID legeBuchung(
            Datenbanktransaktion transaktion, UUID bewegung, UUID konto, String betrag, String bankreferenz)
            throws SQLException {

        transaktion.aus("INSERT INTO bewegung (id) VALUES (?) ON CONFLICT DO NOTHING", bewegung);

        UUID buchung = UUID.randomUUID();
        transaktion.aus(
                """
                INSERT INTO buchung (id, bewegung_id, konto_id, buchungstag, valuta, betrag, bankreferenz)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                buchung,
                bewegung,
                konto,
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 17),
                new BigDecimal(betrag),
                bankreferenz);
        return buchung;
    }

    static void legeSplit(Datenbanktransaktion transaktion, UUID buchung, String betrag, UUID kategorie)
            throws SQLException {
        transaktion.aus(
                "INSERT INTO buchungssplit (id, buchung_id, kategorie_id, betrag) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                buchung,
                kategorie,
                new BigDecimal(betrag));
    }

    /** Eindeutige Bankreferenz je Testlauf. Der Bestand bleibt zwischen den Tests einer Klasse stehen. */
    static String referenz(String zweck) {
        return zweck + "-" + UUID.randomUUID();
    }
}
