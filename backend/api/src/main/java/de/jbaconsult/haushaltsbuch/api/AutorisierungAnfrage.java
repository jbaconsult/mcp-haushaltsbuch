package de.jbaconsult.haushaltsbuch.api;

/**
 * Womit eine Autorisierung gestartet wird.
 *
 * <p>Nur das Institut. Die Rückleitungsadresse kommt aus der Konfiguration und nicht vom Aufrufer:
 * eine vom Browser bestimmte Adresse wäre eine offene Weiterleitung, über die sich ein
 * Autorisierungscode an einen fremden Ort schicken liesse.
 */
public record AutorisierungAnfrage(String institutName, String institutLand) {}
