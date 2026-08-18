import { createHash, randomBytes } from "node:crypto";
import { cookies } from "next/headers";

import { entsiegeln, gleich, versiegeln } from "@/lib/siegel";

/**
 * Der Zustand eines laufenden Anmeldevorgangs: `state` und PKCE-Verifier.
 *
 * Dieselbe Semantik wie die Zustandshaltung der Bankrückleitung — einmalig, kurz
 * gültig, und unbekannt, verbraucht oder abgelaufen sind von aussen nicht zu
 * unterscheiden. Dort liegt der Wert in der Datenbank, weil er an einen bereits
 * angemeldeten Benutzer gebunden wird. Hier gibt es diesen Benutzer noch nicht;
 * der Vorgang ist genau der, der ihn erst herstellt. Die Ablage ist deshalb ein
 * eigenes, kurzlebiges Cookie.
 *
 * Es trägt einen anderen abgeleiteten Schlüssel als die Sitzung. Ein erbeuteter
 * Anmeldezustand lässt sich damit nicht als Sitzung ausgeben.
 */

const ZUSTANDSCOOKIE = "hb_anmeldung";

/**
 * Wie lange ein Anmeldevorgang offen bleiben darf.
 *
 * Zehn Minuten reichen für eine Anmeldung samt zweitem Faktor und sind knapp
 * genug, dass ein abgefangener Link selten noch trägt. Derselbe Gedanke wie bei
 * der Bankautorisierung, nur etwas kürzer: hier steht kein Institut dazwischen,
 * das seinerseits Zeit braucht.
 */
const GUELTIGKEIT_MS = 10 * 60 * 1000;

type Zustandseintrag = {
  zustand: string;
  verifier: string;
  laeuftAbUm: number;
  /** Wohin nach erfolgreicher Anmeldung zurückgekehrt wird. Immer ein reiner Pfad. */
  ziel: string;
};

/**
 * Erzeugt `state` und PKCE-Verifier und legt beide ab.
 *
 * Beide aus 32 Byte Zufall. Ein ratbarer `state` wäre dasselbe wie gar keiner,
 * und ein ratbarer Verifier hebt den Schutz von PKCE auf.
 */
export async function zustandAnlegen(ziel: string): Promise<{ zustand: string; pruefwert: string }> {
  const zustand = randomBytes(32).toString("base64url");
  const verifier = randomBytes(32).toString("base64url");

  const eintrag: Zustandseintrag = {
    zustand,
    verifier,
    laeuftAbUm: Date.now() + GUELTIGKEIT_MS,
    ziel: sicheresZiel(ziel),
  };

  const speicher = await cookies();
  speicher.set(ZUSTANDSCOOKIE, versiegeln("anmeldezustand", JSON.stringify(eintrag)), {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    // Lax und nicht Strict: Der Mensch kommt per Weiterleitung vom Identity
    // Provider zurück, und bei Strict sendete der Browser das Cookie dabei nicht
    // mit. Der Vorgang bräche genau am Ende ab.
    sameSite: "lax",
    path: "/",
    maxAge: Math.floor(GUELTIGKEIT_MS / 1000),
  });

  return { zustand, pruefwert: pruefwertAus(verifier) };
}

/**
 * Löst einen Zustandswert ein.
 *
 * Einmalig: Das Cookie wird gelöscht, bevor geprüft wird. Ein zweiter Aufruf mit
 * demselben Wert findet nichts mehr — auch dann nicht, wenn der erste an der
 * Prüfung scheiterte. Wer einen `state` errät, bekommt keinen zweiten Versuch.
 *
 * Liefert `null`, wenn kein Vorgang läuft, der Wert nicht passt oder er
 * abgelaufen ist. Die drei Fälle sind von aussen nicht unterscheidbar.
 */
export async function zustandEinloesen(
  zustand: string | null,
): Promise<{ verifier: string; ziel: string } | null> {
  const speicher = await cookies();
  const rohwert = speicher.get(ZUSTANDSCOOKIE)?.value;
  speicher.delete(ZUSTANDSCOOKIE);

  return zustandPruefen(rohwert, zustand, Date.now());
}

/**
 * Die reine Prüfung, ohne Cookie-Zugriff.
 *
 * Getrennt, damit ein Test die drei Ablehnungsgründe ohne Anfragekontext
 * durchspielen kann.
 */
export function zustandPruefen(
  rohwert: string | undefined,
  zustand: string | null,
  jetzt: number,
): { verifier: string; ziel: string } | null {
  if (!zustand) {
    return null;
  }

  const klartext = entsiegeln("anmeldezustand", rohwert);
  if (!klartext) {
    return null;
  }

  try {
    const eintrag = JSON.parse(klartext) as Zustandseintrag;

    if (typeof eintrag.zustand !== "string" || typeof eintrag.verifier !== "string") {
      return null;
    }
    if (eintrag.laeuftAbUm <= jetzt) {
      return null;
    }
    if (!gleich(eintrag.zustand, zustand)) {
      return null;
    }

    return { verifier: eintrag.verifier, ziel: sicheresZiel(eintrag.ziel) };
  } catch {
    return null;
  }
}

/** Der `code_challenge` zu einem Verifier, Verfahren S256. */
export function pruefwertAus(verifier: string): string {
  return createHash("sha256").update(verifier).digest("base64url");
}

/**
 * Beschränkt das Rückkehrziel auf einen Pfad dieser Anwendung.
 *
 * Ohne diese Prüfung wäre die Anmeldung ein offener Weiterleiter: ein Link auf
 * `/anmeldung?ziel=https://woanders.invalid` schickte den Menschen nach der
 * Anmeldung dorthin — mit der Glaubwürdigkeit der eigenen Adresse davor.
 *
 * Zugelassen ist deshalb nur ein Pfad, der mit genau einem Schrägstrich beginnt.
 * Das schliesst `//fremd.invalid` mit aus, was ein Browser als absolute Adresse
 * mit übernommenem Schema liest.
 */
export function sicheresZiel(ziel: string | null | undefined): string {
  if (!ziel || !ziel.startsWith("/") || ziel.startsWith("//")) {
    return "/";
  }
  return ziel;
}
