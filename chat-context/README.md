# chat-context

Verdichtete Protokolle der Planungsgespräche — das, was zum Verständnis einer Entscheidung
nötig ist, aber zu lang für ein Kumbuka-Memory.

## Wozu dieser Ordner

Zwischen einem Memory und einer Gesprächsmitschrift klafft eine Lücke. Ein Memory ist
verdichtet und wird zu jedem Sitzungsbeginn geladen — deshalb muss es kurz sein. Eine
vollständige Mitschrift ist lang, weitgehend redundant und wird nie wieder gelesen.

Hier steht die Mitte: der Weg zu einer Entscheidung, mit den erwogenen Alternativen und
den Zahlen, an denen sie sich bemessen hat.

## Verhältnis zu den anderen Ablagen

| Ort | Rolle | Wird geladen |
|---|---|---|
| Kumbuka `haushaltsbuch` | Verbindlichkeit — die ratifizierte Entscheidung | zu jedem Sitzungsbeginn |
| `doc/architektur/adr/` | Begründung — Alternativen und Konsequenzen | bei Bedarf |
| `chat-context/` | Weg — wie die Entscheidung entstanden ist | selten, gezielt |

**Diese Dateien werden nicht automatisch geladen.** Eine ratifizierte Entscheidung gehört
zusätzlich nach Kumbuka, sonst existiert sie für die nächste Sitzung nicht.

## Konventionen

**Dateiname:** `JJJJ-MM-TT-<thema>.md`

**Aufbau:**

```markdown
# JJJJ-MM-TT — Thema

## Ausgangslage
## Verlauf
## Ergebnis
| Entscheidung | Kumbuka-Schlüssel | ADR |
## Offen geblieben
```

Der Abschnitt „Offen geblieben" ist der wichtigste. Er verhindert, dass eine ungeklärte
Frage beim nächsten Mal stillschweigend als geklärt behandelt wird.

## Was hier nicht hineingehört

**Keine echten Kontodaten** — auch nicht in Zitaten, auch nicht in Beispielen. Keine IBANs,
keine Mandatsreferenzen, keine Namen Dritter.

Aggregierte Größen sind in Ordnung, wenn sie zum Verständnis einer Entscheidung nötig sind
(„laufender Bedarf rund 8.000 EUR/Monat"). Einzelbuchungen sind es nicht.

## Übersicht

| Datum | Thema |
|---|---|
| [2026-08-17](2026-08-17-scaffolding.md) | Scaffolding: Stack, Struktur, Zugriffskontrolle, CI |
| [2026-08-17](2026-08-17-dispatch-ledger-grundschema.md) | Dispatch: Ledger-Grundschema und validierender Importer |
| [2026-08-17](2026-08-17-rueckgabe-ledger-grundschema.md) | Rückgabe zum Dispatch Ledger-Grundschema |
| [2026-08-18](2026-08-18-rueckgabe-durchstich-bankzugang.md) | Rückgabe zum Dispatch Durchstich Bankzugang |

## Aufträge und ihre Rückgaben

Ein Dispatch beschreibt einen Auftrag, eine Rückgabe berichtet gegen ihn. Beide liegen hier,
weil sie zusammen den Weg einer Umsetzung erzählen — der Dispatch, was verlangt war, die
Rückgabe, was daraus wurde und was unterwegs auffiel.

Eine Rückgabe folgt der Gliederung ihres Dispatches, damit sie sich Punkt für Punkt dagegen
lesen lässt. Ihre wichtigsten Abschnitte sind dieselben wie hier: **Befunde außerhalb des
Auftrags** und **Offen geblieben**. Was dort nicht steht, ist beim nächsten Zuschnitt
verloren.
