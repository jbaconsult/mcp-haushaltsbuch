# Rückgabe — Sub-Sprint 1: Ledger-Grundschema und validierender Importer

| | |
|---|---|
| Datum | 2026-08-17 |
| Apparat | Code |
| Auftrag | [`2026-08-17-dispatch-ledger-grundschema.md`](2026-08-17-dispatch-ledger-grundschema.md) |
| Zustand | ausgeführt, gemergt |
| Ablage | PR [#5](https://github.com/jbaconsult/mcp-haushaltsbuch/pull/5) auf `main` |

## Ergebnis in einem Satz

Das Ledger hat seine tragenden Tabellen, die strukturierten Felder überleben den Import als
eigene Spalten, und ein Auszug kommt vollständig oder gar nicht in den Bestand — geprüft
über 90 Tests, darunter die Rote Probe in beiden Hälften.

---

## Akzeptanzkriterien

| # | Kriterium | Nachweis |
|---|---|---|
| 1 | `ENABLE` **und** `FORCE ROW LEVEL SECURITY` auf jeder Tabelle mit Kontobezug, `RlsPolicyVollstaendigkeitTest` grün | `V2__ledger.sql`; der bestehende Test lief unverändert durch |
| 2 | Ohne Benutzerkontext null Zeilen auf `buchung` und `buchungssplit` | `LedgerZugriffTest.ohneKontextNullZeilen` — geprüft zusätzlich auf `bewegung`, `kontoauszug`, `kategorie` |
| 3 | Splitsumme gleich Buchungsbetrag, Verletzung **in der Datenbank** abgelehnt | `SplitsummeInvarianteTest`, fünf Fälle; geprüft an einer Verbindung unter der Anwendungsrolle, nicht über Hibernate |
| 4 | Auszug vollständig oder gar nicht, Fehlerliste nennt die Invariante | `RoteProbeTest`, `ImportdienstTest` |
| 5 | Zwei Läufe desselben Auszugs, derselbe Datenbestand | `LedgerImportTest.zweiLaeufeErzeugenDenselbenBestand` — 0 neue, 3 übersprungen, ein `kontoauszug`-Eintrag |
| 6 | MREF, CRED und Gegenpartei-IBAN als eigene, abfragbare Spalten | `LedgerImportTest.strukturierteFelderSindEinzelnAbfragbar`; Abfragen über `BuchungRepository` je Spalte, plus die Zusage, dass der Verwendungszweck sie nicht mehr enthält |
| 7 | Zeilenumbruch mitten in einer IBAN | Fixture `iban-umbruch.sta` — die Mandatsreferenz ist dort ebenfalls über den Umbruch verteilt |
| 8 | Buchungstag Dezember, Valuta Januar | Fixture `jahreswechsel.sta` — beide Richtungen, 30.12./02.01. und 02.01./30.12. |
| 9 | Storno erkannt, nicht als Währungsartefakt | Fixture `storno.sta` — `CR` als Haben in Euro, `RC` und `RD` als Storno |
| 10 | Alle Fixtures synthetisch | zwölf MT940- und zwei CAMT-Dateien, sämtliche IBANs erfunden und mit gültiger Prüfsumme gerechnet |

## Rote Probe

**Erste Hälfte — das Gate greift.** Endsaldo einer sauberen Kopie um einen Cent verändert.
Der Import scheitert mit I1 und der Meldung `Differenz 0.01 EUR`; für diesen Auszug steht
**keine** Zeile im Bestand und auch kein `kontoauszug`-Eintrag.

**Zweite Hälfte — die Daten überleben.** Der zuvor sauber importierte Auszug ist danach
unverändert vollständig.

Das trägt aus einem strukturellen Grund und nicht aus Sorgfalt: der `Importdienst` liest,
prüft und schreibt erst dann. Ein verletzender Auszug erreicht den Schreibport gar nicht.
Ein Zurückrollen wird für den erwarteten Fall nicht gebraucht — und damit auch kein
Vertrauen darauf, dass es die richtige Menge trifft.

---

## Die offen gelassene Entwurfsentscheidung

Der Auftrag stellte die Modellierung der Zweiseitigkeit frei und nannte nur die
Anforderung: *eine Auswertung darf Sammelabbuchung und Einzelumsätze nicht beide zählen
können, ohne dass eine Regel daran denken muss.*

**Gewählt:** Eine Buchung ist **eine Seite** einer `bewegung`. Zwei aufgeschobene
Constraint-Trigger halten fest:

1. Eine Bewegung mit mehreren Seiten ergänzt sich zu null.
2. Eine Bewegung mit mehreren Seiten trägt **keine** Kategorie.

Bedingung 2 ist die eigentliche Sicherung. Eine Kategorienauswertung summiert Splits mit
Kategorie. Der Kartenausgleich *kann* keine haben — die Datenbank lässt es nicht zu —, die
Einzelumsätze haben je genau eine. Doppelt zählen ist damit nicht verboten, sondern nicht
formulierbar.

Ein dritter Trigger hält die Splitsumme gleich dem Buchungsbetrag. Alle drei sind
`DEFERRABLE INITIALLY DEFERRED`, weil eine Buchung zwischen ihrem eigenen Einfügen und dem
ihrer Splits zwingend unausgeglichen ist.

---

## Präzisierungen am Auftrag

Drei Stellen, an denen die Umsetzung den Auftragstext schärfer fasst, als er formuliert war.
Keine davon weicht eine Invariante auf.

**I4 und `NONREF`.** Dedupliziert wird über die Bankreferenz. Viele Banken liefern in Feld 61
aber `NONREF`, und manche wiederholen dieselbe Referenz innerhalb eines Auszugs — in beiden
Fällen ist sie als Schlüssel unbrauchbar, und ein Import verwürfe die zweite Buchung
lautlos als Doublette. Der Parser vergibt dort eine inhaltsstabile Ersatzreferenz aus einem
Fingerabdruck über Valuta, Buchungstag, Betrag, Zweck und Gegenpartei. Ausdrücklich **nicht**
aus einer Position in der Datei: Exportzeiträume überlappen sich an den Randtagen, und
dieselbe Buchung steht dann an anderer Stelle.

**I2 ist dateiintern.** „Endsaldo Block N gleich Anfangssaldo Block N+1" ist als Kette
innerhalb einer Datei je Konto umgesetzt. Ein dateiübergreifender Anschluss bräuchte eine
Heuristik über Auszugsnummern, die viele Banken jährlich zurücksetzen — sie hätte falsch
ausgelöst. Die Lücke zwischen zwei Importen deckt I1 zusammen mit I4 ab.

**Die Policy auf `bewegung` ist bewusst schwach.** `SELECT` für jeden Angemeldeten. Der
naheliegende Entwurf — sichtbar, sobald eine Seite sichtbar ist — macht jedes
`INSERT ... RETURNING id` unmöglich, weil beim Anlegen noch keine Seite existiert; der Fehler
lautet dann „new row violates row-level security policy" und zeigt auf das Anlegen, obwohl
das Lesen gemeint ist. Die Zeile besteht aus Kennung und Zeitstempel, alles Fachliche hängt
an den Seiten. Das Transfer-Leck aus `constraint.autorisierung-der-antwort` bleibt davon
unberührt und weiterhin dokumentiert.

## Stoppbedingung und Rückfragepunkt

**Beide nicht ausgelöst.**

Die Invarianten I1 bis I5 verhalten sich am realen Format wie im Auftrag beschrieben. Beide
genannten MT940-Fallen sind eingetreten und werden getroffen: das Zeichen nach C/D ist das
dritte Zeichen von „EUR", und das Buchungsdatum kommt ohne Jahr.

Parser und Import brauchen **kein** eigenes Maven-Modul. Die Parser liegen als reine
Funktionen über Text in `kern`, das Schreiben in `persistenz`. Die Modulstruktur aus ADR-0001
bleibt unangetastet.

---

## Befunde außerhalb des Auftrags

Vier Dinge, die beim Bauen aufgefallen sind und die Concept kennen sollte, weil sie
kommende Zuschnitte betreffen.

**`.gitignore` verschluckt Test-Fixtures.** Die Regel `*.sta` hält echte Kontoauszüge aus dem
Repository — und hat die synthetischen Fixtures mitgenommen, weil sie dieselben Formate
sind. Der Fehler war lautlos: `git add -A` überspringt ignorierte Dateien, `git status` zeigt
nur das übergeordnete Verzeichnis, und lokal lief alles grün, weil die Dateien auf der
Platte lagen. Erst die CI mit frischem Checkout meldete 29 Fehler. Die Schutzregel steht
unverändert; hinzugekommen ist eine Ausnahme, deren Grenze der Pfad ist. **Lehre für
künftige Aufträge mit Fixtures:** eine grüne lokale Prüfung beweist nicht, dass die Dateien
im Repository sind.

**JaCoCo misst Quarkus-Tests nicht ohne Hilfe.** Hibernate und Panache schreiben den Bytecode
der Entitäten und Repositories beim Start um; die geladene Klasse stimmt dann nicht mehr mit
der auf der Platte überein, und JaCoCo verwirft die Messung stillschweigend. Auf dem
damaligen `main` gemessen: `persistenz` stand auf 0 von 44 Zeilen, obwohl `RlsZugriffTest`
genau dort hineinläuft; mit `quarkus-jacoco` auf 42 von 44, `mcp` sprang von 19 auf 30 von
33. Eine Abdeckungszahl, die zu niedrig aussieht, ist in diesem Stack zuerst ein Verdacht
gegen die Messung.

**SonarCloud analysierte mit dem falschen Analyzer.** Die automatische Analyse hat die
PostgreSQL-Migration mit dem **PL/SQL**-Analyzer geprüft und `CHECK (bezeichnung <> '')` als
NULL-Vergleich beanstandet — in Oracle *ist* der Leerstring NULL, in PostgreSQL nicht. Der
Befund war für diese Datenbank falsch. Umgestellt auf `length(btrim(x)) > 0`, was zusätzlich
Bezeichnungen aus reinem Leerraum ablehnt. Die Analyse läuft inzwischen über Maven
(PR [#7](https://github.com/jbaconsult/mcp-haushaltsbuch/pull/7)).

**Zur Verifikationslage.** Während der Ausführung war der Docker-Daemon dieser Maschine
zeitweise defekt — weder Registry-Zugriff noch Port-Weiterleitung. Die datenbankgestützten
Tests liefen deshalb zwischenzeitlich gegen eine eigenständige PostgreSQL-18.4-Distribution.
Nach der Wiederherstellung wurde **alles** über den regulären Weg wiederholt: `mvn verify`
mit Quarkus Dev Services gegen `postgres:18-alpine`, zusätzlich ein Build aus einem frischen
Klon. Es steht kein Ergebnis im Bericht, das nur über den Ersatzweg belegt wäre.

---

## Regime

**Berührt:** `backend/kern` (Parser, Domänenobjekte, Importdienst), `backend/persistenz`
(Migration `V2__ledger.sql`, Demodaten `V901`, Entities, Repositories), Tests und Fixtures in
beiden Modulen, `backend/persistenz/pom.xml` (Test-Abhängigkeiten).

**Unverändert:** `V1__grundschema.sql`, `RlsKontext`, `backend/mcp`, `backend/api`,
`backend/app`, `frontend/`, `infra/`, `docker-compose*.yml`, `doc/`.

Die verbotene Nachbaränderung ist unterblieben: **keine** Sichtbarkeitsstufen, keine Spalte
`stufe`, keine Vorbereitung darauf, kein Kommentar, der sie vorwegnimmt. `kontozugriff.recht`
mit `LESEN` und `SCHREIBEN` steht unverändert. Ebenso außen vor geblieben: Töpfe und ihre
Nullsummen-Invariante, die Kennzahl `verfuegbar`, Klassifikationsregeln, Belegverarbeitung,
Bankanbindung, MCP-Werkzeuge und REST-Endpunkte auf die neuen Tabellen.

## Geprüft

`make pruefen` grün, Backend und Frontend. 90 Tests: 55 `kern`, 22 `persistenz`, 13 `app` —
letztere unverändert übernommen.

---

## Offen geblieben

**Für den nächsten Zuschnitt vorbereitet, aber nicht gebaut:**

- Die **Klassifikationsschicht**. Ihre Eingangsdaten liegen jetzt einzeln vor — das war der
  Zweck dieses Sub-Sprints. Die beiden Pflicht-Regressionsfälle aus
  `constraint.klassifikation-iban-mref` (Darlehensrate, ESt-Erstattung) sind noch nicht als
  Tests hinterlegt, weil es nichts gibt, wogegen sie laufen könnten.
- Die **Zusammenführung zweier Buchungen zu einer zweiseitigen Bewegung**. Das Schema kann
  es und hält die Invarianten dafür bereit; der Importer legt jede Buchung in ihre eigene
  einseitige Bewegung. Wer die Seiten zusammenführt, ist eine Frage der Klassifikation.
- Die **Acquirer-Heuristik** aus `constraint.dauermandat-vs-pos`. Berechenbar, seit
  Mandatsreferenz und Gläubigerkennung getrennt stehen; die Spalten sind dafür partiell
  indiziert.

**Fragen, die eine Entscheidung brauchen:**

- **Dateiübergreifende Saldenkontinuität.** Soll ein neuer Auszug gegen den zuletzt
  gespeicherten desselben Kontos geprüft werden? Das braucht eine belastbare Aussage
  darüber, wann zwei Auszüge benachbart sind — über Auszugsnummern ist sie es nicht.
- **Zuordnung Datei zu Konto.** Der Importer nimmt das Zielkonto als Parameter und prüft
  optional gegen eine erwartete IBAN. Woher diese Zuordnung kommt, ist offen; Konten tragen
  bewusst keine IBAN.
- **Frontend in der Sonar-Analyse.** Ein Projekt trägt eine Analyse je Pull Request. Beide
  Sprachen in einem Scanner-Lauf kostet die Java-Analyse ihren Abhängigkeitspfad; die
  Alternative ist ein zweites Sonar-Projekt.

**Nicht ratifiziert:** Die Modellierung der Zweiseitigkeit ist eine Umsetzungsentscheidung
innerhalb von HB-06, kein neues HB. Ob sie als Präzisierung nach Kumbuka gehört, ist
Johannes' Entscheidung; von hier aus wurde nichts geschrieben.
