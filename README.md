# Haushaltsbuch

Ein Haushaltsbuch, das mit der KI sprechen kann.

Die primäre Schnittstelle ist das Gespräch, nicht das Dashboard. Die Leitfrage im Alltag
lautet „geht das oder nicht?" — und sie wird aus einer berechneten Kennzahl beantwortet,
nicht aus einer Schätzung. Das Dashboard ist die gemeinsame Übersicht für alle Nutzer eines
Haushalts und damit sekundär, aber notwendig.

Der eigentliche Hebel liegt nicht in der rückblickenden Buchhaltung, sondern in der
Vorausschau: bei schwankenden Einnahmen und terminierten Verbindlichkeiten muss Planbarkeit
hergestellt werden, statt sich von selbst zu ergeben.

## Warum kein fertiges Werkzeug

Firefly III und Actual Budget wurden geprüft und verworfen. Die Gründe stehen ausführlich in
[ADR-0003](doc/architektur/adr/ADR-0003-eigenbau-des-ledger-kerns.md); kurz gefasst:

1. **Das Datenmodell passt nicht.** Firefly modelliert Ausgabenempfänger als Konten, dieses
   System braucht Kategorien als Dimension am Buchungssplit. Das ergibt zwei Vokabulare und
   eine Übersetzungsschicht — genau dort, wo Auswertungsfehler teuer werden. Actual ist
   local-first, hält also die Wahrheit im Client, während sie hier in der Domänenschicht
   liegen muss.
2. **Die Invarianten leben in einem Schema oder in keinem.** Selbstvalidierender Import,
   Nullsummen der Töpfe gegen ihr Trägerkonto, Splitsumme gleich Buchungsbetrag,
   Kartenverbindlichkeit statt Doppelzählung — über zwei Systeme verteilt sind das
   Konventionen statt Invarianten.
3. **Der teuerste Teil ist der validierende Import**, und der ist konzeptionell fertig. Bei
   einem fremden Ledger müsste diese Arbeit auf ein fremdes Importformat zurückgebaut
   werden.
4. **Die Gesprächsoberfläche ist das Produkt**, nicht ein Adapter, den man am Ende
   anschraubt.

Was ausdrücklich **nicht** Ziel ist: ein Kontenrahmen, GoBD-Konformität, Mandantenfähigkeit,
das Auslösen von Zahlungen, und der Betrieb als Dienst für fremde Nutzer. Das Projekt ist
zur Selbstinstallation gedacht.

## Stack

| Bereich | Technologie |
|---|---|
| Backend | Quarkus 3.33 LTS, Java 21, Maven-Multimodul |
| Datenhaltung | PostgreSQL 18 mit Row-Level-Security |
| Gesprächsschnittstelle | MCP-Server als eigenes Modul im Backend |
| Dashboard | Next.js 16, React 19, Tailwind 4, BFF im App Router |
| Identität | Keycloak über OIDC |
| CI | GitHub Actions, Images nach GHCR |

## Schnellstart

```bash
cp .env.example .env      # Platzhalter ausfüllen
make hoch                 # Postgres, Backend, Frontend
```

| Dienst | Adresse |
|---|---|
| Dashboard | http://localhost:3000 |
| Backend | http://localhost:8080 |
| MCP-Endpunkt | http://localhost:8080/mcp |
| OpenAPI | http://localhost:8080/q/swagger-ui |

Für die tägliche Arbeit ist der Quarkus Dev Mode angenehmer — er startet Postgres über Dev
Services selbst und lädt Änderungen ohne Neustart:

```bash
make backend-dev
make frontend-dev
```

Details in [`doc/betrieb/lokale-entwicklung.md`](doc/betrieb/lokale-entwicklung.md).

## Aufbau

```
backend/     Quarkus-Multimodul: kern, persistenz, mcp, api, app
frontend/    Next.js mit BFF
infra/       Keycloak-Realm für die CI, Postgres-Initialisierung
doc/         Architektur, Domäne, Betrieb, Architekturentscheidungen
chat-context/  Verdichtete Protokolle der Planungsgespräche
```

Die Architekturentscheidungen mit ihren verworfenen Alternativen liegen in
[`doc/architektur/adr/`](doc/architektur/adr/).

## Mitarbeit

Die Projektsprache ist Deutsch — Dokumentation, Oberfläche, Commits und die fachlichen
Bezeichner im Code. Die verbindlichen Konventionen und die Anbindung an das
Projektgedächtnis stehen in [`CLAUDE.md`](CLAUDE.md).

Eine Sache vorab, weil sie leicht zu übersehen ist: **echte Kontodaten gehören nie ins
Repository.** IBANs und Kontonamen kommen ausschließlich aus Konfiguration, Tests laufen
gegen einen synthetischen Datensatz. Ein einmal committeter echter Kontostand bleibt auch
nach `git rm` in der Historie. Dasselbe gilt für Beträge, Mandatsreferenzen,
Gläubigerkennungen, Verwendungszwecke und Namen Dritter — auch in Dokumentation, Kommentaren
und Testdaten.

## Lizenz

Siehe [LICENSE](LICENSE).
