# ADR-0001 — Technologiewahl für das Scaffolding

| | |
|---|---|
| Status | Angenommen |
| Datum | 2026-08-17 |
| Kumbuka | — (Umsetzungsentscheidung unter HB-05) |

## Kontext

Phase 3 laut HB-01 umfasst Ledger, Berechnungslogik und MCP-Oberfläche. Sie ist
ausdrücklich **nicht** an die Fertigstellung der Kumbuka-Plattform gekoppelt: Kumbuka wird
das Steuerungsportal davor, nicht das Fundament darunter.

Zwei Uhren laufen parallel — die entspannte für die Software mit Zielbild Ende 2027 und die
unerbittliche für das Geld mit den Steuerterminen. Der Stack muss deshalb über Jahre
wartbar sein, nicht in einem Wochenende fertig.

Vier Anforderungen schränken die Wahl ein:

1. Zeilenbasierte Zugriffskontrolle auf Kontoebene (HB-05).
2. Die MCP-Tooloberfläche ist erstklassiges Design-Artefakt, kein nachträglicher Adapter.
3. Geldbeträge exakt — die Kennzahl `verfuegbar` entscheidet über „geht das oder nicht".
4. Zwei Nutzer, ein Entwickler. Betriebsaufwand muss klein bleiben.

## Entscheidung

| Bereich | Wahl |
|---|---|
| Backend | Quarkus 3.33 LTS, Java 21, Maven, JVM-Modus |
| Datenhaltung | PostgreSQL 18, Hibernate ORM mit Panache, Flyway |
| MCP | `quarkus-mcp-server-http` 1.13.1 (Quarkiverse) in einem eigenen Modul |
| Dashboard | Next.js 16 (App Router), React 19, Tailwind 4, TypeScript |
| Identität | Keycloak über OIDC, BFF hält die Sitzung |
| CI | GitHub Actions, Multi-Stage-Images nach GHCR |

Quarkus 3.33 ist die aktuelle LTS-Linie. Die MCP-Extension 1.13.1 wird gegen Quarkus 3.33.2
gebaut — die Versionen passen ohne Nachjustieren zusammen, was bei einer Quarkiverse-
Extension nicht selbstverständlich ist.

## Alternativen

**Ein fertiges Werkzeug statt Eigenbau.** Firefly III und Actual Budget wurden geprüft und
scheitern beide an Anforderung 1: Firefly III hat Multi-User, aber keine feingranulare
Kontoberechtigung; Actual Budget ist ein geteiltes Haushaltsbudget ohne Mandantentrennung.
Anforderung 2 erfüllt keines von beiden.

**Python mit FastAPI.** Der offensichtliche Weg, weil die MT940- und CAMT.052-Parser aus
Phase 1 dort schon liegen und das MCP-SDK ausgereift ist. Verworfen zugunsten der
Typsicherheit: eine Ledger-Domäne mit Sphärentrennung, Topftypen und
Berechnungsinvarianten profitiert stark davon, dass Fehler beim Kompilieren auffallen und
nicht beim Jahresabschluss. `BigDecimal` und JPA sind hier ein echter Vorteil.

Der Preis ist real: die Parser müssen portiert werden. Sie sind gut testbar, weil
Anfangssaldo plus Buchungen gleich Endsaldo eine maschinell prüfbare Invariante ist — die
Portierung ist Aufwand, aber kein Risiko.

**TypeScript mit NestJS.** Ein Sprachraum mit dem Frontend, geteilte Typen. Verworfen, weil
Geldarithmetik eine externe Bibliothek braucht und JavaScript-Zahlen bei Beträgen eine
stehende Fehlerquelle sind.

**GraalVM-Native als Standardartefakt.** Start in Millisekunden, etwa 50 MB RAM. Verworfen
für den Anfang: Native-Builds kosten im CI mehrere Minuten pro Pull Request und
Reflection-Konfiguration kann bei JPA und Jackson unerwartet stören. Der JVM-Modus ist für
zwei Nutzer reichlich schnell. Native bleibt als CI-Profil nachrüstbar.

**MCP als eigener Dienst.** Unabhängig deploybar, dafür eine Netzwerkgrenze zwischen
Gesprächsschnittstelle und Berechnungslogik — und damit eine Serialisierungsschicht, in der
Geldbeträge Genauigkeit verlieren können. Bei zwei Nutzern bringt die Trennung keinen
Nutzen, der das aufwiegt.

## Konsequenzen

**Leichter:**
- Die Domäne ist typsicher. Eine Verwechslung von `KontoId` und `TopfId` wird vom Compiler
  gefunden, nicht vom Steuerberater.
- Quarkus Dev Services startet Postgres im Dev Mode und in Tests von selbst — kein
  manuelles Datenbank-Setup.
- Der Panache-Zugriff auf Hibernate lässt RLS unangetastet: die Sitzungsvariable wird pro
  Transaktion gesetzt, die Policies greifen darunter.

**Schwerer:**
- Die Parser aus Phase 1 müssen nach Java portiert werden.
- Java 21 als Zielversion bei lokal installiertem JDK 26 — die CI pinnt Temurin 21, lokal
  ist ein passendes JDK nötig.
- Zwei Sprachräume im Repo bedeuten zwei Build-Werkzeuge und zwei Abhängigkeitsbäume.

**Zum MCP-Transport:**

Gewählt ist die stabile Extension-Linie 1.13.1 über das Artefakt
`quarkus-mcp-server-http`. Es bringt **beide** Transporte mit:

| Transport | Endpunkt | Status im MCP-Protokoll |
|---|---|---|
| Streamable HTTP | `/mcp` | aktueller Standard |
| SSE | `/mcp/sse` | abgelöst, aus Kompatibilität weiter verfügbar |

Das früher separate `quarkus-mcp-server-sse` ist nur noch eine Weiterleitung auf dasselbe
Artefakt. Es besteht also kein Grund, auf die 2.x-Linie zu warten — sie steht ohnehin erst
bei `2.0.0.CR2` und baut gegen dieselbe Quarkus-LTS.

Was die 2.x-Linie zusätzlich bringt, ist `quarkus-mcp-server-oidc`: eine Extension, die
die Identität für MCP-Verbindungen selbst auflöst. Bis dahin übernimmt das
`McpBenutzerkontext`, aufgerufen zu Beginn jedes Tools. Der Aufruf ist bewusst sichtbar und
nicht in einen Interceptor versteckt.

Die Extension-Version wird im Eltern-POM gepinnt und bewusst angehoben, nicht per
Bereichsangabe.
