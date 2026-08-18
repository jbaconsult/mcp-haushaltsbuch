import { NextRequest, NextResponse } from "next/server";

import { zustandEinloesen } from "@/lib/anmeldezustand";
import { bffBasis, endpunkte, tokenTauschen } from "@/lib/oidc";
import { ausTokenantwort, sitzungSetzen } from "@/lib/sitzung";

/**
 * Nimmt die Rückleitung vom Identity Provider entgegen.
 *
 * Ein Route Handler und keine Seite, weil hier ein Cookie gesetzt wird - das
 * können Server Components nicht. Fehlerfälle gehen deshalb an eine eigene Seite
 * weiter, statt eine weisse Antwort zu hinterlassen.
 *
 * Reihenfolge der Prüfungen, und jede davon hat einen Grund:
 *
 * 1. **Zustandswert einlösen, zuerst und immer.** Er wird dabei verbraucht, auch
 *    wenn die Prüfung scheitert. Ohne diesen Schritt genügt ein untergeschobener
 *    Link, um im Namen eines Angemeldeten eine fremde Sitzung zu setzen.
 * 2. **Aussteller prüfen**, sofern er mitkommt. Diese Keycloak-Fassung gibt ihn
 *    aus - gemessen, siehe doc/betrieb/anmeldung.md. Er verhindert, dass die
 *    Antwort eines anderen Ausstellers hier eingelöst wird.
 * 3. **Code serverseitig tauschen**, mit PKCE-Verifier und Client-Geheimnis. Der
 *    Code erreicht nie einen Aufruf aus dem Browser heraus.
 */
export async function GET(anfrage: NextRequest) {
  const parameter = anfrage.nextUrl.searchParams;

  // Der Zustandswert wird als Erstes eingelöst und dabei verbraucht - auch dann,
  // wenn der Identity Provider einen Fehler meldet. Ein abgebrochener Vorgang
  // darf keinen wiederverwendbaren Wert hinterlassen.
  const vorgang = await zustandEinloesen(parameter.get("state"));

  const fehler = parameter.get("error");
  if (fehler) {
    return fehlerseite(anfrage, "abgelehnt", parameter.get("error_description") ?? fehler);
  }

  if (!vorgang) {
    return fehlerseite(anfrage, "zustand");
  }

  const aussteller = parameter.get("iss");
  if (aussteller && aussteller !== (await endpunkte()).issuer) {
    return fehlerseite(anfrage, "aussteller");
  }

  const code = parameter.get("code");
  if (!code) {
    return fehlerseite(anfrage, "zustand");
  }

  const antwort = await tokenTauschen(code, vorgang.verifier);
  if (!antwort) {
    return fehlerseite(anfrage, "tausch");
  }

  await sitzungSetzen(ausTokenantwort(antwort, subjektAus(antwort.access_token)));

  return NextResponse.redirect(new URL(vorgang.ziel, bffBasis()));
}

/**
 * Liest den `sub`-Claim aus dem Zugriffstoken.
 *
 * **Ohne Signaturprüfung**, und das ist hier vertretbar: Das Token kam gerade
 * über eine serverseitige, TLS-gesicherte Verbindung direkt vom Token-Endpunkt.
 * Der Wert dient allein der Diagnose im Protokoll des BFF - autorisiert wird im
 * Backend, das die Signatur prüft. Fällt das Lesen aus, fehlt eine Angabe im
 * Protokoll und sonst nichts.
 */
function subjektAus(zugriffstoken: string): string | undefined {
  try {
    const rumpf = zugriffstoken.split(".")[1];
    if (!rumpf) {
      return undefined;
    }
    const inhalt = JSON.parse(Buffer.from(rumpf, "base64url").toString("utf8")) as { sub?: string };
    return inhalt.sub;
  } catch {
    return undefined;
  }
}

function fehlerseite(anfrage: NextRequest, grund: string, meldung?: string): NextResponse {
  const ziel = new URL("/anmeldung/fehler", anfrage.nextUrl.origin);
  ziel.searchParams.set("grund", grund);
  if (meldung) {
    ziel.searchParams.set("meldung", meldung);
  }
  return NextResponse.redirect(ziel);
}
