import { beforeEach, describe, expect, it } from "vitest";

import { entsiegeln, gleich, versiegeln } from "./siegel";

/**
 * Prüft die Versiegelung, auf der Sitzung und Anmeldezustand beruhen.
 *
 * Abnahmekriterium 4 des Auftrags hängt hier: Ein manipuliertes Cookie muss wie
 * kein Cookie behandelt werden — nicht wie ein Fehler und erst recht nicht wie
 * eine gültige Sitzung.
 */

beforeEach(() => {
  process.env.BFF_SESSION_SECRET = "test-geheimnis-mit-genug-entropie-fuer-hkdf";
});

describe("versiegeln und entsiegeln", () => {
  it("gibt zurück, was hineingegeben wurde", () => {
    const gesiegelt = versiegeln("sitzung", '{"zugriffstoken":"abc"}');

    expect(entsiegeln("sitzung", gesiegelt)).toBe('{"zugriffstoken":"abc"}');
  });

  it("legt den Klartext nicht offen", () => {
    const gesiegelt = versiegeln("sitzung", "streng-geheimes-zugriffstoken");

    expect(gesiegelt).not.toContain("streng-geheimes-zugriffstoken");
  });

  it("erzeugt bei gleichem Inhalt verschiedene Siegel", () => {
    // Sonst liesse sich an einem unveränderten Cookie ablesen, dass sich die
    // Sitzung nicht geändert hat - und zwei gleiche Nonces brechen GCM.
    expect(versiegeln("sitzung", "gleich")).not.toBe(versiegeln("sitzung", "gleich"));
  });
});

describe("ein manipuliertes Siegel gilt als nicht vorhanden", () => {
  it("lehnt einen veränderten Inhalt ab", () => {
    const gesiegelt = versiegeln("sitzung", '{"zugriffstoken":"echt"}');
    const teile = gesiegelt.split(".");
    // Ein einziges Byte im Geheimtext kippen.
    const verdreht = Buffer.from(teile[3]!, "base64url");
    verdreht.writeUInt8(verdreht.readUInt8(0) ^ 0xff, 0);
    teile[3] = verdreht.toString("base64url");

    expect(entsiegeln("sitzung", teile.join("."))).toBeNull();
  });

  it("lehnt eine veränderte Echtheitsmarke ab", () => {
    const teile = versiegeln("sitzung", "inhalt").split(".");
    const marke = Buffer.from(teile[2]!, "base64url");
    marke.writeUInt8(marke.readUInt8(0) ^ 0xff, 0);
    teile[2] = marke.toString("base64url");

    expect(entsiegeln("sitzung", teile.join("."))).toBeNull();
  });

  it("lehnt ein Siegel des anderen Zwecks ab", () => {
    // Ein erbeuteter Anmeldezustand darf sich nicht als Sitzung ausgeben lassen.
    const anmeldung = versiegeln("anmeldezustand", "inhalt");

    expect(entsiegeln("sitzung", anmeldung)).toBeNull();
  });

  it("lehnt ein Siegel unter fremdem Geheimnis ab", () => {
    const fremd = versiegeln("sitzung", "inhalt");
    process.env.BFF_SESSION_SECRET = "ein-ganz-anderes-geheimnis";

    expect(entsiegeln("sitzung", fremd)).toBeNull();
  });

  it.each([
    ["leer", ""],
    ["nicht gesetzt", undefined],
    ["kein Siegel", "einfach-nur-text"],
    ["falsche Fassung", "v9.aaaa.bbbb.cccc"],
    ["zu wenige Teile", "v1.aaaa.bbbb"],
    ["unbrauchbare Kodierung", "v1.!!!.###.$$$"],
  ])("lehnt %s ab", (_beschreibung, wert) => {
    expect(entsiegeln("sitzung", wert)).toBeNull();
  });

  it("lehnt eine gekürzte Echtheitsmarke ab", () => {
    // setAuthTag nimmt eine kurze Marke klaglos an; die Längenprüfung fängt das.
    const teile = versiegeln("sitzung", "inhalt").split(".");
    teile[2] = Buffer.from(teile[2]!, "base64url").subarray(0, 8).toString("base64url");

    expect(entsiegeln("sitzung", teile.join("."))).toBeNull();
  });
});

describe("gleich", () => {
  it("erkennt Gleichheit", () => {
    expect(gleich("derselbe-wert", "derselbe-wert")).toBe(true);
  });

  it.each([
    ["ein anderer Wert", "ein-anderer-wert"],
    ["ein Präfix", "derselbe"],
    ["leer", ""],
  ])("erkennt Ungleichheit gegen %s", (_beschreibung, anderer) => {
    expect(gleich("derselbe-wert", anderer)).toBe(false);
  });
});
