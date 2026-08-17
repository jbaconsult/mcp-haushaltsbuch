package de.jbaconsult.haushaltsbuch.persistenz;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Lädt eine Testdatei vom Klassenpfad.
 *
 * <p>Alle Fixtures sind synthetisch. Keine echten Kontonummern, Beträge, Mandatsreferenzen,
 * Gläubigerkennungen, Verwendungszwecke oder Namen.
 */
final class Fixture {

    private Fixture() {}

    static String lies(String pfad) {
        try (InputStream strom = Fixture.class.getClassLoader().getResourceAsStream(pfad)) {
            if (strom == null) {
                throw new IllegalArgumentException("Fixture nicht gefunden: " + pfad);
            }
            return new String(strom.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
