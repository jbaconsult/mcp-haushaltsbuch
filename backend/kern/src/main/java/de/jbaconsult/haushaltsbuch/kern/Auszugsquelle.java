package de.jbaconsult.haushaltsbuch.kern;

/** Format, aus dem ein Auszug gelesen wurde. Wird am Auszug festgehalten, weil sich Befunde sonst nicht zuordnen lassen. */
public enum Auszugsquelle {

    /** SWIFT MT940, das klassische Kontoauszugsformat der deutschen Banken. */
    MT940,

    /** ISO 20022 CAMT.052 - Kontoumsätze innerhalb des Tages. */
    CAMT052
}
