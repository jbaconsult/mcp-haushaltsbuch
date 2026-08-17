# Haushaltsbuch

Ein Haushaltsbuch, dessen primäre Schnittstelle das Gespräch ist. Das Dashboard ist die
Übersicht für beide Ehepartner und damit sekundär, aber notwendig.

---

## 1. Projektsprache

**Die Projektsprache ist Deutsch.** Das Thema taugt nicht als internationales Projekt.

| Bereich | Sprache |
|---|---|
| Dokumentation, Kommentare, Commit-Messages, PR-Beschreibungen | Deutsch |
| Oberfläche, Fehlermeldungen, MCP-Tool-Beschreibungen | Deutsch |
| Fachliche Bezeichner im Code (Domäne) | Deutsch |
| Technische Bezeichner (Framework, Infrastruktur) | Englisch |

### Regel für Bezeichner: Domäne Deutsch, Technik Englisch

Fachbegriffe werden **nicht übersetzt**. Es heißt `Topf`, nicht `Pot`; `Ruecklage`, nicht
`Reserve`; `Verfuegbar`, nicht `Available`; `Zahllast`, `Sphaere`, `Mandatsreferenz`,
`Glaeubigerkennung`, `Privatentnahme`. Eine Übersetzung dieser Begriffe verliert Bedeutung
und öffnet die Tür für Fehldeutungen, die im Ledger teuer werden.

Technische Struktur bleibt bei den Konventionen des jeweiligen Frameworks:
`Repository`, `Resource`, `Service`, `Config`, `Entity`, `Mapper`, `Handler`.

```java
// richtig
public final class TopfRepository { ... }
public Betrag verfuegbarBerechnen(KontoId konto, LocalDate stichtag) { ... }

// falsch
public final class PotRepository { ... }
public Amount calculateAvailable(AccountId account, LocalDate asOf) { ... }
```

### Umlaute in Bezeichnern

In Java-, TypeScript- und SQL-Bezeichnern werden Umlaute **ausgeschrieben**:
`Ruecklage`, `Glaeubiger`, `Sphaere`, `verfuegbar`. In Fließtext, Dokumentation, Oberfläche
und MCP-Beschreibungen wird **korrekt geschrieben**: Rücklage, Gläubiger, Sphäre, verfügbar.
Nie ASCII-Ersatz in Prosa.

---

## 2. Kumbuka-Anbindung — verbindlich

Das Projektgedächtnis liegt im Kumbuka-MCP unter dem Scope **`haushaltsbuch`**. Es ist kein
Notizzettel, sondern die Quelle der ratifizierten Entscheidungen. Der Code hat sich danach
zu richten, nicht umgekehrt.

### Zu Sitzungsbeginn — immer

```
mcp__claude_ai_Kumbuka__memory_load_context(scope="haushaltsbuch")
```

Ohne diesen Aufruf fehlen dir HB-01 bis HB-05 sowie die bindenden Constraints. Arbeite
nicht ohne sie. Bei einer eng umrissenen Frage genügt ergänzend:

```
mcp__claude_ai_Kumbuka__memory_recall(scope="haushaltsbuch", query="...")
```

### Was im Scope liegt

| Typ | Schlüsselkonvention | Inhalt |
|---|---|---|
| `decision` | `decision.hb-NN-<kurzname>` | Ratifizierte Grundsatzentscheidungen (HB-01 … HB-05) |
| `constraint` | `constraint.<kurzname>` | Bindende Randbedingungen, die Code verletzen kann |
| `glossary` | `glossary.<begriff>` | Fachbegriffe mit exakter Bedeutung |
| `status` | `status.<bereich>` | Aktueller Projektstand |
| `convention` | `convention.<kurzname>` | Arbeits- und Codekonventionen |

### Schreibregeln

- **Neue Grundsatzentscheidung** → `memory_remember` mit Typ `decision` und dem nächsten
  freien `hb-NN`. Nur, wenn Johannes sie ratifiziert hat. Nicht selbst ratifizieren.
- **Entscheidung ändert sich** → `memory_update` auf denselben `logicalId`. Nicht als
  zweite Entscheidung danebenlegen, sonst widersprechen sich zwei Memories.
- **Entscheidung fällt weg** → `memory_forget`. Eine widerlegte Entscheidung im Gedächtnis
  ist schädlicher als gar keine.
- **Offene Frage** → Typ `open_question`. Wird beim Standard-Digest nicht geladen und
  verstopft den Kontext nicht.
- **Nie in Kumbuka:** IBANs, Kontonummern, Mandatsreferenzen, Namen Dritter, Beträge aus
  echten Konten. Der Scope ist Steuerung, nicht Datenhaltung.

### Konfliktregel

Widerspricht dein Vorschlag einer ratifizierten Entscheidung, dann **setz ihn nicht um**.
Benenne den Widerspruch, nenne die betroffene HB-Nummer und frag nach. Ein stillschweigend
umgangenes HB ist ein Fehler, kein Fortschritt.

---

## 3. Ratifizierte Entscheidungen — Kurzfassung

Die verbindliche Fassung steht in Kumbuka. Diese Tabelle ist nur ein Wegweiser und kann
veralten.

| Nr. | Titel | Kern |
|---|---|---|
| HB-01 | Phasenmodell | Phase 3 (Ledger, Berechnungslogik, MCP-Oberfläche) ist nicht an Kumbuka gekoppelt |
| HB-02 | Migrationswellen | Reihenfolge 0-1-2-4-3, Ziel 01.01.2027 |
| HB-03 | Topfmodell | Verbindlichkeiten ≠ Sparziele; Töpfe virtuell auf einem physischen Konto |
| HB-04 | Zielumfang Steuer | Abgabereife Datenlage, keine eigene Übermittlung |
| HB-05 | MCP-first | MCP-Tooloberfläche wird zuerst entworfen, nicht am Ende angeschraubt |

### Bindende Constraints, die dieser Code einhalten muss

1. **Sphärentrennung** — privat/gemeinsam, freiberuflich, Finanzamt. Gekoppelt über genau
   zwei Kanten: Privatentnahme und Steuerrücklage.
2. **Klassifikation über IBAN, MREF und CRED** — niemals über Textmuster im
   Gegenpartei-Namen. Zwei Pflicht-Testfälle: die Darlehensrate über 600 EUR und die
   ESt-Erstattung 2024.
3. **Dauermandat vs. POS-Lastschrift** — Gläubigerkennungen mit mehr als drei verschiedenen
   Mandatsreferenzen sind Acquirer, ihre Buchungen sind keine Mandate.
4. **Kennzahl `verfuegbar`** — deterministisch berechnet, niemals vom Sprachmodell
   geschätzt. Umsatzsteuer ist durchlaufender Posten, nie verfügbare Liquidität.
5. **Importe validieren sich selbst** — Saldeninvarianten I1 bis I5. Was nicht aufgeht,
   landet in einer Fehlerliste, nicht im Datenbestand.
6. **Deterministik vor Sprachmodell** — ein Agent bucht nie eigenständig und bucht nie
   zwischen Töpfen um. Zweifelsfälle gehen in die Review-Queue.
7. **Haushaltskonto ohne Kreditlinie** — Finanzierung vor Belastung, Valutareihenfolge,
   Kritikalitätsreihenfolge.

---

## 4. Architektur

```
mcp-haushaltsbuch/
├── backend/          Quarkus 3.33 LTS, Java 21, Maven-Multimodul
│   ├── kern/         Domäne und Berechnungslogik — frameworkfrei
│   ├── persistenz/   JPA, Flyway, RLS-Kontext
│   ├── mcp/          MCP-Server — erstklassige Oberfläche laut HB-05
│   ├── api/          REST für das Dashboard
│   └── app/          Quarkus-Runner, bündelt die Module
├── frontend/         Next.js 16, React 19, Tailwind 4, BFF im App Router
├── infra/            Keycloak-Realm für CI, Postgres-Init
├── doc/              Architektur, Domäne, Betrieb, ADRs
└── chat-context/     Verdichtete Gesprächsprotokolle für spätere Sitzungen
```

### Abhängigkeitsrichtung

```
app ──> api ──┐
              ├──> kern <── persistenz
app ──> mcp ──┘
```

`kern` kennt niemanden. Er enthält keine Quarkus-, JPA- oder Jackson-Annotationen.
Wer in `kern` einen Framework-Import ergänzt, hat die Schichtung gebrochen.

### MCP-Modul

`mcp` ist ein eigenes Maven-Modul, weil HB-05 die Tooloberfläche zum erstklassigen
Design-Artefakt erklärt. Es ruft Domain-Services direkt auf — keine Netzwerkgrenze
zwischen MCP und Berechnungslogik, aber getrennter Code. Ein MCP-Tool enthält **keine**
Fachlogik; es übersetzt zwischen Gesprächsebene und `kern`.

Tool-Beschreibungen sind auf Deutsch und beschreiben, **wann** ein Tool zu benutzen ist,
nicht nur was es tut.

---

## 5. Zugriffskontrolle

Zeilenbasierte Zugriffskontrolle auf Kontoebene ist eine **harte Anforderung** — genau
daran sind Firefly III und Actual Budget gescheitert. Sie ist in der Datenbank verankert,
nicht in der Anwendung.

- PostgreSQL Row-Level-Security mit `FORCE ROW LEVEL SECURITY` auf jeder Tabelle mit
  Kontobezug. **`FORCE` ist nicht optional** — ohne das Schlüsselwort umgeht der
  Tabelleneigentümer sämtliche Policies.
- Die Anwendung verbindet sich mit der Rolle `haushaltsbuch_app`, die weder Eigentümer
  ist noch `BYPASSRLS` besitzt.
- Der Nutzerkontext wird pro Transaktion über `SET LOCAL app.benutzer_id = ...` gesetzt.
  Ist er nicht gesetzt, liefern die Policies **nichts** — Fail-Closed, nie Fail-Open.
- Eine Migration, die eine Tabelle mit Kontobezug ohne Policy anlegt, ist unvollständig
  und wird nicht gemergt.

Identität kommt aus Keycloak über OIDC. Der Issuer ist konfigurierbar:

| Umgebung | Issuer |
|---|---|
| Produktion | `auth.kumbuka.ai` |
| Entwicklung | `auth.jbaconsult.com` |
| CI | lokaler Keycloak aus `docker-compose.ci.yml` |

---

## 6. Arbeiten in diesem Repo

### Befehle

```bash
make hoch          # Entwicklungsstack starten
make runter        # Stack stoppen
make backend-dev   # Quarkus Dev Mode mit Live Reload
make frontend-dev  # Next.js Dev Server
make test          # alle Tests
make pruefen       # das, was auch die CI im PR prüft
```

### Vor jedem Commit

`make pruefen` muss grün sein. Die PR-Pipeline führt dieselben Schritte aus; ein rotes
lokales Ergebnis wird im PR nicht besser.

### Ablieferung eines Auftrags

Ein Auftrag ist fertig, wenn er im Repository steht — nicht, wenn er im Arbeitsbaum liegt.
Der Endzustand ist immer:

1. Ein eigener Branch, benannt nach dem Auftrag.
2. `make pruefen` grün, dann alles committet. `git status` ist leer.
3. Branch gepusht, Pull Request nach `main`, Beschreibung auf Deutsch.

Ein Bericht im Gespräch ersetzt keinen dieser drei Schritte. Uncommittete Arbeit überlebt
keinen Branchwechsel, den jemand anders auslöst — und wer sie liegen lässt, verlagert das
Risiko auf den Nächsten, der das Verzeichnis anfasst.

### Testdaten

Zielbild ist Open Source. Deshalb gilt ausnahmslos:

- IBANs, Kontonamen und Gläubigerkennungen **ausschließlich aus Konfiguration**.
- Synthetischer Demo-Datensatz für Tests und Entwicklung, von Anfang an.
- **Nie echte Werte in der Git-Historie.** Ein einmal committeter echter Kontostand ist
  auch nach `git rm` noch da.

### Geld

Beträge sind `BigDecimal` mit Skalierung 2 und `RoundingMode.HALF_UP`, in der Datenbank
`numeric(14,2)`. Niemals `double` oder `float`. Der Domänentyp ist `Betrag`; primitive
Zahlen für Geldwerte werden im Review beanstandet.

---

## 7. chat-context

`chat-context/` enthält verdichtete Protokolle von Planungsgesprächen — das, was zum
Verständnis einer Entscheidung nötig ist, aber zu lang für ein Kumbuka-Memory.

- Dateiname: `JJJJ-MM-TT-<thema>.md`
- Eine ratifizierte Entscheidung gehört **zusätzlich** nach Kumbuka. Diese Dateien werden
  nicht automatisch geladen.
- Keine echten Kontodaten, auch nicht in Zitaten.
