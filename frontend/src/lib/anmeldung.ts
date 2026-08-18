import { backendHolen } from "@/lib/backend-server";

/**
 * Der Anmeldezustand, wie das Backend ihn sieht.
 *
 * Bewusst vom Backend erfragt und nicht aus dem Token im BFF abgeleitet. Ob eine
 * Anmeldung einem fachlichen Benutzer zugeordnet ist, steht in
 * `benutzeridentitaet` — das weiß nur das Backend. Der BFF sähe ein gültiges
 * Token und hielte den Menschen für vollständig angemeldet, während die
 * Zugriffskontrolle ihm nichts herausgibt.
 */

export type Anmeldezustand = {
  angemeldet: boolean;
  zugeordnet: boolean;
  subjekt: string | null;
};

/** Nicht angemeldet, nicht zugeordnet - der Zustand, wenn das Backend schweigt. */
const UNBEKANNT: Anmeldezustand = { angemeldet: false, zugeordnet: false, subjekt: null };

export async function anmeldezustand(): Promise<Anmeldezustand> {
  const { daten } = await backendHolen<Anmeldezustand>(["ich"]);
  return daten ?? UNBEKANNT;
}
