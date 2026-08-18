"use client";

import { useState } from "react";

import type { Bankzugang } from "@/lib/bank";

import { Zugangszeile } from "./Zugangszeile";

/**
 * Die Liste der Bankzugänge samt Platz für das, was nach dem Entfernen übrig bleibt.
 *
 * Der Hinweis lebt hier und nicht in der Zeile: eine Meldung des Anbieters entsteht
 * genau in dem Moment, in dem ihre Zeile verschwindet. Wer sie dort anzeigt, zeigt
 * sie für den Bruchteil einer Sekunde.
 */
export function Zugangsliste({ zugaenge }: { zugaenge: Bankzugang[] }) {
  const [hinweis, setHinweis] = useState<string | null>(null);

  return (
    <>
      {hinweis && (
        <div className="mb-4 rounded-lg border border-rand bg-flaeche px-5 py-4">
          <p className="text-sm text-finanzamt">
            Der Zugang wurde entfernt, aber die Autorisierung beim Anbieter konnte nicht widerrufen
            werden: {hinweis}
          </p>
          <p className="mt-2 text-sm text-gedaempft">
            Sie läuft dort bis zum Ablauf ihrer Gültigkeit weiter. Wer sie sofort beenden will, tut
            das in der Verwaltung des Instituts.
          </p>
        </div>
      )}

      <ul className="divide-y divide-rand overflow-hidden rounded-lg border border-rand bg-flaeche">
        {zugaenge.map((zugang) => (
          <Zugangszeile key={zugang.id} zugang={zugang} onEntfernt={setHinweis} />
        ))}
      </ul>
    </>
  );
}
