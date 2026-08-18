import { anmeldungEingerichtet } from "@/lib/oidc";
import type { Anmeldezustand } from "@/lib/anmeldung";

/**
 * Die Zeile über allem: wer angemeldet ist, und der Weg hinein oder hinaus.
 *
 * Server Component. Die Abmeldung ist ein Formular mit POST und kein Link — ein
 * Abmelden per GET liesse sich über ein eingebettetes Bild auf einer fremden
 * Seite auslösen.
 */
export function Anmeldeleiste({ zustand }: { zustand: Anmeldezustand }) {
  if (!anmeldungEingerichtet()) {
    // Entwicklungsprofil: kein Identity Provider, fester Demo-Benutzer. Ein
    // Anmeldeknopf, der ins Leere führt, wäre hier irreführend.
    return (
      <div className="text-sm text-gedaempft">
        Ohne Anmeldung — Entwicklungsprofil mit festem Demo-Benutzer.
      </div>
    );
  }

  if (!zustand.angemeldet) {
    return (
      <a href="/anmeldung" className="text-sm text-akzent hover:underline">
        Anmelden →
      </a>
    );
  }

  return (
    <form action="/abmeldung" method="post">
      <button type="submit" className="text-sm text-gedaempft hover:text-akzent hover:underline">
        Abmelden
      </button>
    </form>
  );
}

/**
 * Der Hinweis auf eine Anmeldung ohne fachliche Zuordnung.
 *
 * Dieser Zustand ist der teuerste in der ganzen Anmeldung: Die Anmeldung gelingt,
 * das Token ist gültig, und trotzdem gibt die Zugriffskontrolle nichts heraus,
 * weil der Subject in `benutzeridentitaet` fehlt. Ohne diesen Hinweis sieht das
 * aus wie ein Rechteproblem und ist keines.
 *
 * Der Subject steht mit dabei, damit er sich eintragen lässt, statt ihn aus einem
 * Protokoll fischen zu müssen.
 */
export function Zuordnungshinweis({ zustand }: { zustand: Anmeldezustand }) {
  if (!zustand.angemeldet || zustand.zugeordnet) {
    return null;
  }

  return (
    <div className="mb-8 rounded-lg border border-finanzamt/40 bg-flaeche px-5 py-4">
      <p className="font-medium text-finanzamt">
        Diese Anmeldung ist keinem Benutzer dieses Haushaltsbuchs zugeordnet.
      </p>
      <p className="mt-2 text-sm text-gedaempft">
        Die Anmeldung selbst hat funktioniert. Es fehlt die Zuordnung zu einem fachlichen Benutzer —
        deshalb sind unten keine Konten zu sehen. Das ist kein Rechteproblem und kein Fehler, und es
        geht auch nicht von selbst weg: Wer sich anmelden kann, bekommt damit bewusst nicht
        automatisch Zugriff auf Konten.
      </p>
      {zustand.subjekt && (
        <p className="mt-3 text-sm text-gedaempft">
          Zum Eintragen wird diese Kennung gebraucht:{" "}
          <code className="text-akzent">{zustand.subjekt}</code>
          <span className="mt-1 block">
            Der Handgriff steht in <code>doc/betrieb/anmeldung.md</code>.
          </span>
        </p>
      )}
    </div>
  );
}
