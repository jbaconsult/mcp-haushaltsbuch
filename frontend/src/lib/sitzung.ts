import { cookies } from "next/headers";

/**
 * Sitzungsverwaltung des BFF.
 *
 * Der Browser bekommt niemals ein Zugriffstoken zu sehen. Er hält nur ein
 * `httpOnly`-Cookie; das Token liegt serverseitig und wird erst beim
 * Weiterreichen an das Backend angehängt.
 *
 * Ein Token im `localStorage` ist über jedes eingebettete Skript lesbar. Bei
 * einer Anwendung, die Kontostände führt, ist das der falsche Kompromiss - und
 * genau deshalb gibt es den BFF überhaupt.
 */

const SITZUNGSCOOKIE = "hb_sitzung";

export type Sitzung = {
  zugriffstoken: string;
  laeuftAbUm: number;
};

/**
 * Liest die Sitzung der laufenden Anfrage.
 *
 * Gibt `null` zurück, wenn keine Sitzung besteht oder sie abgelaufen ist - der
 * Aufrufer muss beides gleich behandeln.
 */
export async function aktuelleSitzung(): Promise<Sitzung | null> {
  const speicher = await cookies();
  const rohwert = speicher.get(SITZUNGSCOOKIE)?.value;

  if (!rohwert) {
    return null;
  }

  try {
    const sitzung = JSON.parse(rohwert) as Sitzung;
    // Abgelaufen ist wie nicht vorhanden. Ein abgelaufenes Token weiterzureichen
    // erzeugt einen 401 aus dem Backend, der schwerer zu deuten ist als "keine
    // Sitzung".
    return sitzung.laeuftAbUm > Date.now() ? sitzung : null;
  } catch {
    // Beschädigtes Cookie: behandeln wie keine Sitzung, nicht wie einen Fehler.
    return null;
  }
}

/**
 * Setzt die Sitzung.
 *
 * ANSCHLUSSPUNKT für die Anmeldung: Hier landet das Ergebnis des
 * OIDC-Autorisierungscode-Flows gegen Keycloak. Der Flow selbst ist noch nicht
 * gebaut - im Entwicklungsprofil läuft das Backend ohne OIDC, es gibt also
 * nichts anzuhängen.
 *
 * Was dafür noch fehlt: Callback-Route, Zustandsprüfung gegen CSRF, PKCE,
 * Auffrischung des Tokens. Bewusst ein eigener Arbeitsschritt und keine
 * Nebenwirkung dieses Scaffoldings - eine halbe Anmeldung ist schlechter als
 * gar keine, weil sie fertig aussieht.
 */
export async function sitzungSetzen(sitzung: Sitzung): Promise<void> {
  const speicher = await cookies();

  speicher.set(SITZUNGSCOOKIE, JSON.stringify(sitzung), {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: Math.max(0, Math.floor((sitzung.laeuftAbUm - Date.now()) / 1000)),
  });
}

export async function sitzungLoeschen(): Promise<void> {
  const speicher = await cookies();
  speicher.delete(SITZUNGSCOOKIE);
}
