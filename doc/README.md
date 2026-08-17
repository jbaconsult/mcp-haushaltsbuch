# Dokumentation

## Aufbau

| Ordner | Inhalt |
|---|---|
| [`architektur/`](architektur/) | Systemüberblick, Schichtung, Zugriffskontrolle |
| [`architektur/adr/`](architektur/adr/) | Architekturentscheidungen mit Begründung |
| [`domaene/`](domaene/) | Glossar der Fachbegriffe |
| [`betrieb/`](betrieb/) | Lokale Entwicklung, CI-Pipeline |

## Was wohin gehört

Die Ablage hat drei Ebenen, und die Trennung ist wichtig, weil sonst jede von ihnen
unbrauchbar wird:

**Kumbuka-Scope `haushaltsbuch`** — die ratifizierten Entscheidungen und bindenden
Constraints. Kurz, verdichtet, wird zu jedem Sitzungsbeginn geladen. Das ist die Quelle
der Wahrheit für das *Warum*.

**`doc/`** — die ausformulierte Fassung: wie das System gebaut ist, wie man es startet,
was ein Fachbegriff genau bedeutet. Beschreibt das *Wie*. Darf ausführlich sein.

**`chat-context/`** — Protokolle der Gespräche, aus denen Entscheidungen entstanden sind.
Nur lesen, wenn man den Weg zu einer Entscheidung nachvollziehen will.

Eine ratifizierte Entscheidung steht in Kumbuka **und** als ADR in `doc/architektur/adr/`.
Die ADR trägt die Begründung, das Memory trägt die Verbindlichkeit.

## Sprache

Deutsch, mit korrekten Umlauten. Codebeispiele folgen der Bezeichnerregel aus `CLAUDE.md`:
Fachbegriffe deutsch mit ausgeschriebenen Umlauten, technische Struktur englisch.
