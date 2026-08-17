import { NextRequest, NextResponse } from "next/server";

import { backendUrl } from "@/lib/backend";
import { aktuelleSitzung } from "@/lib/sitzung";

/**
 * Backend for Frontend.
 *
 * Alle Anfragen des Dashboards laufen hier durch. Der Browser spricht nur mit
 * dieser Route; die Adresse des Backends und das Zugriffstoken bleiben auf dem
 * Server.
 *
 * Der Zweck ist nicht Bequemlichkeit, sondern dass kein Token im Browser landet.
 * Ein Token im `localStorage` ist über jedes eingebettete Skript lesbar - bei
 * einer Anwendung, die Kontostände führt, der falsche Kompromiss.
 */

/**
 * Header, die aus der Browser-Anfrage übernommen werden.
 *
 * Eine Positivliste, keine Sperrliste. Alles durchzureichen und Unerwünschtes
 * auszuschließen bedeutet, jeden künftig hinzukommenden Header stillschweigend
 * mitzunehmen - darunter `cookie`, das die Sitzung des BFF an das Backend
 * weitergäbe, und `authorization`, mit dem der Browser das serverseitig
 * gesetzte Token überschreiben könnte.
 */
const UEBERNOMMENE_ANFRAGEHEADER = ["accept", "content-type"];

const UEBERNOMMENE_ANTWORTHEADER = ["content-type", "cache-control"];

async function weiterleiten(anfrage: NextRequest, pfad: string[]): Promise<NextResponse> {
  let ziel: string;
  try {
    ziel = backendUrl(pfad, anfrage.nextUrl.searchParams.toString());
  } catch {
    return NextResponse.json({ fehler: "Ungültiger Pfad" }, { status: 400 });
  }

  const kopfzeilen = new Headers();
  for (const name of UEBERNOMMENE_ANFRAGEHEADER) {
    const wert = anfrage.headers.get(name);
    if (wert) {
      kopfzeilen.set(name, wert);
    }
  }

  const sitzung = await aktuelleSitzung();
  if (sitzung) {
    kopfzeilen.set("authorization", `Bearer ${sitzung.zugriffstoken}`);
  }
  // Ohne Sitzung geht die Anfrage ohne Token hinaus. Im Entwicklungsprofil ist
  // das der Normalfall - dort läuft das Backend ohne OIDC. In Produktion
  // antwortet es mit 401, und das ist die richtige Antwort.

  const hatRumpf = anfrage.method !== "GET" && anfrage.method !== "HEAD";

  let antwort: Response;
  try {
    antwort = await fetch(ziel, {
      method: anfrage.method,
      headers: kopfzeilen,
      body: hatRumpf ? await anfrage.text() : undefined,
      // Kein Zwischenspeichern: Kontodaten sind pro Benutzer verschieden, und
      // eine gemeinsam genutzte Antwort wäre genau der Fehler, den die
      // Zugriffskontrolle verhindern soll.
      cache: "no-store",
    });
  } catch (fehler) {
    // Die Fehlermeldung selbst enthält die interne Backend-Adresse und geht
    // deshalb nur ins Protokoll, nicht an den Browser.
    console.error("BFF: Backend nicht erreichbar", fehler);
    return NextResponse.json({ fehler: "Backend nicht erreichbar" }, { status: 502 });
  }

  const antwortkopf = new Headers();
  for (const name of UEBERNOMMENE_ANTWORTHEADER) {
    const wert = antwort.headers.get(name);
    if (wert) {
      antwortkopf.set(name, wert);
    }
  }

  return new NextResponse(antwort.body, {
    status: antwort.status,
    headers: antwortkopf,
  });
}

type Kontext = { params: Promise<{ pfad: string[] }> };

export async function GET(anfrage: NextRequest, kontext: Kontext) {
  return weiterleiten(anfrage, (await kontext.params).pfad);
}

export async function POST(anfrage: NextRequest, kontext: Kontext) {
  return weiterleiten(anfrage, (await kontext.params).pfad);
}

export async function PUT(anfrage: NextRequest, kontext: Kontext) {
  return weiterleiten(anfrage, (await kontext.params).pfad);
}

export async function DELETE(anfrage: NextRequest, kontext: Kontext) {
  return weiterleiten(anfrage, (await kontext.params).pfad);
}
