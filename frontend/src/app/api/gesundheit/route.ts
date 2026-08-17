import { NextResponse } from "next/server";

/**
 * Health-Endpunkt für den Container.
 *
 * Antwortet ausschliesslich aus dem eigenen Prozess - **ohne** das Backend zu
 * befragen. Das ist der Punkt: ein Healthcheck beantwortet die Frage „läuft
 * dieser Dienst?", nicht „antworten seine Abhängigkeiten?".
 *
 * Andernfalls kaskadieren Ausfälle. Ein Frontend, das sich für krank hält, weil
 * das Backend gerade neu startet, wird von Docker neu gestartet - und danach
 * ist es genauso krank, nur später. Die Startseite eignet sich aus demselben
 * Grund nicht als Prüfziel.
 */
export const dynamic = "force-dynamic";

export function GET() {
  return NextResponse.json({ zustand: "bereit" });
}
