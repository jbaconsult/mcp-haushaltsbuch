# Lokale Entwicklung

## Voraussetzungen

| Werkzeug | Version | Anmerkung |
|---|---|---|
| JDK | 21 | Zielversion. Ein neueres JDK kann den Quarkus-Build stören |
| Maven | 3.9+ | oder `./mvnw` aus `backend/` |
| Node | 24 LTS | |
| Docker | mit Compose v2 | für Postgres, Keycloak und Dev Services |

### Hinweis zur JDK-Version

Das Projekt kompiliert auf `release 21`. Ist lokal nur ein neueres JDK installiert, kann
die Quarkus-Augmentation stolpern — sie verarbeitet Bytecode und ist auf die unterstützten
Klassendateiversionen abgestimmt. Die CI pinnt Temurin 21.

```bash
brew install openjdk@21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

---

## Zwei Wege, das System zu starten

### Weg 1 — Dev Mode (Alltag)

Der angenehmere Weg. Quarkus startet Postgres über **Dev Services** selbst; eine
Datenbank muss nicht eingerichtet werden.

```bash
make backend-dev     # http://localhost:8080
make frontend-dev    # http://localhost:3000
```

Live Reload greift bei Java-Änderungen ohne Neustart. Die Dev-UI liegt auf
http://localhost:8080/q/dev.

Im Dev Mode läuft die Anwendung ohne OIDC (`%dev.quarkus.oidc.enabled=false`) unter einem
festen Entwicklungsbenutzer. Das ist bewusst so: einen Identity-Provider für jede
Codeänderung im Weg zu haben, kostet mehr als es prüft. Die Zugriffskontrolle wird
stattdessen dort geprüft, wo sie hingehört — im Test.

### Weg 2 — Compose (nah am Betrieb)

```bash
cp .env.example .env    # Platzhalter ausfüllen
make hoch
make logs
make runter
```

Startet Postgres, Backend und Frontend als Container. OIDC läuft gegen den externen
Keycloak aus `OIDC_AUTH_SERVER_URL` — voreingestellt `auth.jbaconsult.com`.

Soll ohne externen Identity-Provider gearbeitet werden, lässt sich der CI-Stack samt
lokalem Keycloak verwenden:

```bash
make hoch-ci
```

---

## Datenbank

### Rollen

| Rolle | Zweck |
|---|---|
| `haushaltsbuch_eigentuemer` | Schema und Flyway-Migrationen. Damit wird verbunden |
| `haushaltsbuch_app` | Rolle jeder fachlichen Abfrage. Von `V1__grundschema.sql` angelegt |

Die Verbindung läuft als Eigentümer, damit Flyway migrieren kann. Jede fachliche
Transaktion wechselt dann per `SET LOCAL ROLE` in `haushaltsbuch_app` — das übernimmt
`RlsKontext`. Ausführlich in
[`doc/architektur/03-zugriffskontrolle.md`](../architektur/03-zugriffskontrolle.md).

### Migrationen

Flyway, in `backend/persistenz/src/main/resources/db/migration`. Namensschema
`V<nr>__<beschreibung>.sql`.

Eine Migration, die eine Tabelle mit Kontobezug anlegt, muss **im selben Schritt** die
RLS-Policy setzen. `RlsPolicyVollstaendigkeitTest` prüft das und macht eine vergessene
Policy zu einem roten Build.

### Direkter Zugriff

```bash
make db-shell
```

Verbindet als Eigentümer. Zum Prüfen der Policies aus Anwendungssicht:

```sql
SET ROLE haushaltsbuch_app;
SET LOCAL app.benutzer_id = '00000000-0000-0000-0000-000000000001';
SELECT * FROM konto;
```

Ohne das `SET LOCAL` kommt nichts zurück. Das ist kein Fehler, sondern die Fail-Closed-
Voreinstellung.

---

## Tests

```bash
make test        # alles
make pruefen     # das, was auch die CI im PR prüft
```

`make pruefen` ist die Vorabprüfung vor jedem Commit. Sie führt dieselben Schritte aus wie
die Pipeline — ein rotes lokales Ergebnis wird im Pull Request nicht besser.

| Testart | Ort | Braucht Docker |
|---|---|---|
| Domänentests | `kern` | nein |
| Persistenz- und RLS-Tests | `app` | ja (Dev Services) |
| Frontend | `frontend` | nein |

Die Tests in `kern` laufen ohne Containerstart in Millisekunden. Das ist Absicht: die
Berechnung von `verfuegbar` ist die kritischste Logik im System und muss so schnell
prüfbar sein, dass man sie auch tatsächlich oft prüft.

---

## MCP-Endpunkt

Die Extension bringt beide Transporte mit:

| Transport | Adresse | Status im MCP-Protokoll |
|---|---|---|
| Streamable HTTP | `http://localhost:8080/mcp` | aktueller Standard |
| SSE | `http://localhost:8080/mcp/sse` | abgelöst, aus Kompatibilität verfügbar |

Zum Einbinden in Claude Code:

```bash
claude mcp add --transport http haushaltsbuch http://localhost:8080/mcp
```

Im Dev Mode ist der Endpunkt ungeschützt. In allen anderen Profilen verlangt er ein
gültiges OIDC-Token.

---

## Testdaten

Zielbild ist Open Source. Deshalb ausnahmslos:

- IBANs, Kontonamen und Gläubigerkennungen **nur aus Konfiguration**.
- Synthetischer Demo-Datensatz in `V2__demodaten.sql`, nur im Dev- und Testprofil geladen.
- **Nie echte Werte in der Git-Historie.** Ein einmal committeter echter Kontostand bleibt
  auch nach `git rm` in der Historie.

Echte Auszüge gehören nach `daten/` — der Ordner ist per `.gitignore` ausgeschlossen.
