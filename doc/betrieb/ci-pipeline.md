# CI-Pipeline

## Auslöser

Jeder Pull Request nach `main` sowie Pushes auf `main`.

Läuft ein zweiter Push auf denselben Branch, wird der vorherige Lauf abgebrochen
(`concurrency` mit `cancel-in-progress`). Ein Lauf gegen einen überholten Commit hilft
niemandem und belegt nur einen Runner.

## Aufbau

```
   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
   │   backend    │  │   frontend   │  │    stapel    │   parallel
   │ Build, Test  │  │ Lint, Types, │  │ Compose mit  │
   │ Spotless     │  │ Test, Build  │  │ Keycloak     │
   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
          └────────┬────────┘                 │
                   ▼                          │
            ┌──────────────┐                  │
            │    images    │ Multi-Stage      │
            │  nach GHCR   │                  │
            └──────┬───────┘                  │
                   └───────────┬──────────────┘
                               ▼
                        ┌──────────────┐
                        │     gate     │ ein einziger Required Check
                        └──────────────┘
```

### Der `gate`-Job

Alle vorgelagerten Jobs laufen darauf zusammen. Als Required Check in den
Branch-Protection-Regeln wird **nur `gate`** eingetragen.

Der Grund ist praktisch: würde man jeden einzelnen Job als Required Check führen, müsste
die Branch-Protection jedes Mal angefasst werden, wenn ein Job dazukommt oder umbenannt
wird. Vergisst man es, ist der neue Job zwar rot, blockiert den Merge aber nicht.

`gate` prüft die Ergebnisse ausdrücklich auf `success` — nicht mit `if: failure()`. Ein
übersprungener Job gilt sonst als bestanden.

## Jobs im Einzelnen

### `backend`

Temurin 21, Maven-Cache über `setup-java`. Führt aus:

```bash
./mvnw -B verify
```

Damit laufen Kompilierung, Spotless-Formatprüfung, Unit-Tests und die Integrationstests
inklusive RLS-Test. Die Tests starten Postgres über Quarkus Dev Services — Docker steht
auf den GitHub-Runnern zur Verfügung.

Testberichte werden als Artefakt hochgeladen, auch bei rotem Lauf (`if: always()`). Sonst
steht man mit einem Fehlschlag ohne Bericht da, was den roten Lauf doppelt ärgerlich macht.

### `frontend`

Node 24 mit npm-Cache. Führt aus:

```bash
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
```

Die Reihenfolge ist bewusst: der Build ist der teuerste Schritt und steht am Ende. Ein
Typfehler soll nach zwanzig Sekunden auffallen, nicht nach zwei Minuten.

### `stapel`

Fährt `docker-compose.ci.yml` hoch — Postgres, Keycloak, Backend, Frontend —, lädt den
Demo-Datensatz und prüft dann zwei Dinge, die sonst niemand prüft:

1. **`GET /api/konten` ohne Token muss 401 liefern.** Käme hier eine 200, wäre die
   Anmeldepflicht nicht aktiv. Das ist der schwerwiegendste denkbare
   Konfigurationsfehler, und er fällt in keinem Unit-Test auf, weil dort OIDC
   abgeschaltet ist.
2. **Mit gültigem Token sieht Demo Eins genau vier Konten** — und `Giro Demo Zwei`
   gehört nicht dazu. Das ist die harte Anforderung aus HB-05, geprüft über den ganzen
   Stapel statt nur gegen die Datenbank.

Bei Fehlschlag werden Containerzustand, **Health-Historie** und Protokolle ausgegeben.
Ohne sie ist ein roter Lauf hier kaum zu deuten: `container X is unhealthy` nennt weder
den geprüften Befehl noch dessen Ausgabe — die steht in `docker inspect`.

### Warum die Demodaten ein eigener Schritt sind

Naheliegend wäre gewesen, sie über `QUARKUS_FLYWAY_LOCATIONS` im Compose einzuschalten.
Das funktioniert **nicht**: `quarkus.flyway.locations` ist eine Build-Zeit-Property. Eine
Umgebungsvariable ändert sie nicht — Quarkus meldet beim Start lediglich

```
quarkus.flyway.locations is set to 'db/migration,db/demodaten'
but it is build time fixed to 'db/migration'.
```

und macht weiter. Der Demo-Datensatz bliebe stillschweigend aus, die Zuordnung der
Keycloak-Kennungen auf die fachlichen Benutzer fehlte, und jede Anmeldung sähe null
Konten — fail-closed, aber aus dem falschen Grund.

Deshalb lädt ein eigener Compose-Dienst sie per `psql`:

```bash
docker compose -f docker-compose.ci.yml run --rm demodaten
```

Das hat einen zweiten Vorteil: das Produktionsimage kennt keine Demodaten und kann sie
nicht versehentlich in eine echte Umgebung tragen.

### `images`

Baut Backend- und Frontend-Image mit Buildx. Läuft nur, wenn `backend` und `frontend`
grün sind.

### Warum ohne die Docker-Actions

Der Job ruft `docker buildx build`, `docker login` und `docker buildx create` **direkt**
auf, statt `docker/build-push-action` und Geschwister zu verwenden.

Der Grund ist Erfahrung aus diesem Repo: die Auslieferung dieser Actions über
`codeload.github.com` antwortete wiederholt mit HTTP 429 und liess den Job umfallen,
*bevor überhaupt ein Byte gebaut wurde*. `max-parallel: 1` half nicht — es traf dann eben
den anderen Matrix-Job.

Docker und Buildx sind auf den Runnern vorinstalliert. Die paar Zeilen Shell hängen von
nichts ab, das erst heruntergeladen werden muss.

Der Cache liegt deshalb in der **Registry** (`ghcr.io/…/<image>:cache`) statt in
`type=gha`: letzterer braucht Umgebungsvariablen, die sonst `setup-buildx-action` setzt.
Registry-Cache kommt mit dem Zugang aus, den der Push ohnehin benötigt.

**Tagging:**

| Anlass | Tag |
|---|---|
| Pull Request | `pr-<nummer>` und `pr-<nummer>-<sha>` |
| Push auf `main` | `main` und `main-<sha>` |

Der SHA-Tag existiert, weil ein reiner `pr-42`-Tag mit jedem Push überschrieben wird. Will
man später nachvollziehen, welches Image zu einem bestimmten Stand gehörte, braucht man
den unveränderlichen Tag.

Eine Eigenheit dabei: bei `pull_request`-Ereignissen zeigt `GITHUB_SHA` auf den
**Merge-Commit**, nicht auf den Branch-Head. Der Tag benennt also den Stand, der
tatsächlich geprüft wurde — er lässt sich aber nicht direkt in der Branch-Historie
wiederfinden.

**Bei Pull Requests aus Forks wird nicht gepusht.** Ein Fork-PR hat keinen Schreibzugriff
auf die Registry — der Versuch würde den Lauf ohne Erkenntnisgewinn rot färben. Gebaut
wird trotzdem, damit der Build selbst geprüft ist.

## Registry

GitHub Container Registry, Namensraum `ghcr.io/<eigentuemer>/mcp-haushaltsbuch`:

| Image | Inhalt |
|---|---|
| `…/backend` | Quarkus im JVM-Modus, Temurin-21-JRE |
| `…/frontend` | Next.js Standalone auf Node 24 Alpine |

Beide tragen zusätzlich einen `cache`-Tag. Das ist kein lauffähiges Image, sondern der
Build-Cache — nicht wundern und nicht deployen.

Authentifizierung über `GITHUB_TOKEN` — kein zusätzliches Geheimnis nötig. Die Berechtigung
`packages: write` steht nur im `images`-Job, nicht am Workflow.

### Eine Falle im Backend-Dockerfile

Der Maven-Aufruf im Image-Build läuft mit einem BuildKit-Cache-Mount auf
`/root/.m2/repository` — und **ohne** vorgeschaltetes `dependency:go-offline`.

Das war einmal anders und hat sporadisch versagt: schlug der Vorab-Download teilweise
fehl, verschluckte ein `|| true` den Fehler und hinterliess negative Einträge im lokalen
Repository. Der eigentliche Build übernahm sie und brach ab mit

```
Non-resolvable import POM: ... quarkus-bom:pom:3.33.3.1 (absent)
```

`absent` ist Mavens Marker für einen zwischengespeicherten Fehlversuch, nicht für ein
fehlendes Artefakt — die Meldung führt in die Irre. Wer das Dockerfile wieder um einen
Vorab-Auflösungsschritt ergänzt, holt sich diesen Fehler zurück.

## Keycloak in der CI

Die CI bringt über `docker-compose.ci.yml` einen eigenen Keycloak mit statt gegen
`auth.jbaconsult.com` zu laufen.

Ein Pull Request, der rot wird, weil ein externer Identity-Provider gerade neu startet,
kostet mehr Vertrauen in die Pipeline, als die Realitätsnähe wert ist. Eine Pipeline, der
man nicht glaubt, wird ignoriert — und dann ist sie wertlos.

Der Realm-Import liegt in `infra/keycloak/realm-haushaltsbuch-ci.json`, mit Testbenutzern
und offensichtlichen Testpasswörtern. **Diese Datei ist ausschließlich für die CI** und
darf nie Grundlage einer echten Umgebung werden.

## Was die Pipeline noch nicht tut

Bewusst weggelassen, damit das erste Scaffolding nicht unter seinem eigenen Gewicht
zusammenbricht:

- **SBOM und Signierung** (Syft, Trivy, cosign) — sinnvoll fürs Open-Source-Zielbild,
  gehört aber in einen eigenen Schritt, wenn die Images stabil sind.
- **Native-Image-Build** — mehrere Minuten pro PR für einen Nutzen, den zwei Nutzer nicht
  spüren.
- **Deployment** — es gibt noch keine Zielumgebung.
- **Abhängigkeitsprüfung** (Dependabot, OWASP) — sinnvoll, sobald der Abhängigkeitsbaum
  steht.

## Lokal dasselbe prüfen

```bash
make pruefen
```

Führt dieselben Schritte aus wie `backend` und `frontend`. Wer das vor dem Commit laufen
lässt, spart sich den Umweg über einen roten Pull Request.
