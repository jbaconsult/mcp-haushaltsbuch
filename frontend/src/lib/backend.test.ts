import { afterEach, describe, expect, it } from "vitest";

import { backendBasis, backendUrl } from "./backend";

/**
 * Tests der Backend-Adressierung.
 *
 * Der interessante Teil ist die Pfadprüfung: die Segmente stammen aus der
 * Anfrage-URL des Browsers, laufen also über eine Aussengrenze.
 */
describe("backendUrl", () => {
  const urspruenglicheBasis = process.env.BACKEND_BASE_URL;

  afterEach(() => {
    if (urspruenglicheBasis === undefined) {
      delete process.env.BACKEND_BASE_URL;
    } else {
      process.env.BACKEND_BASE_URL = urspruenglicheBasis;
    }
  });

  it("setzt Pfadsegmente zusammen", () => {
    process.env.BACKEND_BASE_URL = "http://backend:8080";

    expect(backendUrl(["konten"])).toBe("http://backend:8080/api/konten");
    expect(backendUrl(["konten", "abc-123"])).toBe("http://backend:8080/api/konten/abc-123");
  });

  it("haengt Suchparameter an", () => {
    process.env.BACKEND_BASE_URL = "http://backend:8080";

    expect(backendUrl(["konten"], "sphaere=PRIVAT")).toBe(
      "http://backend:8080/api/konten?sphaere=PRIVAT",
    );
  });

  it("weist Pfadwechsel zurueck", () => {
    // Ohne diese Pruefung kaeme man aus /api/ heraus und erreichte beliebige
    // Backend-Pfade - etwa die Verwaltungsendpunkte unter /q/.
    expect(() => backendUrl(["..", "..", "q", "dev"])).toThrow();
    expect(() => backendUrl(["konten/../../q"])).toThrow();
    expect(() => backendUrl(["konten\\..\\q"])).toThrow();
  });

  it("kodiert Sonderzeichen im Segment", () => {
    process.env.BACKEND_BASE_URL = "http://backend:8080";

    expect(backendUrl(["konten", "a b&c=d"])).toBe("http://backend:8080/api/konten/a%20b%26c%3Dd");
  });

  it("faellt auf localhost zurueck", () => {
    delete process.env.BACKEND_BASE_URL;

    expect(backendBasis()).toBe("http://localhost:8080");
  });
});
