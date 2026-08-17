# ADR-0007 — Kommandoklassen der MCP-Oberfläche und der Review-Dialog

| | |
|---|---|
| Status | Angenommen |
| Datum | 2026-08-17 |
| Kumbuka | `constraint.mcp-kommandoklassen`, `constraint.regelvorschlag-reichweite` |
| Verhältnis | Präzisiert die Deterministik-Regel. Setzt ADR-0005 voraus |

## Kontext

Die Deterministik-Regel des Projekts sagt: ein Agent bucht nie eigenständig und bucht nie
zwischen Töpfen um. Sie ist aber gegen eine Oberfläche formuliert worden, die noch nicht
existierte. In der Praxis stehen sehr verschiedene Anliegen nebeneinander: „reicht das Geld für
diese Anschaffung", „wie ist der Stand auf einem Konto", „setz dieses Ziel auf die Sparliste",
„ändere die Rate dieses Sparplans". Das sieht nach einer Oberfläche aus, sind aber
unterschiedliche Vertrauensniveaus.

Zweiter Auslöser: die Klassifikation unklarer Buchungen soll als Dialog laufen und nicht als
Formular — das System fragt „hier ist eine Zahlung bei einem Händler, was war das", und die
Antwort kommt als freier Text. Das ist ein schreibender Vorgang mit einem Sprachmodell in der
Mitte, also genau die Stelle, an der die Deterministik-Regel greift oder eben nicht.

## Entscheidung

**Die Berechtigung hängt am Werkzeug, nicht am Prompt.** Ein Tool, das je nach Parameter liest
oder schreibt, ist eine Sicherheitslücke mit guter Dokumentation. Die Werkzeuge werden
entsprechend geschnitten:

### Klasse 1 — Abfragen

Kennzahlen, Kontostände, Zahlungskalender. Risikofrei, weil nichts verändert wird und die
Berechnung deterministisch ist. Das Modell ist reiner Übersetzer. Wo ein Betrag aus einem Bild
stammt, meldet das Werkzeug zurück, **welchen Betrag es verstanden hat**, bevor es antwortet —
die Erkennung ist die einzige Fehlerquelle und damit prüfbar.

### Klasse 2 — Planänderungen

Sparplan anpassen, ein Ziel auf die Liste setzen, eine Rate ändern. Diese dürfen agentisch
laufen, mit Rückbestätigung im Dialog und einem Änderungsprotokoll, aber **ohne menschliche
Ratifizierung als Torwächter**.

Das ist eine Präzisierung, keine Aufweichung: die Deterministik-Regel verbietet das *Buchen*
und das *Umbuchen zwischen Töpfen*, nicht das Ändern eines Plans. Der Unterschied ist
kategorial. Eine falsche Aussage über die Vergangenheit fällt beim Jahresabschluss auf, wenn
überhaupt. Eine falsche Absicht über die Zukunft fällt beim nächsten Blick auf das Dashboard
auf.

### Klasse 3 — Buchungen und Topfumbuchungen

Bleiben dem Agenten verboten. Unverändert.

### Der Review-Dialog

Ein Verbpaar: das eine gibt einen offenen Posten heraus, das andere nimmt die Antwort an. Die
**Buchungskennung stammt aus dem ersten Aufruf, nicht aus dem Text** — damit ist der
schreibende Vorgang eng adressiert und protokollierbar statt ein offenes „ändere das mal". Das
Modell übersetzt freien Text in eine strukturierte Zuweisung.

Die Warteschlange wird **nach Betrag absteigend** abgearbeitet, nicht chronologisch.
Aufmerksamkeit ist die knappe Ressource; Kleinbeträge verdienen sie nicht.

### Regelvorschläge brauchen eine Reichweitenanzeige

Pro Antwort sind zwei Dinge strikt getrennt: **diese Buchung kategorisieren** (immer, harmlos)
und **eine Regel für künftige anlegen** (optional, riskant).

Riskant, weil die Acquirer-Regel des Projekts genau diesen Fall beschreibt: eine gutmütige
Antwort wird zu einer Regel auf eine Gläubigerkennung, und wenn diese Kennung einem
Zahlungsdienstleister gehört statt dem Händler, kategorisiert sie eine große Zahl unbeteiligter
Buchungen um.

Deshalb verbindlich: **jeder Regelvorschlag meldet vor der Annahme seine Reichweite gegen den
Bestand** („diese Regel würde N vergangene Buchungen betreffen") und läuft gegen die
Acquirer-Heuristik. Rückwirkende Anwendung ist eine eigene Entscheidung pro Regel, kein
Automatismus.

### Wo gelernte Regeln liegen

**Im Ledger, als versionierte und getestete Regeltabelle.** Nicht im Projektgedächtnis:
Dauermandate plus POS-Muster ergeben über die Zeit einige hundert Einträge, das ist operative
Datenhaltung mit hoher Kardinalität und würde den Scope fluten.

Das Gedächtnis hält die **Policy** — Vorrang der strukturierten Felder vor dem
Gegenpartei-Namen, die Definition eines Acquirers, welche Fälle Pflicht-Regressionstests sind.
Also die Meta-Regel, nicht die Instanz.

## Alternativen

**Ein einziges schreibendes Werkzeug mit einem Absichtsparameter.** Verworfen: dann entscheidet
der Inhalt des Aufrufs über die Berechtigung, und die Prüfung wandert in Prosa.

**Planänderungen wie Buchungen behandeln**, also mit menschlicher Ratifizierung. Verworfen: das
macht den Dialogmodus wertlos, ohne ein Risiko zu adressieren, das dieselbe Größe hätte. Ein
falscher Sparplan ist sichtbar und reversibel.

**Regeln ohne Reichweitenanzeige übernehmen.** Verworfen — siehe oben; das ist der billigste
Schutz im ganzen Entwurf.

## Konsequenzen

- Der Werkzeugschnitt ist Teil des Sicherheitsmodells und nicht bloß Ergonomie. Ein neues
  Werkzeug muss einer der drei Klassen zugeordnet werden, bevor es entsteht.
- Klasse 2 braucht ein Änderungsprotokoll auf Planobjekten. Das ist eine Tabelle, die sonst
  niemand angefordert hätte.
- Die Reichweitenprüfung setzt voraus, dass Regeln gegen den Bestand **probeweise** ausgewertet
  werden können, ohne zu schreiben. Der Regelauswerter braucht also einen Trockenlauf-Modus von
  Anfang an.
