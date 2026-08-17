# Dispatch — Sub-Sprint 1: Ledger-Grundschema und validierender Importer

| | |
|---|---|
| Datum | 2026-08-17 |
| Apparat | Code |
| Grundlage | HB-06 / ADR-0003, HB-07 / ADR-0004 |
| Ausbaustufe | Stufe 1 nach HB-06, erster Brocken |
| Parallelbetrieb | nein, kein eigener Worktree nötig |

## Auftrag

Das Ledger bekommt seine tragenden Tabellen und einen Importer, der sich selbst validiert.
`V1__grundschema.sql` hat bewusst bei Benutzer, Konto und Kontozugriff aufgehört, weil der
Ledger-Kern eine offene Entscheidung war. Sie ist getroffen (HB-06, Eigenbau), damit ist die
Fortsetzung frei: Buchungen mit Splits, Kategorien, und der Weg, auf dem Kontoauszüge
fehlerfrei oder gar nicht in die Datenbank kommen.

Klassifikation ist **nicht** Teil dieses Auftrags. Der Auftrag ist, die strukturierten Felder
so zu speichern, dass eine spätere Klassifikationsschicht sie hat.

### Code-Anker

- `backend/persistenz/src/main/resources/db/migration/V1__grundschema.sql` — die neue
  Migration ist `V2__ledger.sql` daneben. `ALTER DEFAULT PRIVILEGES` aus V1 deckt die
  Grants für neue Tabellen bereits ab; die Policies deckt es nicht.
- `backend/persistenz/src/main/java/de/jbaconsult/haushaltsbuch/persistenz/` — bestehend:
  `KontoEntity`, `KontoRepository`, `BenutzerEntity`, `BenutzeridentitaetEntity`,
  `BenutzerRepository`, `RlsKontext`. Die neuen Entities und Repositories kommen in dasselbe
  Paket, dem Muster von `KontoEntity` und `KontoRepository` folgend.
- `backend/kern/` — hierhin gehören die Parser als **reine Funktionen über Text**:
  Eingabe ein Reader oder String, Ausgabe Domänenobjekte plus Fehlerliste. Kein Datei-IO,
  kein Framework-Import, weil `kern` niemanden kennt. Das Lesen der Datei und das Schreiben
  in die Datenbank liegen außerhalb des Parsers.
- `RlsPolicyVollstaendigkeitTest` — bestehender CI-Test. Er muss nach dieser Migration
  weiterhin grün sein, was bedeutet: jede neue Tabelle mit Kontobezug braucht ihre Policy.

### Schema

`buchung` — eine Buchung je Geldbewegung, mit Kontobezug, Buchungstag, Valuta, Betrag,
Bankreferenz für die Deduplizierung, und den strukturierten Feldern getrennt voneinander:
Gegenpartei-Name, Gegenpartei-IBAN, Mandatsreferenz, Gläubigerkennung,
End-to-End-Referenz, Verwendungszweck im Volltext.

**Das ist die zentrale Anforderung dieses Sub-Sprints.** Diese Felder dürfen nicht zu einem
Textblob zusammengeklebt werden, auch nicht „vorläufig". Der Grund steht unter Frame.

`buchungssplit` — n Gegenposten je Buchung, jeder mit Kategorie und Betrag.

`kategorie` und `kategoriegruppe` — benutzereditierbar, stabile UUID als Identität, eine
Ebene Gruppierung.

Transfers zwischen zwei eigenen Konten sind **eine** Bewegung mit zwei Seiten, nicht zwei
unabhängige Buchungen. Das Kartenkonto ist dabei ein Verbindlichkeitskonto — `konto.art`
kennt `KREDITKONTO` bereits. Wie die Zweiseitigkeit modelliert wird, ist deine Entscheidung;
die Anforderung ist, dass eine Auswertung eine Sammelabbuchung und die zugehörigen
Einzelumsätze nicht beide zählen kann, ohne dass eine Regel daran denken muss.

### Importer

MT940 und CAMT.052. Die Pflichtinvarianten sind:

- **I1** Anfangssaldo plus Summe der Buchungen gleich Endsaldo, je Auszug beziehungsweise
  Report.
- **I2** Endsaldo Block N gleich Anfangssaldo Block N+1, je Konto.
- **I3** Jede Buchung hat ihren Detailblock.
- **I4** Deduplizierung über die Bankreferenz, weil sich Exportzeiträume an den Randtagen
  überlappen.
- **I5** IBAN-Prüfsumme, weil MT940-Zeilen bei etwa 55 Zeichen mitten in IBANs umbrechen.

Zwei MT940-Fallen, empirisch bezahlt: das Zeichen nach C/D in Feld 61 ist das dritte Zeichen
von „EUR", nicht die Stornokennung — Storno steht davor als RC beziehungsweise RD. Und das
Buchungsdatum kommt nur als MMTT ohne Jahr und muss aus der Valuta abgeleitet werden, was am
Jahreswechsel nicht naiv funktioniert.

### Akzeptanzkriterien

1. `V2__ledger.sql` legt jede Tabelle mit Kontobezug mit `ENABLE` **und** `FORCE ROW LEVEL
   SECURITY` an, und `RlsPolicyVollstaendigkeitTest` ist grün.
2. Ohne gesetzten Benutzerkontext liefert eine Abfrage auf `buchung` und `buchungssplit`
   null Zeilen. Ein Test weist das nach.
3. Ein Test weist nach, dass die Summe der Splits einer Buchung deren Betrag entspricht und
   dass eine Verletzung dieser Bedingung abgelehnt wird — in der Datenbank, nicht nur im
   Java-Code.
4. Der Importer schreibt einen Auszug **vollständig oder überhaupt nicht**. Verletzt eine
   Invariante, landet der Auszug in einer Fehlerliste mit benannter Invariante, und die
   Datenbank enthält keine Zeile daraus.
5. Zwei Läufe desselben Auszugs erzeugen denselben Datenbestand (I4).
6. Ein Test weist nach, dass Mandatsreferenz, Gläubigerkennung und Gegenpartei-IBAN nach dem
   Import als eigene, abfragbare Spalten vorliegen und nicht nur im Verwendungszweck stehen.
7. Ein MT940-Fixture mit einem Zeilenumbruch mitten in einer IBAN wird korrekt gelesen (I5).
8. Ein MT940-Fixture mit einem Buchungstag im Dezember und einer Valuta im Januar wird auf
   das richtige Jahr abgebildet.
9. Ein MT940-Fixture mit einer Stornobuchung wird als Storno erkannt und nicht als
   Währungsartefakt.
10. Alle Fixtures sind synthetisch. Keine echten Kontonummern, Beträge, Mandatsreferenzen,
    Gläubigerkennungen, Verwendungszwecke oder Namen.

### Rote Probe

Zwei Hälften, beide gehören dazu.

**Erste Hälfte, das Gate greift.** Nimm ein Fixture, das sauber importiert, und verändere in
der Kopie den Endsaldo um einen Cent. Erwartung: der Import dieses Auszugs schlägt fehl, die
Fehlerliste nennt I1, und `SELECT count(*) FROM buchung` für diesen Auszug ist null. Ein
Import, der neun von zehn Buchungen schreibt und die zehnte meldet, ist ein Fehlschlag der
Probe, nicht ein Teilerfolg.

**Zweite Hälfte, die Daten überleben.** Importiere zuerst einen sauberen Auszug, dann den
manipulierten. Erwartung: der Bestand aus dem ersten Auszug ist danach unverändert
vollständig. Ein Rollback, der zu weit zurückrollt, ist derselbe Datenverlust wie ein
fehlender Rollback, nur schwerer zu bemerken.

## Frame

**HB-06 / ADR-0003 — Eigenbau des Ledger-Kerns.** Der Ledger wird selbst gebaut; intern gilt
doppelte Buchführung als Mechanismus, an der Oberfläche erscheint kein Kontenrahmen.
Konsequenz für diesen Auftrag: das Schema darf die Zweiseitigkeit von Transfers und die
Kartenverbindlichkeit als Invariante ausdrücken, statt sie einer Auswertungsregel zu
überlassen.

**HB-07 / ADR-0004 — Kategorien und Splits.** Kategorien sind eine benutzereditierbare
Dimension am Split, keine Konten. Die Split-Tabelle **ist** die Positionsebene; anfangs hat
jede Buchung genau einen Split. Konsequenz: Kategorien brauchen stabile IDs, weil Regeln und
Historie sie referenzieren, und Löschen braucht Merge oder Sperre statt Kaskade.

**`constraint.import-saldenvalidierung`** — ein Import, der sich nicht selbst validiert, gilt
als nicht erfolgt. Was nicht aufgeht, landet in einer Fehlerliste, nicht im Datenbestand.

**`constraint.klassifikation-iban-mref`** — Transaktionen werden nie über Textmuster im
Gegenpartei-Namen klassifiziert, sondern über IBAN, Mandatsreferenz und Gläubigerkennung.
**Das ist der Grund für Akzeptanzkriterium 6.** Eine Namensheuristik hat in der Analyse
zweimal vierstellige Posten verschluckt: eine monatliche Darlehensrate, weil die Gegenpartei
auf beide Kontoinhaber lautete und als interne Umbuchung gefiltert wurde, und eine
Steuererstattung, weil die Bank eine abgekürzte Schreibweise verwendet und der Filter nach
dem ausgeschriebenen Begriff suchte. Beide Fälle werden Pflicht-Regressionstests, sobald eine
Klassifikationsschicht existiert. Dieser Sub-Sprint baut sie nicht — er muss nur dafür
sorgen, dass ihre Eingabedaten nicht schon beim Import verlorengehen.

**`constraint.dauermandat-vs-pos`** — Gläubigerkennungen mit mehr als drei verschiedenen
Mandatsreferenzen sind Zahlungsdienstleister, ihre Buchungen sind keine Mandate. Auch das ist
Klassifikation und nicht in Scope, aber die Heuristik ist nur berechenbar, wenn
Gläubigerkennung und Mandatsreferenz getrennt gespeichert sind.

**Vorgeschichte, damit eine überraschende Messung als überraschend erkennbar ist:** die
Referenzimplementierung der Parser existiert außerhalb dieses Repositories in Python und hat
den validierten Bestand über zwei Jahre und acht Konten ohne Validierungsfehler erzeugt. Der
Java-Code wird neu geschrieben, nicht portiert. Wenn ein Fixture, das dort durchläuft, hier
scheitert, ist das ein Befund und keine Fixture-Schwäche.

**Erwarteter Testschaden:** keiner. V1 wird nicht angefasst, bestehende Tests bleiben
unberührt. Geht `RlsPolicyVollstaendigkeitTest` rot, ist eine Policy vergessen — das ist der
Zweck des Tests und kein Kollateralschaden.

## Grenze

Alles Folgende ist außerhalb, jeweils mit Grund.

**Sichtbarkeitsstufen `full` / `sum` / `none`.** ADR-0006 hat Status *Vorgeschlagen* und ist
nicht ratifiziert. Das ist die verlockendste Nachbaränderung dieses Auftrags, weil
`kontozugriff` ohnehin angefasst werden könnte — genau deshalb ausdrücklich verboten. Keine
Spalte `stufe`, keine Vorbereitung, kein Kommentar, der sie vorwegnimmt. `kontozugriff.recht`
mit `LESEN` und `SCHREIBEN` bleibt unverändert; es ist die Schreibachse und hat mit den
Stufen nichts zu tun.

**Töpfe und die Nullsummen-Invariante.** HB-06 legt sie ausdrücklich in Stufe 2. Ein Schema,
das sie „nur schon mal anlegt", nimmt Entscheidungen über Topfarten und Sollraten vorweg, die
nicht getroffen sind.

**Die Kennzahl `verfuegbar`.** Braucht Verbindlichkeiten, Steuerkalender und Forderungen mit
Sicherheitsklasse. Keines davon existiert. Eine halbe Kennzahl ist schlimmer als keine, weil
sie benutzt wird.

**Klassifikationsregeln und die Regeltabelle.** Eigener Sub-Sprint. Ein Importer, der
unterwegs schon kategorisiert, macht den späteren Trockenlauf-Modus unmöglich, den die
Reichweitenprüfung braucht.

**Belegverarbeitung, OCR, Aufschlüsselung.** Stufe 2 laut HB-06.

**Bankanbindung.** Die Entscheidung zwischen PSD2-Aggregator und FinTS ist offen (E2). Dieser
Importer liest Dateien, nichts anderes.

**MCP-Werkzeuge und REST-Endpunkte auf die neuen Tabellen.** Werkzeuge sind laut ADR-0007
Teil des Sicherheitsmodells und werden einer Kommandoklasse zugeordnet, bevor sie entstehen.
Ein „schnelles Lese-Endpunkt zum Ausprobieren" umgeht diese Zuordnung.

## Regime

Berührt werden dürfen: `backend/persistenz` (neue Migration, neue Entities und
Repositories), `backend/kern` (Parser und Domänenobjekte), sowie Tests und Test-Fixtures in
beiden Modulen.

Unberührt bleiben: `V1__grundschema.sql`, `RlsKontext`, `backend/mcp`, `backend/api`,
`backend/app`, `frontend/`, `infra/`, `docker-compose*.yml`, `doc/`.

Ein Punkt, der eine Rückfrage statt einer Entscheidung verlangt: falls sich beim Bauen
zeigt, dass Parser und Import ein **eigenes Maven-Modul** brauchen, ist das eine Änderung an
der Modulstruktur aus ADR-0001 und damit nicht Teil dieses Auftrags. Melde es zurück, statt
das Modul anzulegen.

Stoppbedingung: wenn eine der Invarianten I1 bis I5 sich am realen Format als anders
verhält als hier beschrieben, halte an und melde den Befund. Ein Importer, der eine
Invariante aufweicht, damit die Fixtures durchlaufen, ist genau der Fehler, den die
Saldenvalidierung verhindern soll.
