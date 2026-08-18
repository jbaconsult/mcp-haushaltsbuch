/**
 * Bankzugänge und die von der Bank gemeldeten Konten.
 *
 * Die Typen bilden ab, was das Backend liefert - bewusst als eigene Deklaration
 * und nicht generiert: eine Änderung am Backend soll hier sichtbar auffallen und
 * nicht stillschweigend durchgereicht werden.
 */

import { aktuelleSitzung } from "@/lib/sitzung";
import { backendUrl } from "@/lib/backend";

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
  bankzugangId: string;
  zugeordnetesKonto: string | null;
  salden: Saldo[];
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

/**
 * Ruft das Backend serverseitig auf und hängt das Zugriffstoken an.
 *
 * Server Components sprechen das Backend direkt an, nicht über den BFF - der ist
 * für Anfragen aus dem Browser da. Das Token muss deshalb hier angehängt werden;
 * ohne diesen Schritt liefe jede serverseitige Abfrage in Produktion in ein 401.
 */
export async function backendHolen<T>(
  segmente: string[],
  init?: RequestInit,
): Promise<{ daten: T | null; fehler: string | null }> {
  const kopfzeilen = new Headers(init?.headers);
  kopfzeilen.set("accept", "application/json");

  const sitzung = await aktuelleSitzung();
  if (sitzung) {
    kopfzeilen.set("authorization", `Bearer ${sitzung.zugriffstoken}`);
  }

  try {
    const antwort = await fetch(backendUrl(segmente), {
      ...init,
      headers: kopfzeilen,
      // Kontodaten sind pro Benutzer verschieden. Eine gemeinsam genutzte Antwort
      // wäre genau der Fehler, den die Zugriffskontrolle verhindern soll.
      cache: "no-store",
    });

    if (!antwort.ok) {
      return { daten: null, fehler: await meldungAus(antwort) };
    }
    return { daten: (await antwort.json()) as T, fehler: null };
  } catch {
    // Die Ursache enthält die interne Backend-Adresse und bleibt deshalb im
    // Protokoll des Servers.
    return { daten: null, fehler: "Backend nicht erreichbar" };
  }
}

/**
 * Holt die Meldung aus einer Fehlerantwort.
 *
 * Die Meldung des Anbieters wird durchgereicht statt durch ein eigenes „Fehler
 * beim Abruf" ersetzt. Wer den Grund verschweigt, kostet den Menschen davor eine
 * halbe Stunde.
 */
async function meldungAus(antwort: Response): Promise<string> {
  try {
    const inhalt = (await antwort.json()) as { meldung?: string; fehler?: string };
    return inhalt.meldung ?? inhalt.fehler ?? `Backend antwortete mit ${antwort.status}`;
  } catch {
    return `Backend antwortete mit ${antwort.status}`;
  }
}

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
