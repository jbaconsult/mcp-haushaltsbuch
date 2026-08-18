"use client";

import { useState } from "react";

import type { Institut } from "@/lib/bank";

/**
 * Startet die Autorisierung eines Bankzugangs.
 *
 * Client Component, weil hier ein Klick verarbeitet wird. Der Aufruf geht über
 * den BFF; die Antwort enthält die Adresse des Instituts, an die der Browser
 * weitergeleitet wird.
 *
 * Der Zustandswert für die Rückleitung entsteht dabei im Backend und ist hier
 * unbekannt. Er wird an Zugang und angemeldeten Benutzer gebunden - im Browser
 * hätte er nichts zu suchen.
 */
export function Zugangsknopf({
  institute,
  fehler,
}: {
  institute: Institut[];
  fehler: string | null;
}) {
  const [gewaehlt, setGewaehlt] = useState<string>("");
  const [laeuft, setLaeuft] = useState(false);
  const [meldung, setMeldung] = useState<string | null>(null);

  if (fehler) {
    return (
      <p className="text-sm text-gedaempft">
        Institute nicht abrufbar: {fehler}. Ist der Bankzugang konfiguriert? Siehe{" "}
        <code className="text-akzent">.env.example</code>.
      </p>
    );
  }

  if (institute.length === 0) {
    return (
      <p className="text-sm text-gedaempft">
        Keine Institute verfügbar. Ohne Anwendungs-ID und privaten Schlüssel gibt es keinen
        Anbieteraufruf — beides ist Konfiguration.
      </p>
    );
  }

  async function starten() {
    const institut = institute.find((kandidat) => kandidat.name === gewaehlt);
    if (!institut) {
      setMeldung("Bitte zuerst ein Institut wählen.");
      return;
    }

    setLaeuft(true);
    setMeldung(null);
    try {
      const antwort = await fetch("/api/bff/bankzugaenge", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ institutName: institut.name, institutLand: institut.land }),
      });

      const inhalt = (await antwort.json()) as { weiterleitung?: string; meldung?: string };
      if (!antwort.ok || !inhalt.weiterleitung) {
        setMeldung(inhalt.meldung ?? `Der Vorgang liess sich nicht starten (${antwort.status}).`);
        setLaeuft(false);
        return;
      }
      // Weiter zum Institut. Ab hier ist der Mensch dort, und der nächste Schritt
      // dieses Systems ist die Rückleitung.
      window.location.href = inhalt.weiterleitung;
    } catch {
      setMeldung("Das Backend ist nicht erreichbar.");
      setLaeuft(false);
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      <select
        value={gewaehlt}
        onChange={(ereignis) => setGewaehlt(ereignis.target.value)}
        className="rounded-lg border border-rand bg-flaeche px-3 py-2 text-sm"
        aria-label="Institut"
      >
        <option value="">Institut wählen …</option>
        {institute.map((institut) => (
          <option key={`${institut.name}-${institut.land}`} value={institut.name}>
            {institut.anzeigename} ({institut.land})
          </option>
        ))}
      </select>

      <button
        type="button"
        onClick={starten}
        disabled={laeuft || gewaehlt === ""}
        className="rounded-lg bg-akzent px-4 py-2 text-sm font-medium text-grund disabled:opacity-50"
      >
        {laeuft ? "Weiterleitung …" : "Bankzugang einrichten"}
      </button>

      {meldung && <span className="text-sm text-finanzamt">{meldung}</span>}
    </div>
  );
}
