# ADR-0004 — Kategorien und Splits statt Kontenrahmen

| | |
|---|---|
| Status | Angenommen |
| Datum | 2026-08-17 |
| Kumbuka | `decision.hb-07-kategorien-und-splits` |
| Verhältnis | Setzt ADR-0003 voraus |

## Kontext

ADR-0003 hat doppelte Buchführung als internen Mechanismus festgelegt und gleichzeitig
verlangt, dass an der Oberfläche nichts davon zu sehen ist. Diese ADR klärt, wie das konkret
aussieht — denn „doppelte Buchführung" wird regelmäßig mit „Kontenrahmen" verwechselt, und
das sind zwei verschiedene Dinge.

Ein **Mechanismus** sagt: jede Bewegung hat zwei Seiten, die Summe aller Seiten ist null,
ein Transfer zwischen eigenen Konten erzeugt keinen Aufwand. Eine **Taxonomie** sagt: welche
Sachkonten es gibt und was wohin gehört. Das System braucht das Erste und will das Zweite
nicht.

Zweiter Auslöser: Belege sollen ausgelesen und mit Buchungen verbunden werden. Ein Einkauf
bei einem Lebensmittelhändler erscheint auf dem Konto als **eine Summe**, der Kassenbon
enthält aber Positionen, die sich über mehrere Kategorien verteilen — Lebensmittel,
Drogerie, und das Werkzeug, das im Regal daneben lag. Damit stellte sich die Frage, ob unter
der Buchung eine eigene Positionsebene eingeführt werden muss und ob das später nachrüstbar
ist.

## Entscheidung

**Zwei orthogonale Strukturen statt einer.**

*Konten* sind die physischen Konten, die Kartenkonten als Verbindlichkeiten und wenige
technische Konten für Eröffnungsbilanz und Verrechnung. Zweistellige Anzahl, ändert sich
fast nie, kein Endnutzer sieht sie.

*Kategorien* sind eine benutzereditierbare, flache Taxonomie mit einer Ebene Gruppierung
darüber. Sie sind eine **Dimension am Split**, kein Konto.

**Die Positionsebene ist keine zusätzliche Struktur, sondern die Split-Tabelle.** Eine
Buchung hat eine Seite gegen das Bankkonto und n Gegenposten, jeder mit einer Kategorie.
Invariante: die Summe der Splits ist der Buchungsbetrag, immer und maschinell geprüft.

Daraus folgt eine zweistufige Bedienung ohne Schemabruch. Zunächst ist n gleich eins: der
Einkauf landet als Ganzes auf einer groben Kategorie. Später öffnet ein
„Aufschlüsseln"-Vorgang die Buchung und ersetzt den einen Split durch mehrere. Kein
Nachrüsten, keine Migration, kein Sonderfall — die Struktur ist von Anfang an dieselbe, nur
die Anzahl der Splits ändert sich.

**Extrahierte Belegzeilen werden nicht persistiert.** Sie sind OCR-Ergebnis und damit
unsicher. Sie leben als Vorschlagsartefakt am Dokument im Belegarchiv und dienen als
Grundlage für den Aufschlüsselungsvorschlag. Ratifiziert und gespeichert wird der Split. Im
Ledger steht damit nur, was ein Mensch bestätigt hat.

## Alternativen

**Kategorien als Konten modellieren**, wie Firefly III es tut. Verworfen: es vermischt die
beiden Strukturen und erzeugt genau die Buchhalteroberfläche, die dieses System vermeiden
soll. Ausführlich in ADR-0003, A1.

**Eine eigene Positionstabelle unter der Buchung**, getrennt von den Splits. Verworfen, weil
sie dieselbe Information ein zweites Mal führt und eine zweite Summeninvariante braucht, die
mit der ersten auseinanderlaufen kann. Splits leisten dasselbe.

**Kategorien fest im Code.** Verworfen: die Taxonomie eines Privathaushalts ist nicht
vorhersagbar und ändert sich mit dem Leben. Eine Codeänderung als Voraussetzung für eine
neue Kategorie ist für die Zielgruppe keine Option.

**Belegzeilen mitspeichern.** Verworfen, weil sie unbestätigt sind. Ein OCR-Ergebnis im
Ledger ist eine Zahl, die aussieht wie eine Messung, aber eine Schätzung ist — dieselbe
Fehlerklasse, gegen die die Deterministik-Regel antritt.

## Konsequenzen

**Drei Wartungspflichten** entstehen unmittelbar aus der Editierbarkeit der Kategorien und
sind beim Schemaentwurf umsonst, später teuer:

1. Kategorien brauchen eine **stabile ID**. Regeln, Prognosen und die Historie referenzieren
   sie; Umbenennen darf nichts brechen.
2. Löschen braucht einen **Umbuchungspfad** — Merge oder Sperre, solange Buchungen daran
   hängen. Eine Kategorie darf nicht verschwinden und Splits verwaisen lassen.
3. Die **Gruppierungsebene** kommt von Anfang an. Eine flache Liste hat nach anderthalb
   Jahren dreißig Einträge, und dann ist die Ebene eine Migration.

**Was dadurch möglich wird:** Splits je Kategorie sind der Ist-Verbrauch. Der vertagte
Essens- und Einkaufsplaner kann daran später als Messwert andocken statt als Schätzung. Das
ist kein Ziel dieser ADR, aber der Grund, warum die Struktur nicht enger geschnitten wird.

**Grenze, die bestehen bleibt:** Bargeld. Eine Abhebung ist eine Zeile auf dem Konto, danach
ist das Geld unbeobachtet. Ein Bon ohne zugehörige Kartenbuchung ist die Spiegelseite und
beweist, dass ein Teil wieder auftaucht. Das Modell muss beides führen können — Belege ohne
Buchung und Buchungen ohne Beleg — ohne dass eines davon ein Fehler ist. Vollständigkeit der
Erfassung ist eine Kennzahl, kein Zustand.
