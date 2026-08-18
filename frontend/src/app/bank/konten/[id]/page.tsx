import Link from "next/link";

import {
  SALDENART_BESCHRIFTUNG,
  STATUS_BESCHRIFTUNG,
  backendHolen,
  betragAnzeigen,
  zeitpunktAnzeigen,
  type Bankzugang,
  type ExternesKonto,
} from "@/lib/bank";

/**
 * Ein einzelnes von der Bank gemeldetes Konto.
 *
 * Zeigt alle bekannten Salden mit ihrem Abrufzeitpunkt und den Zustand des
 * zugehörigen Bankzugangs. Letzteres gehört dazu: ist der Zugang abgelaufen, sind
 * die Zahlen die zuletzt bekannten und nicht die heutigen. Sie sind dadurch nicht
 * falsch, aber alt — und wer das nicht sieht, entscheidet auf veralteter Grundlage.
 */
export default async function Kontoseite({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  const { daten: konto, fehler } = await backendHolen<ExternesKonto>(["bankzugaenge", "konten", id]);

  if (fehler || !konto) {
    return (
      <main className="mx-auto max-w-2xl px-6 py-16">
        <h1 className="mb-4 text-2xl font-semibold tracking-tight">Konto nicht gefunden</h1>
        <p className="text-gedaempft">
          {fehler ?? "Entweder gibt es dieses Konto nicht, oder es ist nicht sichtbar."}
        </p>
        <Link href="/bank" className="mt-8 inline-block text-sm text-akzent hover:underline">
          ← Zu den Bankzugängen
        </Link>
      </main>
    );
  }

  const { daten: zugaenge } = await backendHolen<Bankzugang[]>(["bankzugaenge"]);
  const zugang = zugaenge?.find((kandidat) => kandidat.id === konto.bankzugangId) ?? null;

  return (
    <main className="mx-auto max-w-2xl px-6 py-16">
      <header className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight">{konto.bezeichnung}</h1>
        <p className="mt-1 text-gedaempft">{konto.iban ?? konto.produktname ?? konto.waehrung}</p>
      </header>

      <section className="mb-8">
        <h2 className="mb-3 text-sm font-medium uppercase tracking-wider text-gedaempft">Konto</h2>
        <dl className="space-y-2 text-sm">
          <Zeile bezeichnung="Währung" wert={konto.waehrung} />
          {konto.kontoart && <Zeile bezeichnung="Kontoart laut Bank" wert={konto.kontoart} />}
          {konto.produktname && <Zeile bezeichnung="Produkt" wert={konto.produktname} />}
          <Zeile bezeichnung="Kennung" wert={konto.kennung} />
          <Zeile
            bezeichnung="Zugeordnetes Konto"
            wert={konto.zugeordnetesKonto ?? "keines — wird von Hand gesetzt"}
          />
        </dl>
      </section>

      {zugang && (
        <section className="mb-8">
          <h2 className="mb-3 text-sm font-medium uppercase tracking-wider text-gedaempft">
            Bankzugang
          </h2>
          <dl className="space-y-2 text-sm">
            <Zeile bezeichnung="Institut" wert={zugang.institut} />
            <Zeile bezeichnung="Status" wert={STATUS_BESCHRIFTUNG[zugang.status]} />
            {zugang.restgueltigkeitTage !== null && (
              <Zeile bezeichnung="Gültig" wert={`noch ${zugang.restgueltigkeitTage} Tage`} />
            )}
          </dl>
          {zugang.status !== "AUTORISIERT" && (
            <p className="mt-3 text-sm text-finanzamt">
              Dieser Zugang ist nicht mehr nutzbar. Die Salden unten sind die zuletzt abgerufenen
              und werden nicht mehr aktualisiert.
            </p>
          )}
        </section>
      )}

      <section>
        <h2 className="mb-3 text-sm font-medium uppercase tracking-wider text-gedaempft">
          Salden
        </h2>
        {konto.salden.length === 0 ? (
          <p className="text-sm text-gedaempft">Es wurde noch kein Saldo abgerufen.</p>
        ) : (
          <ul className="divide-y divide-rand overflow-hidden rounded-lg border border-rand bg-flaeche">
            {konto.salden.map((saldo, stelle) => (
              <li key={`${saldo.art}-${saldo.abgerufenAm}-${stelle}`} className="px-5 py-3">
                <div className="flex items-baseline justify-between gap-4">
                  <div>
                    <div className="text-sm font-medium">
                      {SALDENART_BESCHRIFTUNG[saldo.art]}
                      {saldo.art === "SONSTIGE" && ` (${saldo.artOriginal})`}
                    </div>
                    <div className="text-sm text-gedaempft">
                      Stand {saldo.referenzdatum ?? "ohne Angabe"}, abgerufen{" "}
                      {zeitpunktAnzeigen(saldo.abgerufenAm)}
                    </div>
                  </div>
                  <span className="font-medium tabular-nums">
                    {betragAnzeigen(saldo.betrag, saldo.waehrung)}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

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
