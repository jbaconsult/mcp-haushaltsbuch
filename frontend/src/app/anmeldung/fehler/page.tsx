import Link from "next/link";

/**
 * Was schiefging, in verständlichen Worten.
 *
 * Die Rückleitung ist ein Route Handler und kann nichts darstellen; sie leitet
 * bei einem Fehlschlag hierher. Ein abgebrochener Anmeldevorgang endet damit an
 * einer erklärten Stelle statt an einer weissen Seite.
 *
 * Die Gründe sind bewusst grob. „Unbekannt, verbraucht oder abgelaufen" wird
 * nicht aufgeschlüsselt — wer einen Zustandswert errät, soll nicht erfahren, wie
 * nah er dran war.
 */

const GRUENDE: Record<string, { titel: string; text: string }> = {
  zustand: {
    titel: "Der Anmeldevorgang ist nicht zuzuordnen",
    text:
      "Der Vorgang ist abgelaufen, wurde bereits abgeschlossen oder gehört nicht zu diesem Browser. " +
      "Ein erneuter Versuch beginnt von vorn.",
  },
  aussteller: {
    titel: "Die Antwort kam von der falschen Stelle",
    text:
      "Der Aussteller der Anmeldeantwort ist nicht der konfigurierte Identity Provider. " +
      "Die Anmeldung wurde deshalb abgebrochen und keine Sitzung eingerichtet.",
  },
  tausch: {
    titel: "Die Anmeldung liess sich nicht abschliessen",
    text:
      "Der Identity Provider hat den Autorisierungscode nicht angenommen. Häufigster Grund: " +
      "der Code ist abgelaufen oder wurde schon eingelöst.",
  },
  abgelehnt: {
    titel: "Die Anmeldung wurde abgebrochen",
    text: "Der Identity Provider hat den Vorgang beendet.",
  },
};

const UNBEKANNT = {
  titel: "Die Anmeldung ist nicht durchgelaufen",
  text: "Es wurde keine Sitzung eingerichtet.",
};

export default async function Anmeldefehler({
  searchParams,
}: {
  searchParams: Promise<{ grund?: string; meldung?: string }>;
}) {
  const { grund, meldung } = await searchParams;
  const erklaerung = (grund && GRUENDE[grund]) || UNBEKANNT;

  return (
    <main className="mx-auto max-w-2xl px-6 py-16">
      <h1 className="mb-6 text-2xl font-semibold tracking-tight">{erklaerung.titel}</h1>
      <p className="text-gedaempft">{erklaerung.text}</p>

      {meldung && (
        <p className="mt-4 rounded-lg border border-rand bg-flaeche px-4 py-3 text-sm text-finanzamt">
          Meldung des Identity Providers: {meldung}
        </p>
      )}

      <div className="mt-8 flex gap-4 text-sm">
        <Link href="/anmeldung" className="text-akzent hover:underline">
          Erneut anmelden
        </Link>
        <Link href="/" className="text-gedaempft hover:underline">
          Zur Startseite
        </Link>
      </div>
    </main>
  );
}
