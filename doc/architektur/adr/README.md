# Architekturentscheidungen (ADR)

Eine ADR hält fest, **warum** eine Entscheidung so und nicht anders gefallen ist. Der Code
zeigt das Ergebnis; die ADR zeigt die verworfenen Alternativen — und die sind das
Wertvolle, wenn in einem Jahr jemand fragt, warum hier nicht das Naheliegende steht.

## Verhältnis zu Kumbuka

| Ort | Rolle |
|---|---|
| Kumbuka-Scope `haushaltsbuch` | Verbindlichkeit. Wird zu jedem Sitzungsbeginn geladen |
| `doc/architektur/adr/` | Begründung. Ausführlich, mit Alternativen und Konsequenzen |

Eine ratifizierte Grundsatzentscheidung steht an **beiden** Orten. Die HB-Nummer verbindet
sie.

## Namensschema

`ADR-NNNN-<kurzname>.md`, fortlaufend nummeriert. Eine ADR wird nicht gelöscht, wenn sie
überholt ist — sie bekommt den Status `Abgelöst durch ADR-NNNN`. Der Weg zu einer
Entscheidung gehört zur Entscheidung.

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

## Übersicht

| Nr. | Titel | Status |
|---|---|---|
| [0001](ADR-0001-technologiewahl.md) | Technologiewahl für das Scaffolding | Angenommen |
| [0002](ADR-0002-zugriffskontrolle-in-der-datenbank.md) | Zugriffskontrolle in der Datenbank | Angenommen |
