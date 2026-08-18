import Link from "next/link";

import { STATUS_BESCHRIFTUNG, backendHolen, type Bankzugang } from "@/lib/bank";

/**
 * Rückleitung nach der Autorisierung beim Institut.
 *
 * Die Seite liegt bewusst im Frontend und nicht als Backend-Endpunkt: hier landet
 * ein Mensch im Browser, hier ist die Anmeldesitzung bekannt, und hier endet ein
 * Abbruch mit einer verständlichen Meldung statt mit einer weißen Seite.
 *
 * Der Autorisierungscode wird von hier an das Backend gegeben und dort
 * serverseitig gegen eine Sitzung getauscht. Er erreicht niemals einen
 * Anbieteraufruf aus dem Browser heraus.
 */

type Suchparameter = { code?: string; state?: string; error?: string; error_description?: string };

export default async function Rueckleitung({
  searchParams,
}: {
  searchParams: Promise<Suchparameter>;
}) {
  const parameter = await searchParams;

  if (!parameter.state) {
    return (
      <Rahmen titel="Vorgang nicht zuzuordnen">
        <p className="text-gedaempft">
          Die Rückleitung enthält keinen Vorgangsbezug. Ohne ihn lässt sich nicht feststellen,
          welche Einrichtung gemeint war — und ein Zugang wird nicht eingerichtet.
        </p>
      </Rahmen>
    );
  }

  // Ein Fehler des Instituts wird genauso weitergereicht wie ein Erfolg: der
  // Bankzugang bekommt einen sichtbaren Fehlzustand samt Meldung, statt still
  // liegenzubleiben.
  const rumpf = parameter.error
    ? {
        zustand: parameter.state,
        fehler: parameter.error,
        fehlerbeschreibung: parameter.error_description ?? null,
        code: null,
      }
    : { zustand: parameter.state, code: parameter.code ?? null, fehler: null, fehlerbeschreibung: null };

  const { daten, fehler } = await backendHolen<Bankzugang>(["bankzugaenge", "rueckleitung"], {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(rumpf),
  });

  if (fehler || !daten) {
    return (
      <Rahmen titel="Der Zugang wurde nicht eingerichtet">
        <p className="text-gedaempft">{fehler ?? "Unbekannter Fehler."}</p>
        <p className="mt-4 text-sm text-gedaempft">
          Häufigster Grund: der Vorgang ist abgelaufen oder wurde bereits abgeschlossen. Ein
          erneuter Versuch beginnt auf der Bankseite von vorn.
        </p>
      </Rahmen>
    );
  }

  const erfolgreich = daten.status === "AUTORISIERT";

  return (
    <Rahmen titel={erfolgreich ? "Bankzugang eingerichtet" : "Der Vorgang ist nicht durchgelaufen"}>
      <dl className="space-y-2 text-sm">
        <Zeile bezeichnung="Institut" wert={daten.institut} />
        <Zeile bezeichnung="Status" wert={STATUS_BESCHRIFTUNG[daten.status]} />
        {daten.restgueltigkeitTage !== null && (
          <Zeile bezeichnung="Gültig" wert={`noch ${daten.restgueltigkeitTage} Tage`} />
        )}
      </dl>

      {daten.fehlermeldung && (
        <p className="mt-4 rounded-lg border border-rand bg-flaeche px-4 py-3 text-sm text-finanzamt">
          Meldung des Anbieters: {daten.fehlermeldung}
        </p>
      )}

      {erfolgreich && (
        <p className="mt-4 text-sm text-gedaempft">
          Konten und Salden wurden abgerufen und liegen im Bestand.
        </p>
      )}
    </Rahmen>
  );
}

function Rahmen({ titel, children }: { titel: string; children: React.ReactNode }) {
  return (
    <main className="mx-auto max-w-2xl px-6 py-16">
      <h1 className="mb-6 text-2xl font-semibold tracking-tight">{titel}</h1>
      {children}
      <Link href="/bank" className="mt-8 inline-block text-sm text-akzent hover:underline">
        ← Zu den Bankzugängen
      </Link>
    </main>
  );
}

function Zeile({ bezeichnung, wert }: { bezeichnung: string; wert: string }) {
  return (
    <div className="flex justify-between border-b border-rand pb-2">
      <dt className="text-gedaempft">{bezeichnung}</dt>
      <dd className="font-medium">{wert}</dd>
    </div>
  );
}
