/**
 * Adressierung des Backends aus Sicht des Servers.
 *
 * Der Browser spricht das Backend nie direkt an - er kennt nur den BFF unter
 * `/api/bff/...`. Diese Adresse gilt ausschließlich serverseitig und darf
 * deshalb auf einen Docker-internen Namen zeigen, den kein Browser auflösen
 * könnte.
 */

const STANDARD_BACKEND = "http://localhost:8080";

export function backendBasis(): string {
  // Bewusst ohne NEXT_PUBLIC_-Präfix: eine so benannte Variable würde in das
  // Browser-Bundle eingebettet und die interne Adresse nach außen tragen.
  return process.env.BACKEND_BASE_URL ?? STANDARD_BACKEND;
}

/**
 * Setzt eine Backend-URL aus Pfadsegmenten zusammen.
 *
 * Die Segmente stammen aus der Anfrage-URL und damit vom Aufrufer. Sie werden
 * deshalb einzeln kodiert und auf Pfadwechsel geprüft: ohne diese Prüfung
 * käme man mit `..` aus `/api/` heraus und erreichte beliebige Backend-Pfade -
 * etwa die Verwaltungsendpunkte unter `/q/`.
 */
export function backendUrl(segmente: string[], suchparameter?: string): string {
  for (const segment of segmente) {
    if (segment === ".." || segment === "." || segment.includes("/") || segment.includes("\\")) {
      throw new Error(`Unzulässiges Pfadsegment: ${segment}`);
    }
  }

  const pfad = segmente.map(encodeURIComponent).join("/");
  const anfrage = suchparameter ? `?${suchparameter}` : "";

  return `${backendBasis()}/api/${pfad}${anfrage}`;
}

/** Konto in der Darstellung, die das Backend liefert. */
export type Konto = {
  id: string;
  bezeichnung: string;
  art: "HAUSHALTSKONTO" | "GIROKONTO" | "GESCHAEFTSKONTO" | "RUECKLAGENKONTO" | "KREDITKONTO";
  sphaere: "PRIVAT" | "FREIBERUFLICH" | "FINANZAMT";
};

export const KONTOART_BESCHRIFTUNG: Record<Konto["art"], string> = {
  HAUSHALTSKONTO: "Haushaltskonto",
  GIROKONTO: "Girokonto",
  GESCHAEFTSKONTO: "Geschäftskonto",
  RUECKLAGENKONTO: "Rücklagenkonto",
  KREDITKONTO: "Kreditkonto",
};

export const SPHAERE_BESCHRIFTUNG: Record<Konto["sphaere"], string> = {
  PRIVAT: "Privat",
  FREIBERUFLICH: "Freiberuflich",
  FINANZAMT: "Finanzamt",
};
