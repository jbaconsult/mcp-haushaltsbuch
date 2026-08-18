"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import {
  STATUS_BESCHRIFTUNG,
  type Bankzugang,
  type Kontenbehandlung,
  type Zugangsentfernung,
} from "@/lib/bank";

/**
 * Ein Bankzugang in der Liste, mit dem Weg wieder heraus.
 *
 * Client Component, weil hier Klicks verarbeitet werden. Der Rest der Seite bleibt
 * serverseitig; nur diese Zeile braucht Zustand.
 *
 * Die Beschriftung folgt dem Status: solange die Autorisierung läuft, heisst es
 * „Vorgang abbrechen“, danach „Entfernen“. Dahinter steht derselbe Aufruf — der
 * Unterschied liegt allein darin, was es zu entfernen gibt.
 *
 * Eine Meldung des Anbieters wird nach oben gereicht statt hier angezeigt: nach dem
 * Entfernen ist diese Zeile verschwunden, und ein Hinweis, der mit seinem Träger
 * stirbt, ist keiner.
 */

const STATUSFARBE: Record<Bankzugang["status"], string> = {
  NICHT_AUTORISIERT: "text-gedaempft",
  AUTORISIERUNG_LAEUFT: "text-akzent",
  AUTORISIERT: "text-freiberuflich",
  ABGELAUFEN: "text-finanzamt",
  FEHLGESCHLAGEN: "text-finanzamt",
};

export function Zugangszeile({
  zugang,
  onEntfernt,
}: {
  zugang: Bankzugang;
  onEntfernt: (anbietermeldung: string | null) => void;
}) {
  const router = useRouter();
  const [fragt, setFragt] = useState(false);
  const [kontenLoeschen, setKontenLoeschen] = useState(false);
  const [laeuft, setLaeuft] = useState(false);
  const [meldung, setMeldung] = useState<string | null>(null);

  // Ein Vorgang, der nie zu einer Autorisierung geführt hat, hat keine Konten und
  // keine Salden. Die Frage danach wäre eine Wahl ohne Gegenstand.
  const istUnfertigerVorgang =
    zugang.status === "AUTORISIERUNG_LAEUFT" || zugang.status === "NICHT_AUTORISIERT";

  async function entfernen() {
    const behandlung: Kontenbehandlung =
      !istUnfertigerVorgang && kontenLoeschen ? "loeschen" : "behalten";

    setLaeuft(true);
    setMeldung(null);
    try {
      const antwort = await fetch(
        `/api/bff/bankzugaenge/${zugang.id}?konten=${behandlung}`,
        { method: "DELETE" },
      );

      if (!antwort.ok) {
        const inhalt = (await antwort.json().catch(() => null)) as { meldung?: string } | null;
        setMeldung(inhalt?.meldung ?? `Der Zugang liess sich nicht entfernen (${antwort.status}).`);
        setLaeuft(false);
        return;
      }

      const ergebnis = (await antwort.json()) as Zugangsentfernung;

      // Der Zugang ist weg — auch dann, wenn der Widerruf beim Anbieter scheiterte.
      // Das gehört gesagt, statt es unter einem stillen Erfolg zu begraben.
      onEntfernt(ergebnis.anbietermeldung);
      router.refresh();
    } catch {
      setMeldung("Das Backend ist nicht erreichbar.");
      setLaeuft(false);
    }
  }

  return (
    <li className="px-5 py-4">
      <div className="flex items-baseline justify-between gap-4">
        <div>
          <div className="font-medium">{zugang.institut}</div>
          <div className="text-sm text-gedaempft">{zugang.anbieter}</div>
        </div>
        <div className="text-right">
          <div className={`text-sm font-medium ${STATUSFARBE[zugang.status]}`}>
            {STATUS_BESCHRIFTUNG[zugang.status]}
          </div>
          {zugang.restgueltigkeitTage !== null && (
            <div className="text-sm text-gedaempft">
              noch {zugang.restgueltigkeitTage} Tage gültig
            </div>
          )}
        </div>
      </div>

      {zugang.fehlermeldung && (
        <p className="mt-2 text-sm text-finanzamt">{zugang.fehlermeldung}</p>
      )}

      {!fragt ? (
        <div className="mt-3">
          <button
            type="button"
            onClick={() => setFragt(true)}
            className="text-sm text-gedaempft underline-offset-4 hover:text-finanzamt hover:underline"
          >
            {istUnfertigerVorgang ? "Vorgang abbrechen" : "Zugang entfernen"}
          </button>
        </div>
      ) : (
        <div className="mt-3 rounded-lg border border-rand bg-grund px-4 py-3">
          <p className="text-sm">
            {istUnfertigerVorgang
              ? "Der Vorgang wird abgebrochen und verschwindet aus der Liste."
              : `Der Zugang zu ${zugang.institut} wird entfernt und die Autorisierung beim Anbieter widerrufen.`}
          </p>

          {!istUnfertigerVorgang && (
            <label className="mt-3 flex items-start gap-2 text-sm text-gedaempft">
              <input
                type="checkbox"
                checked={kontenLoeschen}
                onChange={(ereignis) => setKontenLoeschen(ereignis.target.checked)}
                className="mt-0.5"
              />
              <span>
                Auch die abgerufenen Konten und Salden entfernen.
                <span className="block text-xs">
                  Ohne Haken bleiben sie als Bestand stehen — ein Saldo von vor drei Monaten lässt
                  sich nicht neu abrufen.
                </span>
              </span>
            </label>
          )}

          <div className="mt-3 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={entfernen}
              disabled={laeuft}
              className="rounded-lg bg-finanzamt px-3 py-1.5 text-sm font-medium text-grund disabled:opacity-50"
            >
              {laeuft ? "Wird entfernt …" : istUnfertigerVorgang ? "Abbrechen" : "Entfernen"}
            </button>
            <button
              type="button"
              onClick={() => {
                setFragt(false);
                setKontenLoeschen(false);
                setMeldung(null);
              }}
              disabled={laeuft}
              className="text-sm text-gedaempft hover:underline disabled:opacity-50"
            >
              Doch nicht
            </button>
            {meldung && <span className="text-sm text-finanzamt">{meldung}</span>}
          </div>
        </div>
      )}
    </li>
  );
}
