import { KONTOART_BESCHRIFTUNG, SPHAERE_BESCHRIFTUNG, type Konto } from "@/lib/backend";
import { anmeldezustand } from "@/lib/anmeldung";
import { backendHolen } from "@/lib/backend-server";

import { Anmeldeleiste, Zuordnungshinweis } from "./Anmeldeleiste";

/**
 * Kontoübersicht.
 *
 * Server Component: sie ruft das Backend serverseitig auf, nicht über den BFF.
 * Der BFF ist für Anfragen aus dem Browser da; von hier aus wäre er ein Umweg
 * über die eigene Adresse.
 *
 * Der Aufruf geht über `backendHolen` und nicht über ein blankes `fetch`: nur so
 * wird das Zugriffstoken der Sitzung angehängt. Ohne diesen Schritt liefe die
 * Seite im Entwicklungsprofil durch - dort arbeitet das Backend ohne OIDC - und
 * in Produktion in ein 401.
 */

const SPHAERENFARBE: Record<Konto["sphaere"], string> = {
  PRIVAT: "text-privat",
  FREIBERUFLICH: "text-freiberuflich",
  FINANZAMT: "text-finanzamt",
};

export default async function Startseite() {
  const [{ daten: konten, fehler }, zustand] = await Promise.all([
    backendHolen<Konto[]>(["konten"]),
    anmeldezustand(),
  ]);

  return (
    <main className="mx-auto max-w-4xl px-6 py-16">
      <header className="mb-12">
        <h1 className="text-3xl font-semibold tracking-tight">Haushaltsbuch</h1>
        <p className="mt-2 text-gedaempft">
          Übersicht für beide. Die eigentliche Arbeit passiert im Gespräch.
        </p>
        <div className="mt-4 flex items-center justify-between gap-4">
          <a href="/bank" className="text-sm text-akzent hover:underline">
            Bankzugänge und abgerufene Salden →
          </a>
          <Anmeldeleiste zustand={zustand} />
        </div>
      </header>

      <Zuordnungshinweis zustand={zustand} />

      <section>
        <h2 className="mb-4 text-sm font-medium uppercase tracking-wider text-gedaempft">
          Konten
        </h2>

        {fehler ? (
          <Hinweis>
            {fehler}. Läuft das Backend? <code className="text-akzent">make hoch</code> startet
            den Stapel.
          </Hinweis>
        ) : !konten || konten.length === 0 ? (
          <Hinweis>
            {zustand.angemeldet && zustand.zugeordnet
              ? "Keine Konten angelegt."
              : "Keine Konten sichtbar. Das heißt nicht, dass keine existieren — ohne angemeldeten und zugeordneten Benutzer gibt die Zugriffskontrolle nichts heraus."}
          </Hinweis>
        ) : (
          <ul className="divide-y divide-rand overflow-hidden rounded-lg border border-rand bg-flaeche">
            {konten.map((konto) => (
              <li key={konto.id} className="flex items-baseline justify-between px-5 py-4">
                <div>
                  <div className="font-medium">{konto.bezeichnung}</div>
                  <div className="text-sm text-gedaempft">{KONTOART_BESCHRIFTUNG[konto.art]}</div>
                </div>
                <span className={`text-sm font-medium ${SPHAERENFARBE[konto.sphaere]}`}>
                  {SPHAERE_BESCHRIFTUNG[konto.sphaere]}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <footer className="mt-16 border-t border-rand pt-6 text-sm text-gedaempft">
        Die primäre Schnittstelle ist der MCP-Endpunkt unter <code>/mcp</code>.
      </footer>
    </main>
  );
}

function Hinweis({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-rand bg-flaeche px-5 py-4 text-sm text-gedaempft">
      {children}
    </div>
  );
}
