# ADR-0006 — Projektionsautorisierung: Zeilensichtbarkeit unten, Aggregationsstufe oben

| | |
|---|---|
| Status | **Vorgeschlagen** — nicht ratifiziert |
| Datum | 2026-08-17 |
| Kumbuka | `constraint.autorisierung-der-antwort` (ratifiziert), `decision.hb-05-mcp-first` (korrigiert) |
| Verhältnis | **Revidiert ADR-0002** in der Zuständigkeitsverteilung, nicht im Mechanismus |

> Diese ADR ist bewusst als *Vorgeschlagen* eingetragen. Der zugrunde liegende Constraint ist
> ratifiziert, die daraus folgende Revision von ADR-0002 ist es nicht. ADR-0002 hat „RLS plus
> zusätzliche Filterung in der Anwendung" ausdrücklich verworfen; dieser Vorschlag ist eine
> Variante davon und braucht eine Entscheidung, bevor Code entsteht.

## Kontext

ADR-0002 wurde unter einer Prämisse geschrieben, die inzwischen korrigiert ist. Sie lautete:
zeilenbasierte Zugriffskontrolle auf Kontoebene sei eine harte Anforderung, weil ein zweiter
Nutzer sich anmelden und nur einen Teil sehen solle, und genau daran seien die fertigen
Kandidaten gescheitert.

Beide Hälften stimmen nicht mehr. Es gibt keinen Geheimhaltungsbedarf innerhalb des
Haushalts; der Ausschluss der fertigen Kandidaten ist inzwischen anders begründet (ADR-0003).
Was an ihre Stelle getreten ist, ist ein **Produktmerkmal** mit drei Stufen je physischem
Konto:

| Stufe | Bedeutung |
|---|---|
| `full` | Positionen, Buchungen, Salden |
| `sum` | ausschließlich Aggregate |
| `none` | nichts |

Virtuelle Konten und Töpfe sind nicht abgestuft.

Und hier entsteht das Problem: **Row-Level-Security kann drei Stufen nicht ausdrücken.** Sie
kennt zwei Zustände, Zeile sichtbar oder nicht. Für die Stufe `sum` bedeutet Zeilenfilterung,
dass der Aufrufer keine Zeilen bekommt und jedes Aggregat das betroffene Konto **still
wegrechnet**. Die Summe ist dann nicht geschützt, sondern falsch — plausibel, ohne
Fehlermeldung, und niemand sucht sie. Das ist dieselbe Fehlerklasse, gegen die die
Deterministik-Regel antritt, nur eine Schicht tiefer.

## Entscheidung (vorgeschlagen)

**Autorisiert wird die Antwort, nicht die Abfrage.** Die Zuständigkeit wird aufgeteilt:

*Die Datenbank erzwingt `none`.* Die Zugriffstabelle erhält eine Spalte `stufe`. Das
Policy-Prädikat lässt Zeilen durch, wenn die Stufe `full` **oder** `sum` ist. Alles aus
ADR-0002 bleibt damit in Kraft: `FORCE ROW LEVEL SECURITY`, der Rollenwechsel pro
Transaktion, `SET LOCAL`, das Fail-Closed-Verhalten bei fehlendem Kontext und der CI-Test auf
Policy-Vollständigkeit.

*Die Serviceschicht erzwingt `sum` gegen `full`* — über den **Rückgabetyp**. Eine Methode
liefert entweder Positionen oder ein Aggregat. Welche von beiden ein Aufrufer erreicht,
entscheidet seine Stufe. Das Repository bleibt rollenblind.

Dazu drei Regeln, die aus dem ratifizierten Constraint folgen:

1. Jede berechnete Größe trägt die **Menge ihrer Quellkonten**. Autorisiert wird gegen diese
   Menge, nicht gegen die einzelne Zeile. Kennzahlen und Prognosen sind kontoübergreifend;
   ohne diese Herkunftsangabe transportiert eine Haushaltskennzahl Zahlen aus einer Sphäre,
   die der Aufrufer nicht sehen darf.
2. Ist ein Quellkonto für den Aufrufer `none`, wird die Größe **verweigert** — niemals still
   ohne dieses Konto berechnet. Sonst entsteht genau die falsche Summe von oben.
3. Ein **Trägerkonto darf nicht restriktiver sein als die Töpfe darauf**. Die
   Nullsummen-Invariante aus HB-03 macht den Saldo des Trägerkontos aus den Topfständen
   ableitbar; eine gegenläufige Einstellung wäre eine Inkonsistenz, die niemand sucht.

## Verhältnis zu ADR-0002

ADR-0002 verwarf „RLS plus zusätzliche Filterung in der Anwendung" mit der Begründung, zwei
Regelwerke müssten gepflegt werden und liefen auseinander. Diese Begründung ist für ein
**zweiwertiges** Modell richtig — dort täte die Anwendung dasselbe wie die Datenbank, nur
schlechter.

Sie trifft für das dreiwertige Modell nicht, weil die Serviceschicht keine zweite
Zeilenfilterung ist. Sie entscheidet über den **Aggregationsgrad der Antwort**, und diese
Entscheidung kann die Datenbank grundsätzlich nicht treffen: sie kennt den Rückgabetyp nicht.
Es sind nicht zwei Regelwerke für eine Frage, sondern zwei Fragen mit je einer zuständigen
Schicht.

## Alternativen

**Aggregate in einer zweiten Transaktion mit erhöhtem Kontext berechnen**, deren Policy auch
`sum`-Zeilen freigibt, während die Serviceschicht garantiert, dass nur Aggregate herausgehen.
Verworfen: dieser erhöhte Kontext ist einen Fehler davon entfernt, Zeilen auszuliefern, und er
verlagert die eigentliche Absicherung ohnehin in die Anwendung — nur an eine schlechter
sichtbare Stelle.

**Auf die Stufe `sum` verzichten.** Technisch die einfachste Lösung, denn dann genügt ADR-0002
unverändert. Verworfen, weil `sum` das Merkmal ist, das die Sichtbarkeitsstufen überhaupt
interessant macht — ohne es bleibt eine Alles-oder-nichts-Freigabe.

## Konsequenzen

- Änderungsumfang gegenüber dem bestehenden Stand: eine Spalte, ein erweitertes
  Policy-Prädikat, und eine Präzisierung in `doc/architektur/03-zugriffskontrolle.md` — aus
  „die Zugriffsregel liegt in der Datenbank" wird „die Zeilensichtbarkeit liegt in der
  Datenbank, die Projektionsstufe in der Anwendung".
- Das Transfer-Leck bleibt und ist **prinzipiell, nicht behebbar**: ein Transfer zwischen zwei
  eigenen Konten ist eine Buchung mit zwei Seiten. Wer die Gegenseite auf `full` sieht, sieht
  Betrag, Datum und Gegenkonto. Die Stufe `sum` schützt die internen Bewegungen eines Kontos,
  nicht seine Kanten. Das gehört als **dokumentierte Eigenschaft** in die
  Merkmalsbeschreibung; sonst wird daraus später ein Fehlerbericht über ein Datenleck.
- Die Sichtbarkeitsstufen sind eine Kuration mit Datenschutzcharakter, keine Sicherheitsgrenze
  gegen einen entschlossenen Mitbewohner. Diese Einordnung offen zu dokumentieren ist
  billiger, als sie später zu verteidigen.
