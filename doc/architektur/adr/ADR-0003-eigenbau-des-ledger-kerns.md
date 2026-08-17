# ADR-0003 — Eigenbau des Ledger-Kerns

| | |
|---|---|
| Status | Angenommen |
| Datum | 2026-08-17 |
| Kumbuka | `decision.hb-06-ledger-kern` |
| Verhältnis | Setzt ADR-0001 voraus (Technologiewahl). Präzisiert HB-05 |

## Kontext

Die Planungssession Phase 3 hatte als blockierende Kernfrage: entsteht eigener
Ledger-Code, oder wird ein fertiges Haushaltsbuch als Speicher hinter eine eigene
Domänenschicht gestellt? Alles Weitere — Reporting, Dashboard, Bankanbindungsweg —
hängt daran.

Drei Kräfte wirken auf die Antwort:

**Das Topfmodell aus HB-03.** Töpfe sind virtuelle Zweckbindungen auf einem physischen
Konto, Verbindlichkeiten sind ausdrücklich keine Sparziele, und es gibt drei Topfarten mit
eigener Rechnung: Zielsparen mit Sollrate aus Zielbetrag und Zieldatum, zyklische Töpfe,
die geleert werden und sofort neu beginnen, und Dauerrücklagen mit Mindestbestand ohne
Zielbetrag. Dazu die Nullsummen-Invariante gegen den Saldo des Trägerkontos.

**Die Doppelzählung von Kartenumsätzen.** Sammelabbuchung auf dem Girokonto und
Einzelumsätze auf dem Kartenkonto beschreiben denselben Geldfluss. Erschwerend: eine Karte
erzeugt nicht nur einen Monatssammelposten, sondern zusätzlich Einzel-Settlements
innerhalb des Monats, etwa bei Bargeldverfügung. Eine Regel der Form „pro Monat matcht ein
Sammelposten alle Einzelumsätze" verliert diese Beträge oder zählt sie doppelt.

**Der vorhandene Bestand.** Zwei validierende Importer für MT940 und CAMT.052 mit harter
Saldenprüfung liegen fertig vor, geprüft über 24 Monate, acht Konten und etwa neunzig
Buchungen pro Monat, ohne Validierungsfehler.

Die ursprüngliche Begründung, mit der die fertigen Kandidaten ausgeschlossen wurden —
fehlende zeilenbasierte Zugriffskontrolle —, ist entfallen. HB-05 wurde in diesem Punkt
korrigiert: es gibt keinen Geheimhaltungsbedarf innerhalb des Haushalts, statt einer
Mandantentrennung gilt ein Produktmerkmal mit drei Sichtbarkeitsstufen, und die
Autorisierung wandert ohnehin über die Domänenschicht. Der Ausschluss musste damit neu
begründet werden — oder fallen.

Die Technologiewahl selbst ist ADR-0001. Hier geht es allein um die Frage, ob eigener
Ledger-Code entsteht.

## Entscheidung

Eigenbau des Ledger-Kerns im Modul `kern` mit `persistenz` als Speicherschicht. Kein
fremdes Haushaltsbuch als Datenhaltung.

**Intern** gilt doppelte Buchführung als Mechanismus: jede Bewegung hat zwei Seiten, das
Kartenkonto ist ein Verbindlichkeitskonto, die Sammelabbuchung ist ein Transfer zwischen
Aktiv- und Passivkonto. Damit kann die Doppelzählung nicht entstehen — sie wird nicht durch
eine Regel verhindert, sondern durch eine Invariante, die man nicht verletzen kann. Das
gilt auch für die Settlements innerhalb des Monats, weil ihr Rhythmus für die
Bilanzmechanik unerheblich ist.

**An der Oberfläche** erscheint davon nichts. Kein Kontenrahmen, kein Soll und Haben, keine
Belegpflicht, keine periodengerechte Abgrenzung. Sichtbar sind Konten und Kategorien —
siehe ADR-0004.

Das Datenmodell von Firefly III wird als **Lektüre** genutzt, insbesondere die Aufteilung
in Transaction Journals mit Splits und die Feldabbildung seines Importers. Lesen kostet
nichts, Einbinden kostet ein zweites Vokabular.

## Alternativen

### A1 — Firefly III als Speicher hinter der Domänenschicht

Dafür sprach viel: Doppelkonto-Logik, ein Regelwerk, das auf strukturierte Felder triggert,
ein reiches REST-API, ein Importer, Mehrbenutzerfähigkeit, und zwanzig Jahre eingebauter
Buchhaltungsschmerz.

Verworfen, weil Firefly Ausgabenempfänger als **Konten** modelliert, während dieses System
Kategorien als Dimension braucht. Das ergibt zwei Vokabulare und eine Übersetzungsschicht,
die dauerhaft von Menschen und Regeln mitgedacht werden muss — und zwar genau an der
Stelle, an der die Analyse aus Phase 1 zweimal Beträge in vierstelliger Höhe verloren hat,
weil eine Namensheuristik zugegriffen hat. Zusätzlich: Fireflys Budgets sind
periodenbasiert und übertragen nicht sauber über Monatsgrenzen, Sparschweine sind Beiwerk
statt Kernkonzept, und betrieblich wäre es eine PHP-Anwendung mit eigener Datenbank und
eigenem Cron neben dem Quarkus-Stack. Für ein Projekt, dessen Zielbild die
Selbstinstallation über ein Docker-Compose ist, wäre das eine Abhängigkeit mit eigener
Migrationsgeschichte, die niemand kontrolliert.

### A2 — Actual Budget als Speicher

Actual ist im Kern zero-based Envelope Budgeting mit Kategorienbeständen, die über
Monatsgrenzen übertragen — näher an HB-03 als Firefly.

Verworfen aus zwei Gründen. Erstens ist Actual local-first: der Client hält die Wahrheit,
während hier die Domänenschicht sie halten muss. Das Modell steht verkehrt herum. Zweitens
schrumpft der Vorteil bei genauem Hinsehen: Actual liefert die übertragende
Envelope-Arithmetik, aber nicht die Sollrate aus Zielbetrag und Zieldatum, nicht die
zyklische Topfart und nicht den Mindestbestand. Die Topfmathematik entsteht in jeder
Variante selbst; Actual spart eine Tabelle.

### A3 — Eigenbau (gewählt)

Die fünf Invarianten dieses Systems — selbstvalidierender Import, Nullsumme der Töpfe gegen
das Trägerkonto, Splitsumme gleich Buchungsbetrag, Sichtbarkeitskonsistenz zwischen Topf
und Trägerkonto, Kartenverbindlichkeit statt Doppelzählung — leben in einem Schema oder in
keinem. Über zwei Systeme verteilt sind sie Konventionen, und das fremde Schema ändert sich
im fremden Tempo.

Der entscheidende ökonomische Punkt liegt außerhalb der Kriterienmatrix: der teuerste Teil
jedes Ledgers ist der validierende Import, und der ist fertig. In den Varianten A1 und A2
müsste diese Arbeit auf ein fremdes Importformat **zurückgebaut** werden. Was nach Abzug
der ohnehin selbstgebauten Teile — Domänenschicht, Topfmathematik,
Projektionsautorisierung, Forderungsmodell mit Sicherheitsklassen, Prognosemodul,
Steuerkalender — als Kaufsache übrig bleibt, ist ein Schema von acht bis zwölf Tabellen
plus Regelauswerter für etwa neunzig Buchungen im Monat.

## Konsequenzen

**Leichter:**

- Ein Vokabular: Konto, Buchung, Split, Kategorie, Topf. Keine Übersetzungsschicht.
- Export ist eine Query. Reversibilität bleibt erhalten.
- Die Berechnung von `verfuegbar` läuft gegen die Datenbank statt gegen n HTTP-Aufrufe.
- Neue Felder — Sicherheitsklasse an Forderungen, Sollrate an Töpfen, Sichtbarkeitsstufe an
  Kontozugriffen — sind Migrationen, nicht Fremdschema-Erweiterungen.

**Schwerer:**

- Kein Sicherheitsnetz durch fremden, erprobten Code. Kompensiert durch die
  Saldenvalidierung als Pflichtgate und durch Pflicht-Regressionstests für die beiden
  bekannten Klassifikationsfehler.
- Buchhaltungsdetails, die eine gereifte Anwendung kennt und dieses Projekt nicht, werden
  einzeln entdeckt. Deshalb die Lektüre-Klausel.

**Harte Ausbaustufengrenze.** Der Einwand „Eigenbau kostet Monate" ist nicht widerlegt,
sondern bedingt: er trifft zu, wenn der Ledger vollständig sein soll, bevor etwas nutzt.
Deshalb gilt verbindlich — Stufe 1 umfasst Import, Konten, Buchungen mit Splits, Kategorien
und den Zahlungskalender. Töpfe, Prognose und Belegaufschlüsselung kommen danach. Fällt
diese Grenze, war diese Entscheidung falsch.

**Ausdrücklich nicht Bestandteil:** Kontenrahmen, GoBD-Konformität (die freiberufliche
Sphäre bleibt bei ihrem bisherigen System of Record), Mandantenfähigkeit,
Zahlungsauslösung, Hosting für fremde Nutzer.
