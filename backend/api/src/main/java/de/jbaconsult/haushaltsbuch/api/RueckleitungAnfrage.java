package de.jbaconsult.haushaltsbuch.api;

/**
 * Was das Frontend nach der Rückleitung an das Backend gibt.
 *
 * <p>Entweder {@code code} - dann hat der Mensch autorisiert - oder {@code fehler} samt
 * Beschreibung. Beides zugleich kommt nicht vor; keines von beiden ist ein Fehler des Aufrufers.
 *
 * <p>Der Autorisierungscode wird ausschließlich <b>serverseitig</b> eingetauscht. Er erreicht
 * niemals einen Anbieteraufruf aus dem Browser heraus - dort wäre er in jedem Skript und jedem
 * Verlauf sichtbar.
 */
public record RueckleitungAnfrage(String zustand, String code, String fehler, String fehlerbeschreibung) {}
