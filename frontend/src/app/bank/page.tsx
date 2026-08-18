import Link from "next/link";

import { backendHolen } from "@/lib/backend-server";
import {
  SALDENART_BESCHRIFTUNG,
  betragAnzeigen,
  zeitpunktAnzeigen,
  type Bankzugang,
  type ExternesKonto,
  type Institut,
} from "@/lib/bank";

import { Zugangsknopf } from "./Zugangsknopf";
import { Zugangsliste } from "./Zugangsliste";

/**
 * Bankzugänge und die von der Bank gemeldeten Konten.
 *
 * Server Component: sie ruft das Backend serverseitig auf. Bewusst schmal - eine
 * Liste, ein Knopf, eine Detailansicht. Keine Diagramme und keine Auswertungen;
 * dies ist ein Durchstich, kein Dashboard.
 */

export default async function Bankseite() {
  const [zugaenge, konten, institute] = await Promise.all([
    backendHolen<Bankzugang[]>(["bankzugaenge"]),
    backendHolen<ExternesKonto[]>(["bankzugaenge", "konten"]),
    backendHolen<Institut[]>(["bankzugaenge", "institute"]),
  ]);

  return (
    <main className="mx-auto max-w-4xl px-6 py-16">
      <header className="mb-12">
        <h1 className="text-3xl font-semibold tracking-tight">MCP-Haushaltsbuch</h1>
        <p className="mt-2 text-gedaempft">
          Bankzugänge und die Konten, die eine Bank an dieses System gemeldet hat.
        </p>
        <Link href="/" className="mt-4 inline-block text-sm text-akzent hover:underline">
          ← Zur Kontenübersicht
        </Link>
      </header>

      <section className="mb-12">
        <h2 className="mb-4 text-sm font-medium uppercase tracking-wider text-gedaempft">
          Bankzugänge
        </h2>

        {zugaenge.fehler ? (
          <Hinweis>{zugaenge.fehler}</Hinweis>
        ) : !zugaenge.daten || zugaenge.daten.length === 0 ? (
          <Hinweis>
            Noch kein Bankzugang eingerichtet. Unten lässt sich einer anlegen — dabei führt der
            Weg über die Anmeldung beim Institut.
          </Hinweis>
        ) : (
          <Zugangsliste zugaenge={zugaenge.daten} />
        )}

        <div className="mt-6">
          <Zugangsknopf institute={institute.daten ?? []} fehler={institute.fehler} />
        </div>
      </section>

      <section>
        <h2 className="mb-4 text-sm font-medium uppercase tracking-wider text-gedaempft">
          Konten
        </h2>

        {konten.fehler ? (
          <Hinweis>{konten.fehler}</Hinweis>
        ) : !konten.daten || konten.daten.length === 0 ? (
          <Hinweis>
            Keine Konten bekannt. Sie entstehen, sobald ein Bankzugang autorisiert ist.
          </Hinweis>
        ) : (
          <ul className="divide-y divide-rand overflow-hidden rounded-lg border border-rand bg-flaeche">
            {konten.daten.map((konto) => (
              <li key={konto.id} className="px-5 py-4">
                <div className="flex items-baseline justify-between gap-4">
                  <div>
                    <Link
                      href={`/bank/konten/${konto.id}`}
                      className="font-medium hover:text-akzent hover:underline"
                    >
                      {konto.bezeichnung}
                    </Link>
                    <div className="text-sm text-gedaempft">
                      {konto.iban ?? konto.produktname ?? konto.waehrung}
                    </div>
                  </div>
                  <div className="text-right">
                    {konto.salden.length === 0 ? (
                      <span className="text-sm text-gedaempft">kein Saldo abgerufen</span>
                    ) : (
                      konto.salden.slice(0, 1).map((saldo) => (
                        <div key={saldo.art}>
                          <div className="font-medium tabular-nums">
                            {betragAnzeigen(saldo.betrag, saldo.waehrung)}
                          </div>
                          <div className="text-sm text-gedaempft">
                            {SALDENART_BESCHRIFTUNG[saldo.art]}, abgerufen{" "}
                            {zeitpunktAnzeigen(saldo.abgerufenAm)}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}

function Hinweis({ children }: { children: React.ReactNode }) {
  return (
    <p className="rounded-lg border border-rand bg-flaeche px-5 py-4 text-sm text-gedaempft">
      {children}
    </p>
  );
}
