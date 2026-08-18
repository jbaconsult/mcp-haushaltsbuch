import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Prüft Sitzungsinhalt und Auffrischung.
 *
 * Abnahmekriterium 4: Ein manipuliertes Cookie wird wie keine Sitzung behandelt.
 * Abnahmekriterium 6: Ein abgelaufenes Zugriffstoken wird aufgefrischt, ohne dass
 * der Mensch etwas bemerkt — nachgewiesen mit **verkürzter Lebensdauer** statt
 * durch Warten. Ein Test, der fünf Minuten schläft, wird nach dem zweiten Mal
 * übersprungen und prüft danach gar nichts mehr.
 */

const auffrischen = vi.fn();

vi.mock("@/lib/oidc", () => ({
  tokenAuffrischen: (token: string) => auffrischen(token),
}));

/** Ein Cookie-Speicher im Arbeitsspeicher, wie ihn next/headers liefern würde. */
const speicher = new Map<string, string>();

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) => (speicher.has(name) ? { value: speicher.get(name) } : undefined),
    set: (name: string, wert: string) => void speicher.set(name, wert),
    delete: (name: string) => void speicher.delete(name),
  }),
}));

const { aktuelleSitzung, ausTokenantwort, sitzungAusCookie, sitzungSetzen } = await import("./sitzung");
const { versiegeln } = await import("./siegel");

const COOKIE = "hb_sitzung";

beforeEach(() => {
  process.env.BFF_SESSION_SECRET = "test-geheimnis-mit-genug-entropie-fuer-hkdf";
  speicher.clear();
  auffrischen.mockReset();
});

describe("der Cookie-Inhalt", () => {
  it("wird gelesen, wenn er echt ist", () => {
    const inhalt = versiegeln("sitzung", JSON.stringify({ zugriffstoken: "abc", laeuftAbUm: 42 }));

    expect(sitzungAusCookie(inhalt)).toEqual({ zugriffstoken: "abc", laeuftAbUm: 42 });
  });

  it("gilt als nicht vorhanden, wenn er manipuliert wurde", () => {
    const teile = versiegeln("sitzung", JSON.stringify({ zugriffstoken: "abc", laeuftAbUm: 42 })).split(".");
    const verdreht = Buffer.from(teile[3]!, "base64url");
    verdreht.writeUInt8(verdreht.readUInt8(0) ^ 0xff, 0);
    teile[3] = verdreht.toString("base64url");

    expect(sitzungAusCookie(teile.join("."))).toBeNull();
  });

  it("gilt als nicht vorhanden, wenn er unverschlüsselt ist", () => {
    // Der Zustand vor diesem Auftrag. Ein altes Cookie soll nicht plötzlich als
    // Sitzung durchgehen, sondern zur Neuanmeldung führen.
    expect(sitzungAusCookie(JSON.stringify({ zugriffstoken: "abc", laeuftAbUm: 42 }))).toBeNull();
  });

  it("gilt als nicht vorhanden, wenn Pflichtfelder fehlen", () => {
    expect(sitzungAusCookie(versiegeln("sitzung", JSON.stringify({ irgendwas: true })))).toBeNull();
  });
});

describe("die Auffrischung", () => {
  it("erneuert ein bald ablaufendes Token, ohne dass jemand etwas merkt", async () => {
    // Verkürzte Lebensdauer: Das Token läuft in zehn Sekunden ab und liegt damit
    // im Vorlauffenster von einer Minute.
    await sitzungSetzen({
      zugriffstoken: "altes-token",
      auffrischungstoken: "auffrischung-eins",
      laeuftAbUm: Date.now() + 10_000,
      auffrischungLaeuftAbUm: Date.now() + 1_800_000,
    });

    auffrischen.mockResolvedValue({
      access_token: "frisches-token",
      refresh_token: "auffrischung-zwei",
      expires_in: 300,
      refresh_expires_in: 1800,
      token_type: "Bearer",
    });

    const sitzung = await aktuelleSitzung();

    expect(auffrischen).toHaveBeenCalledWith("auffrischung-eins");
    expect(sitzung?.zugriffstoken).toBe("frisches-token");
    expect(sitzung?.laeuftAbUm).toBeGreaterThan(Date.now() + 250_000);

    // Und das Ergebnis liegt im Cookie, nicht nur in der Rückgabe - sonst
    // frischte jede Anfrage erneut auf.
    expect(sitzungAusCookie(speicher.get(COOKIE))?.zugriffstoken).toBe("frisches-token");
  });

  it("lässt ein noch lange gültiges Token in Ruhe", async () => {
    await sitzungSetzen({
      zugriffstoken: "gueltiges-token",
      auffrischungstoken: "auffrischung-eins",
      laeuftAbUm: Date.now() + 300_000,
    });

    const sitzung = await aktuelleSitzung();

    expect(auffrischen).not.toHaveBeenCalled();
    expect(sitzung?.zugriffstoken).toBe("gueltiges-token");
  });

  it("beendet die Sitzung, wenn auch das Auffrischungstoken abgelaufen ist", async () => {
    await sitzungSetzen({
      zugriffstoken: "altes-token",
      auffrischungstoken: "abgelaufene-auffrischung",
      laeuftAbUm: Date.now() + 10_000,
      auffrischungLaeuftAbUm: Date.now() + 20_000,
    });

    // So antwortet der Identity Provider auf ein totes Auffrischungstoken.
    auffrischen.mockResolvedValue(null);

    expect(await aktuelleSitzung()).toBeNull();
    expect(speicher.has(COOKIE)).toBe(false);
    // Kein Fehler, keine Ausnahme: Der nächste Aufruf führt zur Anmeldung.
  });

  it("beendet die Sitzung, wenn es gar kein Auffrischungstoken gibt", async () => {
    await sitzungSetzen({ zugriffstoken: "altes-token", laeuftAbUm: Date.now() + 10_000 });

    expect(await aktuelleSitzung()).toBeNull();
    expect(auffrischen).not.toHaveBeenCalled();
  });

  it("liefert null ohne jede Sitzung", async () => {
    expect(await aktuelleSitzung()).toBeNull();
  });
});

describe("ausTokenantwort", () => {
  it("rechnet die Lebensdauern in Zeitpunkte um", () => {
    const vorher = Date.now();
    const sitzung = ausTokenantwort(
      {
        access_token: "abc",
        refresh_token: "def",
        expires_in: 300,
        refresh_expires_in: 1800,
        token_type: "Bearer",
      },
      "sub-eins",
    );

    expect(sitzung.laeuftAbUm).toBeGreaterThanOrEqual(vorher + 300_000);
    expect(sitzung.auffrischungLaeuftAbUm).toBeGreaterThanOrEqual(vorher + 1_800_000);
    expect(sitzung.subjekt).toBe("sub-eins");
  });

  it("kommt ohne Auffrischungstoken aus", () => {
    const sitzung = ausTokenantwort({ access_token: "abc", expires_in: 300, token_type: "Bearer" });

    expect(sitzung.auffrischungstoken).toBeUndefined();
    expect(sitzung.auffrischungLaeuftAbUm).toBeUndefined();
  });
});

describe("das Cookie", () => {
  it("lebt so lange wie das Auffrischungstoken, nicht wie das Zugriffstoken", async () => {
    // Ein Cookie mit der Lebensdauer des Zugriffstokens wäre nach fünf Minuten
    // verschwunden, und die Auffrischung hätte nichts mehr, womit sie arbeiten
    // könnte. Geprüft am Inhalt: das Auffrischungstoken muss erhalten bleiben.
    await sitzungSetzen({
      zugriffstoken: "abc",
      auffrischungstoken: "def",
      laeuftAbUm: Date.now() + 300_000,
      auffrischungLaeuftAbUm: Date.now() + 1_800_000,
    });

    expect(sitzungAusCookie(speicher.get(COOKIE))?.auffrischungstoken).toBe("def");
  });

  it("enthält das Token nicht im Klartext", async () => {
    await sitzungSetzen({ zugriffstoken: "streng-geheim", laeuftAbUm: Date.now() + 300_000 });

    expect(speicher.get(COOKIE)).not.toContain("streng-geheim");
  });
});
