# Architekturentscheidungen (ADR)

Eine ADR hält fest, **warum** eine Entscheidung so und nicht anders gefallen ist. Der Code zeigt
das Ergebnis; die ADR zeigt die verworfenen Alternativen — und die sind das Wertvolle, wenn in
einem Jahr jemand fragt, warum hier nicht das Naheliegende steht.

## Verhältnis zu Kumbuka

| Ort | Rolle |
|---|---|
| Kumbuka-Scope `haushaltsbuch` | Verbindlichkeit. Wird zu jedem Sitzungsbeginn geladen |
| `doc/architektur/adr/` | Begründung. Ausführlich, mit Alternativen und Konsequenzen |

Eine ratifizierte Grundsatzentscheidung steht an **beiden** Orten. Die HB-Nummer verbindet sie.
Nicht jede ADR hat eine HB-Nummer — eine Bauentscheidung innerhalb eines ratifizierten Rahmens
braucht keine.

## Namensschema

`ADR-NNNN-<kurzname>.md`, fortlaufend nummeriert. Eine ADR wird nicht gelöscht, wenn sie überholt
ist — sie bekommt den Status `Abgelöst durch ADR-NNNN`. Der Weg zu einer Entscheidung gehört zur
Entscheidung. Wird nur ein Teil überholt, benennt die neue ADR den betroffenen Abschnitt, und die
alte bekommt eine Vorwärtsnotiz am Kopf.

## Aufbau

```markdown
# ADR-NNNN — Titel

| | |
|---|---|
| Status | Angenommen / Vorgeschlagen / Abgelöst durch ADR-NNNN |
| Datum | JJJJ-MM-TT |
| Kumbuka | decision.hb-NN-<kurzname> (falls ratifiziert) |

## Kontext
Welche Lage erzwingt eine Entscheidung?

## Entscheidung
Was wurde entschieden?

## Alternativen
Was wurde erwogen und aus welchem Grund verworfen?

## Konsequenzen
Was wird dadurch leichter, was schwerer?
```

## Keine echten Daten

Dies ist ein öffentliches Repository. Die ADRs benennen ihre empirische Grundlage deshalb
**strukturell** — „ein Vorauszahlungstermin, dessen Betrag die vorhandene Rücklage um
Größenordnungen übersteigt" statt konkreter Zahlen. Nicht in dieses Repository gehören: Beträge
aus echten Konten, Kontonummern und IBANs, Mandatsreferenzen und Gläubigerkennungen,
Verwendungszwecke, Namen von Geschäftspartnern und Dritten, sowie Datumsangaben, die sich einer
einzelnen Buchung zuordnen lassen.

Die Belege zu den Entscheidungen liegen außerhalb, referenziert über die ADR-Nummer. Ohne diese
Trennung wäre entweder die Entscheidung nicht nachvollziehbar oder der Haushalt nicht geschützt;
beides ist vermeidbar.

## Übersicht

| Nr. | Titel | Status | HB |
|---|---|---|---|
| [0001](ADR-0001-technologiewahl.md) | Technologiewahl für das Scaffolding | Angenommen | — |
| [0002](ADR-0002-zugriffskontrolle-in-der-datenbank.md) | Zugriffskontrolle in der Datenbank | Angenommen, teils in Revision | HB-05 |
| [0003](ADR-0003-eigenbau-des-ledger-kerns.md) | Eigenbau des Ledger-Kerns | Angenommen | HB-06 |
| [0004](ADR-0004-kategorien-und-splits.md) | Kategorien und Splits statt Kontenrahmen | Angenommen | HB-07 |
| [0005](ADR-0005-zugangswege-und-authentifizierung.md) | Zugangswege und Authentifizierung | Angenommen | — |
| [0006](ADR-0006-projektionsautorisierung.md) | Projektionsautorisierung | **Vorgeschlagen** | — |
| [0007](ADR-0007-mcp-kommandoklassen.md) | Kommandoklassen der MCP-Oberfläche | Angenommen | — |
| [0008](ADR-0008-modul-finanzprognose.md) | Finanzprognose als eigenes Modul | Angenommen | HB-08 |

## Offene Punkte

- **ADR-0006 ist nicht ratifiziert.** Bis dahin bleibt `doc/architektur/03-zugriffskontrolle.md`
  auf dem Stand von ADR-0002. Kein Code sollte die dreistufige Sichtbarkeit umsetzen, solange die
  Zuständigkeitsverteilung offen ist.
- **HB-01 bis HB-04 haben keine ADR.** Sie sind ratifiziert und liegen im Kumbuka-Scope, aber ihre
  Herleitung stammt aus einer früheren Sitzung und ist hier nicht rekonstruiert. Eine
  Nachdokumentation wäre Erfindung, solange die Protokolle fehlen.
