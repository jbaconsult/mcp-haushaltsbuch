import { createCipheriv, createDecipheriv, hkdfSync, randomBytes, timingSafeEqual } from "node:crypto";

/**
 * Verschlüsselung und Echtheitsprüfung für alles, was der BFF in ein Cookie legt.
 *
 * Ein `httpOnly`-Cookie schützt gegen Skripte im Browser. Es schützt nicht gegen
 * jemanden, der den Cookie-Speicher liest — und der liegt als Datei auf dem
 * Endgerät. Ein Zugriffstoken im Klartext auf der Platte ist bei einer Anwendung,
 * die Kontostände führt, der falsche Kompromiss.
 *
 * AES-256-GCM leistet beides in einem Schritt: verschlüsseln und authentifizieren.
 * Ein verändertes Byte lässt die Entschlüsselung fehlschlagen, und der Aufrufer
 * bekommt `null` — dasselbe wie bei „kein Cookie". Die Unterscheidung zwischen
 * „manipuliert" und „nicht vorhanden" gehört nicht nach außen: Wer ein Cookie
 * fälscht, soll nicht erfahren, ob er nah dran war.
 *
 * Zwei getrennte Schlüssel aus demselben Geheimnis, abgeleitet über HKDF mit
 * unterschiedlichem `info`. Damit lässt sich ein Anmeldezustand nicht als Sitzung
 * ausgeben und umgekehrt — ein Angreifer, der irgendwo ein gültiges Siegel
 * erbeutet, kann es nicht an anderer Stelle einsetzen.
 */

/** Verwendungszwecke. Jeder bekommt seinen eigenen abgeleiteten Schlüssel. */
export type Zweck = "sitzung" | "anmeldezustand";

const FASSUNG = "v1";
const IV_BYTES = 12; // GCM-Norm. Abweichende Längen sind zulässig und schwächer.
const TAG_BYTES = 16;

/**
 * Das Geheimnis des BFF.
 *
 * Wird bei jedem Zugriff gelesen und nicht beim Laden des Moduls: In Next.js
 * werden Module auch beim Bauen ausgeführt, und dort ist die Umgebung eine
 * andere. Ein zur Bauzeit eingefrorener Wert wäre entweder leer oder falsch.
 */
function geheimnis(): string {
  const wert = process.env.BFF_SESSION_SECRET;
  if (!wert) {
    throw new Error(
      "BFF_SESSION_SECRET ist nicht gesetzt. Ohne dieses Geheimnis kann der BFF keine Sitzung führen.",
    );
  }
  return wert;
}

/**
 * Leitet den Schlüssel für einen Zweck ab.
 *
 * HKDF und nicht etwa ein SHA-256 über das Geheimnis: HKDF ist genau dafür
 * gedacht, aus einem Schlüsselmaterial mehrere unabhängige Schlüssel zu gewinnen.
 * Das Geheimnis selbst ist bereits hochentropisch (`openssl rand -base64 32`),
 * ein langsames Verfahren wie scrypt wäre hier Aufwand ohne Gegenwert.
 */
function schluessel(zweck: Zweck): Buffer {
  return Buffer.from(hkdfSync("sha256", geheimnis(), "haushaltsbuch-bff", zweck, 32));
}

/** Verschlüsselt und authentifiziert einen Wert. */
export function versiegeln(zweck: Zweck, klartext: string): string {
  const iv = randomBytes(IV_BYTES);
  const chiffre = createCipheriv("aes-256-gcm", schluessel(zweck), iv);

  const inhalt = Buffer.concat([chiffre.update(klartext, "utf8"), chiffre.final()]);

  return [
    FASSUNG,
    iv.toString("base64url"),
    chiffre.getAuthTag().toString("base64url"),
    inhalt.toString("base64url"),
  ].join(".");
}

/**
 * Prüft und entschlüsselt einen Wert.
 *
 * Liefert `null` für jeden Fehlerfall: falsche Fassung, unlesbare Kodierung,
 * fehlgeschlagene Echtheitsprüfung, falscher Zweck. Der Aufrufer behandelt alle
 * gleich, und das ist Absicht.
 */
export function entsiegeln(zweck: Zweck, gesiegelt: string | undefined): string | null {
  if (!gesiegelt) {
    return null;
  }

  const [fassung, ivRoh, markeRoh, inhaltRoh] = gesiegelt.split(".");
  if (fassung !== FASSUNG || !ivRoh || !markeRoh || inhaltRoh === undefined) {
    return null;
  }

  try {
    const iv = Buffer.from(ivRoh, "base64url");
    const marke = Buffer.from(markeRoh, "base64url");
    const inhalt = Buffer.from(inhaltRoh, "base64url");

    // Längen vor der Verwendung prüfen. createDecipheriv wirft bei falscher
    // IV-Länge zwar ohnehin, aber setAuthTag nimmt eine zu kurze Marke
    // klaglos an - und eine kurze Marke ist eine schwache Marke.
    if (iv.length !== IV_BYTES || marke.length !== TAG_BYTES) {
      return null;
    }

    const entschluessler = createDecipheriv("aes-256-gcm", schluessel(zweck), iv);
    entschluessler.setAuthTag(marke);

    return Buffer.concat([entschluessler.update(inhalt), entschluessler.final()]).toString("utf8");
  } catch {
    // Fehlgeschlagene Echtheitsprüfung landet hier. Kein Protokolleintrag mit
    // Inhalt: Was jemand einzuschleusen versucht, gehört nicht ins Log.
    return null;
  }
}

/**
 * Vergleicht zwei Zeichenketten ohne Zeitunterschied.
 *
 * Für den Abgleich des `state`. Ein Vergleich mit `===` bricht beim ersten
 * abweichenden Zeichen ab; aus der Laufzeit ließe sich der erwartete Wert Zeichen
 * für Zeichen erraten. Bei einem kurzlebigen Einmalwert ist das ein schmaler
 * Angriffspfad, aber der Aufwand für die richtige Bauweise ist eine Zeile.
 */
export function gleich(einer: string, anderer: string): boolean {
  const a = Buffer.from(einer, "utf8");
  const b = Buffer.from(anderer, "utf8");

  // timingSafeEqual verlangt gleiche Länge und verrät sie damit. Die Länge ist
  // hier kein Geheimnis - beide Werte sind Zufall fester Länge.
  return a.length === b.length && timingSafeEqual(a, b);
}
