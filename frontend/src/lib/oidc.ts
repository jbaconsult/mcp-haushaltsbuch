/**
 * Der OIDC-Client des BFF.
 *
 * Die Anwendung ist reiner Client, nie Identity Provider (ADR-0005). Es gibt hier
 * kein Anmeldeformular, keine Passwortprüfung und keine Benutzerverwaltung —
 * angemeldet wird beim Identity Provider, und was zurückkommt, sind Token.
 *
 * Alle Aufrufe von hier laufen serverseitig. Das Client-Geheimnis verlässt den
 * Server nie, und der Autorisierungscode wird serverseitig getauscht statt im
 * Browser.
 */

/** Was der Identity Provider auf einen Tokenaufruf antwortet. */
export type Tokenantwort = {
  access_token: string;
  refresh_token?: string;
  expires_in: number;
  refresh_expires_in?: number;
  token_type: string;
};

type Endpunkte = {
  issuer: string;
  autorisierung: string;
  token: string;
  abmeldung: string;
};

/**
 * Ob die Anmeldung überhaupt eingerichtet ist.
 *
 * Im Entwicklungsprofil läuft das Backend ohne OIDC, und das bleibt so (Grenze
 * des Auftrags). Ohne Konfiguration darf hier nichts brechen — die Anmelderoute
 * sagt dann verständlich, dass keine Anmeldung eingerichtet ist, statt mit einer
 * Ausnahme zu enden, die nach einem Defekt aussieht.
 */
export function anmeldungEingerichtet(): boolean {
  return Boolean(
    process.env.OIDC_AUTH_SERVER_URL && process.env.BFF_CLIENT_ID && process.env.BFF_CLIENT_SECRET,
  );
}

function pflichtwert(name: string): string {
  const wert = process.env[name];
  if (!wert) {
    throw new Error(`${name} ist nicht gesetzt. Ohne diesen Wert gibt es keine Anmeldung.`);
  }
  return wert;
}

/**
 * Die Adresse, unter der der Browser diesen BFF erreicht.
 *
 * Bewusst Konfiguration und nicht aus der Anfrage abgeleitet. Die Rückleitungs-
 * adresse ist im Realm hinterlegt und damit verbindlich; sie aus `Host` oder
 * `X-Forwarded-Host` zu bilden hiesse, sie von einem Kopfzeilenwert abhängig zu
 * machen, den ein Aufrufer setzt.
 */
export function bffBasis(): string {
  return process.env.BFF_BASIS_URL ?? "http://localhost:3000";
}

/**
 * Schreibt eine Adresse auf die um, unter der der Browser den Anbieter erreicht.
 *
 * Nötig, sobald der BFF den Identity Provider unter einem anderen Namen erreicht
 * als der Mensch davor — im Container-Verbund der Regelfall: dort heisst er
 * `keycloak:8080` im Netz und `localhost:8081` auf dem Rechner. Ohne diese
 * Umschreibung leitet die Anmeldung den Browser auf einen Namen, den nur die
 * Container auflösen können, und endet in „Server nicht gefunden".
 *
 * Betrifft **nur** die Weiterleitung des Browsers. Tokentausch, Auffrischung und
 * Abmeldung laufen weiter über die interne Adresse, und der Issuer-Vergleich
 * ebenfalls: Das Token trägt den Aussteller, den der Anbieter selbst kennt.
 */
function fuerDenBrowser(adresse: string): string {
  const extern = process.env.OIDC_BROWSER_URL?.replace(/\/+$/, "");
  if (!extern) {
    return adresse;
  }

  const intern = pflichtwert("OIDC_AUTH_SERVER_URL").replace(/\/+$/, "");
  return adresse.startsWith(intern) ? extern + adresse.slice(intern.length) : adresse;
}

export function rueckleitungsadresse(): string {
  return `${bffBasis()}/anmeldung/rueckleitung`;
}

/**
 * Endpunkte aus dem Metadatendokument.
 *
 * Gelesen statt zusammengebaut: die Pfade sind eine Eigenschaft des Anbieters,
 * keine Konvention. Sie zu raten funktioniert bei Keycloak und bricht beim
 * nächsten Anbieter — und ADR-0005 hält den Provider ausdrücklich austauschbar.
 *
 * Das Ergebnis wird im Prozess gehalten. Es ändert sich nicht im Betrieb, und ein
 * Netzaufruf vor jeder Anmeldung wäre eine zusätzliche Ausfallstelle.
 */
let zwischenspeicher: { basis: string; endpunkte: Endpunkte } | null = null;

export async function endpunkte(): Promise<Endpunkte> {
  const basis = pflichtwert("OIDC_AUTH_SERVER_URL").replace(/\/+$/, "");

  if (zwischenspeicher?.basis === basis) {
    return zwischenspeicher.endpunkte;
  }

  const antwort = await fetch(`${basis}/.well-known/openid-configuration`, { cache: "no-store" });
  if (!antwort.ok) {
    throw new Error(`Das Metadatendokument des Identity Providers ist nicht abrufbar (${antwort.status}).`);
  }

  const dokument = (await antwort.json()) as {
    issuer: string;
    authorization_endpoint: string;
    token_endpoint: string;
    end_session_endpoint?: string;
  };

  const gelesen: Endpunkte = {
    issuer: dokument.issuer,
    autorisierung: dokument.authorization_endpoint,
    token: dokument.token_endpoint,
    abmeldung: dokument.end_session_endpoint ?? `${basis}/protocol/openid-connect/logout`,
  };

  zwischenspeicher = { basis, endpunkte: gelesen };
  return gelesen;
}

/** Nur für Tests: den Zwischenspeicher verwerfen. */
export function zwischenspeicherLeeren(): void {
  zwischenspeicher = null;
}

/** Baut die Adresse, an die der Mensch zum Identity Provider geschickt wird. */
export async function anmeldeadresse(zustand: string, pruefwert: string): Promise<string> {
  const ziel = new URL(fuerDenBrowser((await endpunkte()).autorisierung));

  ziel.searchParams.set("client_id", pflichtwert("BFF_CLIENT_ID"));
  ziel.searchParams.set("response_type", "code");
  // Nur "openid". Die Scopes profile, email, roles und die Audience des Backends
  // stehen im Realm als DEFAULT-Scopes des BFF-Clients - Keycloak gewährt sie von
  // sich aus. Sie zusätzlich anzufragen ist kein Mehr an Rechten, sondern ein
  // Fehler: Ein Scope, der weder default noch optional ist, wird mit
  // "invalid_scope" abgelehnt, und die Anmeldung bricht ab, bevor ein
  // Anmeldeformular erscheint.
  ziel.searchParams.set("scope", "openid");
  ziel.searchParams.set("redirect_uri", rueckleitungsadresse());
  ziel.searchParams.set("state", zustand);
  ziel.searchParams.set("code_challenge", pruefwert);
  ziel.searchParams.set("code_challenge_method", "S256");

  return ziel.toString();
}

/** Gemeinsamer Aufruf gegen den Token-Endpunkt, mit Client-Authentifizierung. */
async function tokenaufruf(felder: Record<string, string>): Promise<Tokenantwort | null> {
  const rumpf = new URLSearchParams({
    client_id: pflichtwert("BFF_CLIENT_ID"),
    client_secret: pflichtwert("BFF_CLIENT_SECRET"),
    ...felder,
  });

  const antwort = await fetch((await endpunkte()).token, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: rumpf.toString(),
    cache: "no-store",
  });

  if (!antwort.ok) {
    return null;
  }
  return (await antwort.json()) as Tokenantwort;
}

/**
 * Tauscht den Autorisierungscode gegen Token.
 *
 * Der Verifier gehört zwingend dazu (PKCE mit S256). Der Realm verlangt ihn, und
 * das ist richtig so: ein abgefangener Code allein ist damit wertlos.
 */
export async function tokenTauschen(code: string, verifier: string): Promise<Tokenantwort | null> {
  return tokenaufruf({
    grant_type: "authorization_code",
    code,
    code_verifier: verifier,
    redirect_uri: rueckleitungsadresse(),
  });
}

/**
 * Frischt das Zugriffstoken auf.
 *
 * Liefert `null`, wenn das Auffrischungstoken nicht mehr gilt. Das ist kein
 * Fehler, sondern das Ende der Sitzung — der Aufrufer behandelt es wie „nicht
 * angemeldet".
 */
export async function tokenAuffrischen(auffrischungstoken: string): Promise<Tokenantwort | null> {
  return tokenaufruf({ grant_type: "refresh_token", refresh_token: auffrischungstoken });
}

/**
 * Beendet die Sitzung beim Identity Provider.
 *
 * Serverseitig über den Logout-Endpunkt mit dem Auffrischungstoken, nicht über
 * eine Weiterleitung des Browsers mit `id_token_hint`. Zwei Gründe:
 *
 * 1. Ohne `id_token_hint` zeigt Keycloak bei der Weiterleitung eine
 *    Bestätigungsseite. Ein Abmelden, das nachfragt, ist kein Abmelden.
 * 2. Das `id_token` mitzuführen, nur um es hier vorzuzeigen, kostet über tausend
 *    Byte im Sitzungscookie. Das Cookie liegt schon ohne es bei 84 Prozent der
 *    4-Kilobyte-Grenze; mit ihm wäre sie überschritten, und Browser verwerfen ein
 *    zu grosses Cookie kommentarlos. Gemessen, siehe doc/betrieb/anmeldung.md.
 *
 * Der Weg ist nachgewiesen: Der Aufruf quittiert mit 204, und ein anschliessender
 * Auffrischungsversuch scheitert mit „Session not active".
 */
export async function abmeldenBeimAnbieter(auffrischungstoken: string): Promise<boolean> {
  try {
    const antwort = await fetch((await endpunkte()).abmeldung, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: pflichtwert("BFF_CLIENT_ID"),
        client_secret: pflichtwert("BFF_CLIENT_SECRET"),
        refresh_token: auffrischungstoken,
      }).toString(),
      cache: "no-store",
    });
    return antwort.ok;
  } catch {
    return false;
  }
}
