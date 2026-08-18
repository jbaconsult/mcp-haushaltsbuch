/**
 * Bankzugänge und die von der Bank gemeldeten Konten.
 *
 * Die Typen bilden ab, was das Backend liefert - bewusst als eigene Deklaration
 * und nicht generiert: eine Änderung am Backend soll hier sichtbar auffallen und
 * nicht stillschweigend durchgereicht werden.
 *
 * Diese Datei ist frei von serverseitigen Abhängigkeiten und darf deshalb auch aus
 * einer Client Component heraus benutzt werden. Der serverseitige Abruf steht in
 * `bank-server.ts`: er liest über `next/headers` die Sitzung, und ein Import davon
 * aus dem Browser heraus lässt den Build scheitern - zu Recht, denn er zöge die
 * Sitzungsverwaltung des BFF in das Bündel, das der Browser bekommt.
 */

export type Bankzugangstatus =
  | "NICHT_AUTORISIERT"
  | "AUTORISIERUNG_LAEUFT"
  | "AUTORISIERT"
  | "ABGELAUFEN"
  | "FEHLGESCHLAGEN";

export type Bankzugang = {
  id: string;
  anbieter: string;
  institut: string;
  status: Bankzugangstatus;
  gueltigBis: string | null;
  restgueltigkeitTage: number | null;
  fehlermeldung: string | null;
};

export type Saldo = {
  art: "GEBUCHT" | "VERFUEGBAR" | "VORGEMERKT" | "ABSCHLUSS" | "SONSTIGE";
  artOriginal: string;
  betrag: string;
  waehrung: string;
  referenzdatum: string | null;
  abgerufenAm: string;
};

export type ExternesKonto = {
  id: string;
  kennung: string;
  bezeichnung: string;
  iban: string | null;
  waehrung: string;
  kontoart: string | null;
  produktname: string | null;
  /** Zugang, über den das Konto bekannt wurde. `null`, wenn er entfernt wurde. */
  bankzugangId: string | null;
  zugeordnetesKonto: string | null;
  salden: Saldo[];
};

/** Was beim Entfernen eines Zugangs mit dessen Konten geschieht. */
export type Kontenbehandlung = "behalten" | "loeschen";

/**
 * Ergebnis eines entfernten Bankzugangs.
 *
 * Bewusst nicht leer: das Entfernen berührt zwei Systeme, und das Beenden der Sitzung beim
 * Anbieter kann fehlschlagen, ohne dass der Vorgang insgesamt fehlschlägt. Ein stiller Erfolg
 * hiesse dann, dass jemand die Autorisierung für widerrufen hält, während sie weiterläuft.
 */
export type Zugangsentfernung = {
  sitzungBeendet: boolean;
  anbietermeldung: string | null;
  entfernteKonten: number;
  behalteneKonten: number;
};

export type Institut = {
  name: string;
  land: string;
  anzeigename: string;
  hoechsteGueltigkeitTage: number;
};

export const STATUS_BESCHRIFTUNG: Record<Bankzugangstatus, string> = {
  NICHT_AUTORISIERT: "nicht autorisiert",
  AUTORISIERUNG_LAEUFT: "Autorisierung läuft",
  AUTORISIERT: "autorisiert",
  ABGELAUFEN: "abgelaufen",
  FEHLGESCHLAGEN: "fehlgeschlagen",
};

export const SALDENART_BESCHRIFTUNG: Record<Saldo["art"], string> = {
  GEBUCHT: "gebucht",
  VERFUEGBAR: "verfügbar laut Bank",
  VORGEMERKT: "einschließlich vorgemerkt",
  ABSCHLUSS: "Periodenabschluss",
  SONSTIGE: "sonstiger Saldo",
};

/** Formatiert einen Betrag mit seiner Währung. */
export function betragAnzeigen(betrag: string, waehrung: string): string {
  const zahl = Number(betrag);
  if (Number.isNaN(zahl)) {
    return `${betrag} ${waehrung}`;
  }
  return new Intl.NumberFormat("de-DE", { style: "currency", currency: waehrung }).format(zahl);
}

/** Formatiert einen Zeitpunkt für die Anzeige. */
export function zeitpunktAnzeigen(zeitpunkt: string): string {
  const datum = new Date(zeitpunkt);
  if (Number.isNaN(datum.getTime())) {
    return zeitpunkt;
  }
  return new Intl.DateTimeFormat("de-DE", { dateStyle: "medium", timeStyle: "short" }).format(datum);
}
