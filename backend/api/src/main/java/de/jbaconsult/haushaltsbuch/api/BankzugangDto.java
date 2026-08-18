package de.jbaconsult.haushaltsbuch.api;

import java.time.Duration;
import java.time.Instant;

import de.jbaconsult.haushaltsbuch.kern.Bankzugang;

/**
 * Bankzugang in der Darstellung für das Dashboard.
 *
 * <p>Enthält bewusst <b>nicht</b> die Sitzungskennung und nicht den Zustandswert des laufenden
 * Autorisierungsvorgangs. Beides sind Betriebsmittel; im Browser haben sie nichts zu suchen, und
 * einmal ausgeliefert stehen sie in jedem Zwischenspeicher.
 *
 * <p>Die verbleibende Gültigkeit wird als Tageszahl mitgegeben, statt sie im Frontend aus zwei
 * Zeitstempeln zu rechnen. Ein Zugang, der in zwei Wochen abläuft, ohne dass es jemand sieht, ist
 * ein Ausfall mit Ankündigung - und die Anzeige soll nicht daran scheitern, dass zwei Uhren
 * auseinanderlaufen.
 */
public record BankzugangDto(
        String id,
        String anbieter,
        String institut,
        String status,
        String gueltigBis,
        Long restgueltigkeitTage,
        String fehlermeldung) {

    public static BankzugangDto von(Bankzugang zugang, Instant jetzt) {
        return new BankzugangDto(
                zugang.id().toString(),
                zugang.anbieter(),
                zugang.institutsname(),
                zugang.status().name(),
                zugang.gueltigBis().map(Instant::toString).orElse(null),
                zugang.restgueltigkeit(jetzt).map(Duration::toDays).orElse(null),
                zugang.fehlermeldung().orElse(null));
    }
}
