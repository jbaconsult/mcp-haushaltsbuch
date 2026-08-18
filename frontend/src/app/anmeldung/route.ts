import { NextRequest, NextResponse } from "next/server";

import { zustandAnlegen } from "@/lib/anmeldezustand";
import { anmeldeadresse, anmeldungEingerichtet } from "@/lib/oidc";

/**
 * Startet die Anmeldung.
 *
 * Erzeugt `state` und PKCE-Verifier, legt beide kurzlebig ab und schickt den
 * Menschen zum Identity Provider. Hier steht kein Anmeldeformular und wird auch
 * keines stehen — nach ADR-0005 enthält diese Anwendung keinen
 * Identity-Provider-Code.
 *
 * `?ziel=/pfad` bestimmt, wohin es nach der Anmeldung zurückgeht. Der Wert wird
 * auf einen Pfad dieser Anwendung beschränkt, sonst wäre die Anmeldung ein
 * offener Weiterleiter.
 */
export async function GET(anfrage: NextRequest) {
  if (!anmeldungEingerichtet()) {
    // Im Entwicklungsprofil ist das der Normalfall und kein Defekt: dort läuft
    // das Backend ohne OIDC. Eine Ausnahme sähe hier nach einem Fehler aus.
    return NextResponse.json(
      {
        meldung:
          "Es ist keine Anmeldung eingerichtet. Im Entwicklungsprofil ist das gewollt - " +
          "dort arbeitet die Anwendung ohne Identity Provider. Für den Betrieb fehlen " +
          "OIDC_AUTH_SERVER_URL, BFF_CLIENT_ID und BFF_CLIENT_SECRET.",
      },
      { status: 503 },
    );
  }

  const { zustand, pruefwert } = await zustandAnlegen(anfrage.nextUrl.searchParams.get("ziel") ?? "/");

  return NextResponse.redirect(await anmeldeadresse(zustand, pruefwert));
}
