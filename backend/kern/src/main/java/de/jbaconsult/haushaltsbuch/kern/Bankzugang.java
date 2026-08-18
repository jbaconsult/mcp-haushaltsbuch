package de.jbaconsult.haushaltsbuch.kern;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein autorisierter Zugang zu einem Institut.
 *
 * <p>Trägt den Status seines Vorgangs und den Zeitpunkt, an dem die Autorisierung verfällt. Beides
 * gehört ins Modell und nicht in eine Randnotiz: ohne Ablaufzeitpunkt merkt niemand, dass ein
 * Zugang stirbt, und ohne Status ist ein abgebrochener Vorgang von einem funktionierenden nicht zu
 * unterscheiden.
 *
 * @param id Kennung dieses Zugangs
 * @param anbieter welcher Bankanbieter diesen Zugang vermittelt
 * @param institut das Institut, gegen das autorisiert wurde
 * @param institutsname Anzeigename des Instituts zum Zeitpunkt der Einrichtung
 * @param status Zustand des Vorgangs
 * @param gueltigBis Zeitpunkt, an dem die Autorisierung verfällt; leer, solange nicht autorisiert
 * @param sitzung Kennung der Anbietersitzung; leer, solange nicht autorisiert
 * @param fehlermeldung Meldung des Anbieters, falls der Vorgang scheiterte
 * @param angelegtVon Benutzer, der den Zugang eingerichtet hat
 * @param angelegtAm Zeitpunkt der Einrichtung
 */
public record Bankzugang(
        BankzugangId id,
        String anbieter,
        InstitutKennung institut,
        String institutsname,
        Bankzugangstatus status,
        Optional<Instant> gueltigBis,
        Optional<Sitzungskennung> sitzung,
        Optional<String> fehlermeldung,
        BenutzerId angelegtVon,
        Instant angelegtAm) {

    public Bankzugang {
        Objects.requireNonNull(id, "id darf nicht null sein");
        Objects.requireNonNull(anbieter, "anbieter darf nicht null sein");
        Objects.requireNonNull(institut, "institut darf nicht null sein");
        Objects.requireNonNull(status, "status darf nicht null sein");
        Objects.requireNonNull(angelegtVon, "angelegtVon darf nicht null sein");
        Objects.requireNonNull(angelegtAm, "angelegtAm darf nicht null sein");
        Objects.requireNonNull(gueltigBis, "gueltigBis darf nicht null sein - Optional.empty() statt null");
        Objects.requireNonNull(sitzung, "sitzung darf nicht null sein - Optional.empty() statt null");
        Objects.requireNonNull(fehlermeldung, "fehlermeldung darf nicht null sein - Optional.empty() statt null");
        institutsname = institutsname == null ? institut.name() : institutsname;
    }

    /**
     * Ob der Zugang zum genannten Zeitpunkt noch nutzbar ist.
     *
     * <p>Prüft Status <b>und</b> Ablauf. Der Status allein genügt nicht: zwischen dem Verfall einer
     * Autorisierung und dem Zeitpunkt, an dem jemand den Status nachführt, liegt Zeit, und in
     * dieser Zeit stünde sonst {@code AUTORISIERT} an einem toten Zugang.
     */
    public boolean istNutzbar(Instant jetzt) {
        return status.erlaubtAbruf()
                && gueltigBis.map(ende -> ende.isAfter(jetzt)).orElse(false);
    }

    /** Verbleibende Gültigkeit, leer wenn nicht autorisiert oder bereits verfallen. */
    public Optional<Duration> restgueltigkeit(Instant jetzt) {
        return gueltigBis.filter(ende -> ende.isAfter(jetzt)).map(ende -> Duration.between(jetzt, ende));
    }

    /**
     * Derselbe Zugang mit nachgeführtem Status, falls die Gültigkeit verstrichen ist.
     *
     * <p>Verändert nichts anderes. Insbesondere bleiben Konten und Salden erhalten - sie sind
     * gemessene Vergangenheit und werden nicht dadurch falsch, dass die Autorisierung ausläuft.
     */
    public Bankzugang mitAblaufGeprueft(Instant jetzt) {
        if (status != Bankzugangstatus.AUTORISIERT) {
            return this;
        }
        boolean verfallen = gueltigBis.map(ende -> !ende.isAfter(jetzt)).orElse(true);
        return verfallen ? mitStatus(Bankzugangstatus.ABGELAUFEN) : this;
    }

    public Bankzugang mitStatus(Bankzugangstatus neuerStatus) {
        return new Bankzugang(
                id,
                anbieter,
                institut,
                institutsname,
                neuerStatus,
                gueltigBis,
                sitzung,
                fehlermeldung,
                angelegtVon,
                angelegtAm);
    }

    /** Nach erfolgreicher Autorisierung: Sitzung und Ablauf sind bekannt. */
    public Bankzugang autorisiert(Sitzungskennung neueSitzung, Instant ablauf) {
        return new Bankzugang(
                id,
                anbieter,
                institut,
                institutsname,
                Bankzugangstatus.AUTORISIERT,
                Optional.of(ablauf),
                Optional.of(neueSitzung),
                Optional.empty(),
                angelegtVon,
                angelegtAm);
    }

    /** Nach einem Fehlschlag: die Meldung des Anbieters bleibt sichtbar. */
    public Bankzugang fehlgeschlagen(String meldung) {
        return new Bankzugang(
                id,
                anbieter,
                institut,
                institutsname,
                Bankzugangstatus.FEHLGESCHLAGEN,
                gueltigBis,
                sitzung,
                Optional.ofNullable(meldung),
                angelegtVon,
                angelegtAm);
    }
}
