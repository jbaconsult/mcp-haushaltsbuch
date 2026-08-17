package de.jbaconsult.haushaltsbuch.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prüft, dass keine Tabelle ohne Zugriffskontrolle existiert.
 *
 * <p>Die Regel „eine Migration, die eine Tabelle mit Kontobezug anlegt, setzt im selben Schritt die
 * Policy" steht in {@code CLAUDE.md}. Eine Konvention, die nur in einem Dokument steht, wird
 * irgendwann übersehen - und eine vergessene Policy schlägt nicht fehl, sie liefert einfach mehr
 * Zeilen. Deshalb prüft dieser Test sie maschinell.
 *
 * <p>Der Test wird mit dem Schema wachsen. Kommen Buchungen und Töpfe dazu, wird er ohne
 * Anpassung rot, bis deren Policies stehen. Das ist der Zweck.
 */
@QuarkusTest
class RlsPolicyVollstaendigkeitTest {

    /**
     * Tabellen, die bewusst ohne Zugriffskontrolle auskommen.
     *
     * <p>Jeder Eintrag hier ist eine Entscheidung, die begründet sein muss - nicht ein Ort, an dem
     * man unbequeme Tabellen ablegt.
     *
     * <ul>
     *   <li>{@code benutzeridentitaet} - wird gelesen, bevor der Benutzerkontext feststeht, und
     *       enthält ausschließlich zwei opake Kennungen. Begründung in {@code V1__grundschema.sql}.
     *   <li>{@code flyway_schema_history} - gehört Flyway, nicht der Fachlichkeit.
     * </ul>
     */
    private static final Set<String> BEWUSST_OHNE_RLS = Set.of("benutzeridentitaet", "flyway_schema_history");

    @Inject
    AgroalDataSource datenquelle;

    @Test
    @DisplayName("jede fachliche Tabelle hat RLS aktiviert und erzwungen")
    void jedeTabelleHatRlsAktiviertUndErzwungen() throws SQLException {
        List<String> ohneRls = new ArrayList<>();
        List<String> ohneForce = new ArrayList<>();

        // relrowsecurity = ENABLE, relforcerowsecurity = FORCE. Beide sind noetig: ENABLE allein
        // nimmt den Tabelleneigentuemer von saemtlichen Policies aus.
        String abfrage = """
                SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public' AND c.relkind = 'r'
                 ORDER BY c.relname
                """;

        try (Connection verbindung = datenquelle.getConnection();
                Statement anweisung = verbindung.createStatement();
                ResultSet ergebnis = anweisung.executeQuery(abfrage)) {

            while (ergebnis.next()) {
                String tabelle = ergebnis.getString("relname");
                if (BEWUSST_OHNE_RLS.contains(tabelle)) {
                    continue;
                }
                if (!ergebnis.getBoolean("relrowsecurity")) {
                    ohneRls.add(tabelle);
                }
                if (!ergebnis.getBoolean("relforcerowsecurity")) {
                    ohneForce.add(tabelle);
                }
            }
        }

        assertThat(ohneRls)
                .as("Tabellen ohne ENABLE ROW LEVEL SECURITY - die Zugriffskontrolle fehlt dort ganz")
                .isEmpty();
        assertThat(ohneForce)
                .as("Tabellen ohne FORCE ROW LEVEL SECURITY - der Eigentuemer umgeht dort alle Policies")
                .isEmpty();
    }

    @Test
    @DisplayName("jede Tabelle mit aktivierter RLS hat auch mindestens eine Policy")
    void jedeTabelleMitRlsHatPolicy() throws SQLException {
        List<String> ohnePolicy = new ArrayList<>();

        // RLS ohne Policy ist nicht harmlos, sondern das Gegenteil des Gemeinten: Postgres
        // verweigert dann JEDEN Zugriff. Das faellt zwar auf - aber erst zur Laufzeit.
        String abfrage = """
                SELECT c.relname
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public'
                   AND c.relkind = 'r'
                   AND c.relrowsecurity
                   AND NOT EXISTS (SELECT 1 FROM pg_policies p
                                    WHERE p.schemaname = 'public' AND p.tablename = c.relname)
                 ORDER BY c.relname
                """;

        try (Connection verbindung = datenquelle.getConnection();
                Statement anweisung = verbindung.createStatement();
                ResultSet ergebnis = anweisung.executeQuery(abfrage)) {

            while (ergebnis.next()) {
                ohnePolicy.add(ergebnis.getString("relname"));
            }
        }

        assertThat(ohnePolicy)
                .as("Tabellen mit RLS, aber ohne Policy - dort ist gar kein Zugriff mehr moeglich")
                .isEmpty();
    }
}
