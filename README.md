# Haushaltsbuch

Ein Haushaltsbuch, das mit der KI sprechen kann.

Die primäre Schnittstelle ist das Gespräch, nicht das Dashboard. Die Leitfrage im Alltag
lautet „geht das oder nicht?" — und sie wird aus einer berechneten Kennzahl beantwortet,
nicht aus einer Schätzung. Das Dashboard ist die gemeinsame Übersicht für beide
Ehepartner und damit sekundär, aber notwendig.

## Warum kein fertiges Werkzeug

Zwei Anforderungen schließen die naheliegenden Kandidaten aus:

1. **Zeilenbasierte Zugriffskontrolle auf Kontoebene.** Firefly III bringt Multi-User mit,
   aber keine feingranulare Kontoberechtigung. Actual Budget ist ein geteiltes
   Haushaltsbudget ohne Mandantentrennung.
2. **Die Gesprächsoberfläche ist das Produkt**, nicht ein Adapter, den man am Ende
   anschraubt.

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

Für die tägliche Arbeit ist der Quarkus Dev Mode angenehmer — er startet Postgres
über Dev Services selbst und lädt Änderungen ohne Neustart:

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

## Mitarbeit

Die Projektsprache ist Deutsch — Dokumentation, Oberfläche, Commits und die fachlichen
Bezeichner im Code. Die verbindlichen Konventionen und die Anbindung an das
Projektgedächtnis stehen in [`CLAUDE.md`](CLAUDE.md).

Eine Sache vorab, weil sie leicht zu übersehen ist: **echte Kontodaten gehören nie ins
Repository.** IBANs und Kontonamen kommen ausschließlich aus Konfiguration, Tests laufen
gegen einen synthetischen Datensatz. Ein einmal committeter echter Kontostand bleibt auch
nach `git rm` in der Historie.

## Lizenz

Siehe [LICENSE](LICENSE).
