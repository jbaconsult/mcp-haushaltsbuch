package de.jbaconsult.haushaltsbuch.kern;

/**
 * Die fünf Pflichtinvarianten eines Kontodaten-Imports.
 *
 * <p>Grundlage ist {@code constraint.import-saldenvalidierung}: ein Import, der sich nicht selbst
 * validiert, gilt als nicht erfolgt. Was nicht aufgeht, landet in einer Fehlerliste - nicht im
 * Datenbestand.
 *
 * <p>Die Kennungen sind Teil der Schnittstelle, nicht Dokumentation. Ein Fehlereintrag nennt die
 * verletzte Invariante beim Namen, damit aus „Import fehlgeschlagen" eine Aussage wird, mit der
 * jemand etwas anfangen kann.
 */
public enum Invariante {

    /** Anfangssaldo plus Summe der Buchungen gleich Endsaldo, je Auszug beziehungsweise Report. */
    I1("Anfangssaldo plus Summe der Buchungen gleich Endsaldo"),

    /** Endsaldo Block N gleich Anfangssaldo Block N+1, je Konto. */
    I2("Endsaldo eines Blocks gleich Anfangssaldo des naechsten"),

    /** Jede Buchung hat ihren Detailblock. */
    I3("Jede Buchung hat ihren Detailblock"),

    /**
     * Deduplizierung über die Bankreferenz.
     *
     * <p>Exportzeiträume überlappen sich an den Randtagen. Ohne diese Invariante entstehen
     * Doubletten, und zwar lautlos.
     */
    I4("Deduplizierung ueber die Bankreferenz"),

    /**
     * IBAN-Prüfsumme.
     *
     * <p>MT940-Zeilen brechen bei etwa 65 Zeichen um, gern mitten in einer IBAN. Eine falsch
     * zusammengesetzte IBAN sieht aus wie eine IBAN; nur die Prüfsumme merkt es.
     */
    I5("IBAN-Pruefsumme");

    private final String beschreibung;

    Invariante(String beschreibung) {
        this.beschreibung = beschreibung;
    }

    public String beschreibung() {
        return beschreibung;
    }
}
