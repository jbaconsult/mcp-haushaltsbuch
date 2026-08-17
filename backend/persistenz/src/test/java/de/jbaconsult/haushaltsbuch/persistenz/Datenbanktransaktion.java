package de.jbaconsult.haushaltsbuch.persistenz;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import io.agroal.api.AgroalDataSource;

/**
 * Eine Transaktion direkt an der Datenbank, unter der Anwendungsrolle und mit gesetztem
 * Benutzerkontext.
 *
 * <p>Warum an Hibernate vorbei: die Zusagen, um die es in diesen Tests geht - Policies, Splitsumme,
 * Zweiseitigkeit von Bewegungen - stehen in der Datenbank. Ein Test, der sie über den
 * Persistenzrahmen prüft, prüft auch den Rahmen mit und lässt offen, ob die Zusage der Datenbank
 * gilt oder nur dem Weg dorthin. Genau diese Unterscheidung ist der Punkt: eine Migration, die an
 * der Anwendung vorbei läuft, sieht Hibernate nie.
 *
 * <p>Die Constraint-Trigger sind aufgeschoben und schlagen erst beim {@link #bestaetigen()} zu -
 * das ist notwendig, weil eine Buchung zwischen ihrem eigenen Einfügen und dem ihrer Splits
 * zwingend unausgeglichen ist. Wer hier eine Ausnahme erwartet, muss sie am Bestätigen erwarten,
 * nicht am Einfügen.
 */
final class Datenbanktransaktion implements AutoCloseable {

    private final Connection verbindung;

    /**
     * @param benutzer der Benutzerkontext. {@code null} steht für „nicht gesetzt" - dann liefern die
     *     Policies nichts, und genau das ist der Fail-Closed-Nachweis
     */
    Datenbanktransaktion(AgroalDataSource quelle, UUID benutzer) throws SQLException {
        verbindung = quelle.getConnection();
        verbindung.setAutoCommit(false);

        // Der Rollenwechsel ist nicht optional: Dev Services baut die Verbindung als Superuser auf,
        // und ein Superuser umgeht Row-Level-Security immer - auch FORCE hilft dagegen nicht.
        try (Statement anweisung = verbindung.createStatement()) {
            anweisung.execute("SET LOCAL ROLE haushaltsbuch_app");
        }
        try (PreparedStatement anweisung =
                verbindung.prepareStatement("SELECT set_config('app.benutzer_id', ?, true)")) {
            anweisung.setString(1, benutzer == null ? "" : benutzer.toString());
            anweisung.execute();
        }
    }

    void aus(String sql, Object... werte) throws SQLException {
        try (PreparedStatement anweisung = verbindung.prepareStatement(sql)) {
            for (int i = 0; i < werte.length; i++) {
                anweisung.setObject(i + 1, werte[i]);
            }
            anweisung.executeUpdate();
        }
    }

    long zaehle(String sql, Object... werte) throws SQLException {
        try (PreparedStatement anweisung = verbindung.prepareStatement(sql)) {
            for (int i = 0; i < werte.length; i++) {
                anweisung.setObject(i + 1, werte[i]);
            }
            try (ResultSet ergebnis = anweisung.executeQuery()) {
                ergebnis.next();
                return ergebnis.getLong(1);
            }
        }
    }

    void bestaetigen() throws SQLException {
        verbindung.commit();
    }

    @Override
    public void close() throws SQLException {
        try {
            if (!verbindung.getAutoCommit()) {
                verbindung.rollback();
            }
        } finally {
            verbindung.close();
        }
    }
}
