import { cookies } from "next/headers";

import { tokenAuffrischen, type Tokenantwort } from "@/lib/oidc";
import { entsiegeln, versiegeln } from "@/lib/siegel";

/**
 * Sitzungsverwaltung des BFF.
 *
 * Der Browser bekommt niemals ein Zugriffstoken zu sehen. Er hält ein
 * `httpOnly`-Cookie mit **verschlüsseltem** Inhalt; das Token wird erst beim
 * Weiterreichen an das Backend angehängt.
 *
 * Zwei Schichten, gegen zwei verschiedene Angriffe:
 *
 * - `httpOnly` schützt gegen Skripte im Browser. Ein Token im `localStorage` ist
 *   über jedes eingebettete Skript lesbar - bei einer Anwendung, die Kontostände
 *   führt, der falsche Kompromiss, und genau deshalb gibt es den BFF überhaupt.
 * - Die Verschlüsselung schützt gegen jeden, der den Cookie-Speicher liest. Der
 *   liegt als Datei auf dem Endgerät; `httpOnly` bedeutet dort gar nichts.
 *
 * Ein manipuliertes Cookie wird wie kein Cookie behandelt - siehe
 * {@link entsiegeln}.
 */

const SITZUNGSCOOKIE = "hb_sitzung";

/**
 * Wie früh vor Ablauf aufgefrischt wird.
 *
 * Ein Token, das in dreissig Sekunden abläuft, überlebt den Aufruf nicht
 * zwangsläufig, für den es angehängt wird - zwischen dem Anhängen hier und der
 * Prüfung im Backend liegen Netzwerk und Uhrendrift. Aufgefrischt wird deshalb
 * mit Vorlauf, nicht auf den letzten Moment.
 */
const VORLAUF_MS = 60_000;

/**
 * Obergrenze, ab der das Cookie gemeldet wird.
 *
 * **Gemessen an einem echten Token dieses Realms:** Zugriffstoken 1599 Byte,
 * Auffrischungstoken 789 Byte, versiegelt zusammen 3439 Byte - das sind 84
 * Prozent der 4096-Byte-Grenze eines Cookies. Der Wert gilt für einen Benutzer
 * mit zwei Realm-Rollen; ohne Rollen im Token wären es 2182 Byte (53 Prozent).
 *
 * Der Abstand zur Grenze ist damit dünn, und er wird mit jeder weiteren Rolle,
 * Gruppe oder eigenen Claim dünner. Erreicht wird die Grenze **still**: Browser
 * verwerfen ein zu grosses Cookie kommentarlos, und der Mensch davor ist einfach
 * nicht angemeldet, ohne Fehlermeldung an irgendeiner Stelle.
 *
 * Diese Warnung ist der Wächter davor, und sie steht bewusst knapp über dem
 * heutigen Messwert. Schlägt sie an, ist eine serverseitige Sitzungsablage
 * fällig - das ist eine Entwurfsentscheidung mit eigenen Folgen (Speicherort,
 * Lebensdauer, Verhalten bei Neustart) und keine Ausweichlösung.
 */
const WARNGRENZE_BYTES = 3600;

export type Sitzung = {
  zugriffstoken: string;
  /** Fehlt, wenn der Anbieter keines ausgegeben hat - dann endet die Sitzung mit dem Zugriffstoken. */
  auffrischungstoken?: string;
  laeuftAbUm: number;
  auffrischungLaeuftAbUm?: number;
  /** Der `sub`-Claim. Nur zur Diagnose; die Autorisierung entscheidet das Backend. */
  subjekt?: string;
};

/**
 * Liest die Sitzung der laufenden Anfrage und frischt sie bei Bedarf auf.
 *
 * Gibt `null` zurück, wenn keine Sitzung besteht, sie manipuliert wurde oder sie
 * endgültig abgelaufen ist - der Aufrufer muss alle drei gleich behandeln.
 *
 * Die Auffrischung geschieht hier und nicht in einer Middleware, weil hier die
 * Sitzung ohnehin gelesen wird. Ein abgelaufenes Token weiterzureichen erzeugt
 * einen 401 aus dem Backend, der schwerer zu deuten ist als „keine Sitzung".
 */
export async function aktuelleSitzung(): Promise<Sitzung | null> {
  const speicher = await cookies();
  const sitzung = sitzungAusCookie(speicher.get(SITZUNGSCOOKIE)?.value);

  if (!sitzung) {
    return null;
  }

  if (sitzung.laeuftAbUm - VORLAUF_MS > Date.now()) {
    return sitzung;
  }

  return aufgefrischt(sitzung);
}

/**
 * Entschlüsselt und prüft den Cookie-Inhalt.
 *
 * Getrennt von {@link aktuelleSitzung}, damit ein Test sie ohne Anfragekontext
 * prüfen kann - und weil die Auffrischung sie nicht braucht.
 */
export function sitzungAusCookie(rohwert: string | undefined): Sitzung | null {
  const klartext = entsiegeln("sitzung", rohwert);
  if (!klartext) {
    return null;
  }

  try {
    const gelesen = JSON.parse(klartext) as Sitzung;
    // Ein entsiegeltes, aber inhaltlich unbrauchbares Cookie ist ebenfalls keine
    // Sitzung. Der Fall entsteht nach einem Formatwechsel, nicht durch Angriff.
    if (typeof gelesen.zugriffstoken !== "string" || typeof gelesen.laeuftAbUm !== "number") {
      return null;
    }
    return gelesen;
  } catch {
    return null;
  }
}

/**
 * Erneuert das Zugriffstoken, ohne den Menschen zu behelligen.
 *
 * Scheitert die Auffrischung, ist die Sitzung zu Ende: Das Cookie wird gelöscht
 * und `null` geliefert. Der nächste Aufruf führt dann zur Anmeldung statt zu
 * einem Fehler.
 */
async function aufgefrischt(sitzung: Sitzung): Promise<Sitzung | null> {
  if (!sitzung.auffrischungstoken) {
    await sitzungLoeschen();
    return null;
  }

  const antwort = await tokenAuffrischen(sitzung.auffrischungstoken);
  if (!antwort) {
    await sitzungLoeschen();
    return null;
  }

  const erneuert = ausTokenantwort(antwort, sitzung.subjekt);
  await sitzungSetzen(erneuert);
  return erneuert;
}

/** Formt eine Antwort des Identity Providers in eine Sitzung um. */
export function ausTokenantwort(antwort: Tokenantwort, subjekt?: string): Sitzung {
  const jetzt = Date.now();

  return {
    zugriffstoken: antwort.access_token,
    auffrischungstoken: antwort.refresh_token,
    laeuftAbUm: jetzt + antwort.expires_in * 1000,
    auffrischungLaeuftAbUm: antwort.refresh_expires_in
      ? jetzt + antwort.refresh_expires_in * 1000
      : undefined,
    subjekt,
  };
}

/**
 * Setzt die Sitzung.
 *
 * Die Lebensdauer des Cookies richtet sich nach dem **Auffrischungstoken**, nicht
 * nach dem Zugriffstoken. Letzteres gilt fünf Minuten; ein Cookie mit dieser
 * Lebensdauer wäre alle fünf Minuten verschwunden, und die Auffrischung hätte
 * nichts mehr, womit sie arbeiten könnte.
 */
export async function sitzungSetzen(sitzung: Sitzung): Promise<void> {
  const speicher = await cookies();
  const inhalt = versiegeln("sitzung", JSON.stringify(sitzung));

  if (inhalt.length > WARNGRENZE_BYTES) {
    console.warn(
      `BFF: Das Sitzungscookie ist auf ${inhalt.length} Byte gewachsen und nähert sich der ` +
        "4096-Byte-Grenze. Wird sie überschritten, verwerfen Browser das Cookie kommentarlos und " +
        "die Anmeldung schlägt ohne Fehlermeldung fehl. Dann ist eine serverseitige Sitzungsablage fällig.",
    );
  }

  const bis = sitzung.auffrischungLaeuftAbUm ?? sitzung.laeuftAbUm;

  speicher.set(SITZUNGSCOOKIE, inhalt, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: Math.max(0, Math.floor((bis - Date.now()) / 1000)),
  });
}

export async function sitzungLoeschen(): Promise<void> {
  const speicher = await cookies();
  speicher.delete(SITZUNGSCOOKIE);
}
