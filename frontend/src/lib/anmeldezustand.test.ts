import { beforeEach, describe, expect, it } from "vitest";

import { pruefwertAus, sicheresZiel, zustandPruefen } from "./anmeldezustand";
import { versiegeln } from "./siegel";

/**
 * Prüft die Zustandshaltung des Anmeldevorgangs.
 *
 * Abnahmekriterium 5 des Auftrags: Eine Rückleitung mit unbekanntem,
 * verbrauchtem oder abgelaufenem `state` wird abgelehnt und erzeugt keine
 * Sitzung. Alle drei Fälle enden hier in `null`, und der Aufrufer kann daraus
 * nichts anderes machen als eine Ablehnung.
 */

const JETZT = 1_800_000_000_000;

beforeEach(() => {
  process.env.BFF_SESSION_SECRET = "test-geheimnis-mit-genug-entropie-fuer-hkdf";
});

function eintrag(felder: Partial<{ zustand: string; verifier: string; laeuftAbUm: number; ziel: string }> = {}) {
  return versiegeln(
    "anmeldezustand",
    JSON.stringify({
      zustand: "der-echte-zustand",
      verifier: "der-echte-verifier",
      laeuftAbUm: JETZT + 60_000,
      ziel: "/bank",
      ...felder,
    }),
  );
}

describe("ein gültiger Vorgang", () => {
  it("gibt Verifier und Ziel heraus", () => {
    expect(zustandPruefen(eintrag(), "der-echte-zustand", JETZT)).toEqual({
      verifier: "der-echte-verifier",
      ziel: "/bank",
    });
  });
});

describe("abgelehnt wird", () => {
  it("ein unbekannter Zustandswert - es läuft gar kein Vorgang", () => {
    // Das ist zugleich der Fall „verbraucht": zustandEinloesen löscht das Cookie,
    // bevor es prüft, also findet der zweite Aufruf nichts mehr vor.
    expect(zustandPruefen(undefined, "irgendein-zustand", JETZT)).toBeNull();
  });

  it("ein Zustandswert, der nicht zum abgelegten passt", () => {
    expect(zustandPruefen(eintrag(), "selbst-erfundener-zustand", JETZT)).toBeNull();
  });

  it("ein abgelaufener Vorgang", () => {
    expect(zustandPruefen(eintrag({ laeuftAbUm: JETZT - 1 }), "der-echte-zustand", JETZT)).toBeNull();
  });

  it("eine Rückleitung ganz ohne Zustandswert", () => {
    expect(zustandPruefen(eintrag(), null, JETZT)).toBeNull();
  });

  it("ein manipulierter Eintrag", () => {
    const teile = eintrag().split(".");
    const verdreht = Buffer.from(teile[3]!, "base64url");
    verdreht.writeUInt8(verdreht.readUInt8(0) ^ 0xff, 0);
    teile[3] = verdreht.toString("base64url");

    expect(zustandPruefen(teile.join("."), "der-echte-zustand", JETZT)).toBeNull();
  });

  it("ein Eintrag ohne Verifier", () => {
    const ohneVerifier = versiegeln(
      "anmeldezustand",
      JSON.stringify({ zustand: "der-echte-zustand", laeuftAbUm: JETZT + 60_000, ziel: "/" }),
    );

    expect(zustandPruefen(ohneVerifier, "der-echte-zustand", JETZT)).toBeNull();
  });
});

describe("das Rückkehrziel", () => {
  it.each([
    ["ein Pfad dieser Anwendung", "/bank/konten", "/bank/konten"],
    ["eine fremde Adresse", "https://woanders.invalid/", "/"],
    ["eine schemalose fremde Adresse", "//woanders.invalid/", "/"],
    ["nichts", null, "/"],
    ["ein leerer Wert", "", "/"],
  ])("macht aus %s das Ziel %s", (_beschreibung, eingabe, erwartet) => {
    // Ohne diese Beschränkung wäre die Anmeldung ein offener Weiterleiter: ein
    // Link auf /anmeldung?ziel=https://fremd.invalid schickte den Menschen nach
    // erfolgreicher Anmeldung dorthin, mit der eigenen Adresse als Vorspann.
    expect(sicheresZiel(eingabe)).toBe(erwartet);
  });

  it("wird auch beim Einlösen noch geprüft", () => {
    // Doppelt geprüft, weil das Cookie zwar versiegelt ist, aber ein Fehler beim
    // Anlegen sonst erst in der Weiterleitung sichtbar würde.
    expect(zustandPruefen(eintrag({ ziel: "https://woanders.invalid" }), "der-echte-zustand", JETZT)).toEqual({
      verifier: "der-echte-verifier",
      ziel: "/",
    });
  });
});

describe("PKCE", () => {
  it("bildet den Prüfwert nach S256", () => {
    // Beispiel aus RFC 7636, Anhang B - die einzige Stelle, an der ein fremder
    // Erwartungswert mehr wert ist als ein selbst erzeugter.
    expect(pruefwertAus("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")).toBe(
      "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
    );
  });
});
