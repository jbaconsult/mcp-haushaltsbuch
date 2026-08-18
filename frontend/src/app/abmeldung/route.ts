import { NextRequest, NextResponse } from "next/server";

import { abmeldenBeimAnbieter } from "@/lib/oidc";
import { aktuelleSitzung, sitzungLoeschen } from "@/lib/sitzung";

/**
 * Meldet ab — lokal und beim Identity Provider.
 *
 * Beides ist nötig. Nur das eigene Cookie zu löschen liesse die Sitzung beim
 * Identity Provider bestehen; die nächste Anmeldung liefe dann ohne Nachfrage
 * durch, weil dort noch eine gültige Sitzung liegt. Das sieht aus wie „Abmelden
 * funktioniert nicht" und ist genau das.
 *
 * Nur POST. Ein Abmelden per GET liesse sich über ein eingebettetes Bild auf
 * einer fremden Seite auslösen — der Schaden wäre gering, aber die richtige
 * Bauweise kostet hier nichts.
 */
export async function POST(anfrage: NextRequest) {
  const sitzung = await aktuelleSitzung();

  // Zuerst beim Anbieter, dann lokal: Nach dem Löschen ist das
  // Auffrischungstoken verloren, und ohne dieses lässt sich die Sitzung dort
  // nicht mehr beenden.
  if (sitzung?.auffrischungstoken) {
    await abmeldenBeimAnbieter(sitzung.auffrischungstoken);
  }

  await sitzungLoeschen();

  // Scheitert das Beenden beim Anbieter, wird trotzdem lokal abgemeldet. Alles
  // andere hiesse: wer sich abmelden will, kann es nicht, weil der Anbieter
  // gerade schweigt.
  return NextResponse.redirect(new URL("/", anfrage.nextUrl.origin), { status: 303 });
}
