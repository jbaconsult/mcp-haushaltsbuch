# ADR-0002 — Zugriffskontrolle in der Datenbank

| | |
|---|---|
| Status | Angenommen |
| Datum | 2026-08-17 |
| Kumbuka | decision.hb-05-mcp-first (zweite harte Anforderung) |

## Kontext

HB-05 nennt als zweite harte Anforderung an die Toolauswahl: zeilenbasierte
Zugriffskontrolle auf Kontoebene, weil Julia sich einloggen und nur ihre Sachen sehen soll.
Genau daran scheitern die fertigen Kandidaten.

Die Frage ist nicht **ob**, sondern **wo** diese Regel lebt.

## Entscheidung

Die Zugriffsregel liegt in PostgreSQL als Row-Level-Security, mit `FORCE ROW LEVEL
SECURITY` auf jeder Tabelle mit Kontobezug. Die Anwendung verbindet sich mit einer Rolle,
die weder Eigentümer ist noch `BYPASSRLS` besitzt. Der Benutzerkontext wird pro Transaktion
über `SET LOCAL app.benutzer_id` gesetzt.

Ist der Kontext nicht gesetzt, liefern die Policies nichts. Fail-Closed.

## Alternativen

**Filterung in der Anwendungsschicht.** Ein Repository, das jede Abfrage um eine
Zugriffsbedingung ergänzt. Einfacher zu lesen, leichter zu debuggen, in Unit-Tests ohne
Datenbank prüfbar.

Verworfen, weil die Regel dann nur für die Abfragepfade gilt, die daran gedacht haben. Jede
neue Abfrage ist eine neue Gelegenheit, sie zu vergessen — und eine vergessene
Filterbedingung schlägt nicht fehl. Sie liefert einfach mehr Zeilen. Der Fehler ist
unsichtbar, bis jemand ihn bemerkt.

Bei einem System, dessen Existenzgrund unter anderem diese Trennung ist, ist eine
Sicherheitsregel mit stiller Ausfallart der falsche Kompromiss.

**RLS plus zusätzliche Filterung in der Anwendung.** Doppelte Absicherung. Verworfen, weil
zwei Regelwerke gepflegt werden müssen und irgendwann auseinanderlaufen. Wenn sie sich
widersprechen, gewinnt das restriktivere — und man sucht lange nach der Ursache
verschwundener Zeilen. Ein Regelwerk an der richtigen Stelle ist besser als zwei an
mittelmäßigen.

**SQLite zum Start.** Kein Container, eine Datei. Verworfen: SQLite hat keine
Zugriffskontrolle auf Zeilenebene. Die harte Anforderung müsste vollständig in die
Anwendung — also die bereits verworfene Alternative, nur ohne Ausweichmöglichkeit. Der
spätere Wechsel auf Postgres wäre kein Datenbankwechsel, sondern ein Umbau des
Sicherheitsmodells an einem System, das dann schon echte Daten führt.

## Konsequenzen

**Leichter:**
- Die Regel gilt für alle Zugriffspfade, auch für den Reporting-Job, den in zwei Jahren
  jemand danebenstellt.
- Sie ist testbar: der Test meldet sich am Fremdkonto an und prüft, dass nichts kommt.
- `RlsPolicyVollstaendigkeitTest` vergleicht Tabellen mit Kontobezug gegen `pg_policies`
  und macht eine vergessene Policy zu einem roten Build statt zu einem Datenleck.

**Schwerer:**
- Postgres ist Pflicht — auch in Tests. Testcontainers über Quarkus Dev Services löst das,
  kostet aber Startzeit.
- Jeder Transaktionspfad muss den Kontext setzen. Zentral in `RlsKontext` gelöst, aber
  Hintergrundjobs brauchen eine bewusste Entscheidung, unter welchem Kontext sie laufen.
- Zwei Datenbankrollen im Betrieb. Läuft die Anwendung versehentlich als Eigentümer, wäre
  ohne `FORCE` die gesamte Zugriffskontrolle wirkungslos — deshalb ist `FORCE` nicht
  optional, sondern Teil jeder Migration.

**Fallstrick für später:**
`SET` statt `SET LOCAL` würde den Kontext an der gepoolten Verbindung hängen lassen und dem
nächsten Benutzer mitgeben, der sie bekommt. Das ist genau die Art Fehler, die im Test mit
einer Verbindung nie auftritt und unter Last sofort.
