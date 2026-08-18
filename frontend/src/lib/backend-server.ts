/**
 * Serverseitiger Zugriff auf das Backend, mit angehängtem Zugriffstoken.
 *
 * Getrennt von `backend.ts` und `bank.ts`, weil hier die Sitzung gelesen wird.
 * Stünde das in einer Datei mit Typen und Beschriftungen, zöge jede Client
 * Component, die auch nur eine Beschriftung von dort braucht, die
 * Sitzungsverwaltung des BFF in ihr Bündel - und der Build bricht ab, sobald
 * `next/headers` im Browser landet.
 *
 * Jede Server Component, die etwas vom Backend braucht, geht über diese Datei.
 * Ein direkter `fetch` ohne Token liefert in Produktion ein 401, und zwar erst
 * dort - im Entwicklungsprofil läuft er durch, weil das Backend ohne OIDC
 * arbeitet.
 */

import { aktuelleSitzung } from "@/lib/sitzung";
import { backendUrl } from "@/lib/backend";

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
