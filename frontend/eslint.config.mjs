import coreWebVitals from "eslint-config-next/core-web-vitals";
import typescript from "eslint-config-next/typescript";

/**
 * Flache ESLint-Konfiguration.
 *
 * eslint-config-next 16 liefert Flat Config direkt aus - die Brücke über
 * FlatCompat aus dem Next.js-Standardgerüst ist nicht mehr nötig und bricht
 * mit ESLint 9 an einer zirkulären Referenz in der Plugin-Struktur.
 */
const konfiguration = [
  {
    ignores: [".next/**", "node_modules/**", "next-env.d.ts"],
  },
  ...coreWebVitals,
  ...typescript,
];

export default konfiguration;
