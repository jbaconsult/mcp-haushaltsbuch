# 2026-08-17 — Scaffolding: Stack, Struktur, Zugriffskontrolle, CI

## Ausgangslage

Das Repository bestand aus `README.md` und `LICENSE`. Phase 1 (Analyse) ist abgeschlossen,
Phase 2 (Konsolidierung der Zahlungsströme) läuft mit Ziel 01.01.2027. Phase 3 —
Analyse-Infrastruktur mit Ledger, Berechnungslogik und MCP-Oberfläche — war laut
`status.projektphase` noch nicht begonnen und brauchte eine eigene Planungssession.

Der Kumbuka-Scope `haushaltsbuch` enthielt zu diesem Zeitpunkt 14 Memories: fünf
ratifizierte Entscheidungen HB-01 bis HB-05, sieben bindende Constraints, ein
Glossareintrag, ein Statuseintrag.

Auftrag: erstes Scaffolding. Projektstruktur, CLAUDE.md mit Kumbuka-Anbindung, Frontend,
Backend, docker-compose für ein schnell startbares Setup, GitHub-CI mit
Qualitätspipeline und Artefaktbau pro Pull Request, Ordner `doc` und `chat-context`.
Projektsprache Deutsch.

## Verlauf

### Was die vorhandenen Entscheidungen bereits festlegten

Der Scope schränkte die Wahlfreiheit stärker ein als zunächst erkennbar. Drei Punkte waren
faktisch schon entschieden:

**Die MCP-Oberfläche ist kein Adapter** (HB-05). Sie wird zuerst entworfen, nicht am Ende
angeschraubt. Das schließt aus, MCP als dünne Hülle über eine fertige REST-API zu legen.

**Zeilenbasierte Zugriffskontrolle auf Kontoebene ist die harte Anforderung** — genau
daran waren Firefly III und Actual Budget gescheitert. Eine Lösung, die diese Kontrolle
nur in der Anwendungsschicht führt, wiederholt denselben Fehler eine Ebene höher.

**Die Kennzahl `verfuegbar` wird deterministisch berechnet**, nie geschätzt. Das stellt
Anforderungen an die Zahlarithmetik, die eine Sprachwahl mitbestimmen.

### Backend

Vorgeschlagen war Python mit FastAPI, weil die MT940- und CAMT.052-Parser aus Phase 1
vermutlich dort liegen und das MCP-SDK ausgereift ist.

Entschieden wurde **Quarkus**. Die Begründung liegt in der Domäne: Sphärentrennung,
Topfarten und Berechnungsinvarianten profitieren stark von Typsicherheit — ein
verwechseltes `KontoId` und `TopfId` soll beim Kompilieren auffallen, nicht beim
Jahresabschluss. `BigDecimal` als eingebauter Typ ist bei Geldbeträgen ein echter Vorteil.

Der Preis ist die Portierung der Parser. Sie ist Aufwand, aber kein Risiko, weil
„Anfangssaldo plus Buchungen gleich Endsaldo" eine maschinell prüfbare Invariante ist.

Konkretisierung: Java 21, Maven, JVM-Modus. Native-Image verworfen für den Anfang — mehrere
Minuten CI-Zeit pro Pull Request für einen Nutzen, den zwei Nutzer nicht spüren.

**Versionsbefund:** Quarkus 3.33 ist die aktuelle LTS-Linie (3.38 ist neuester Stand, aber
nicht LTS). Die MCP-Extension baut gegen genau diese Linie — 1.13.1 gegen 3.33.2, die
2.0.0.CR2 gegen 3.33.3.1. Gewählt wurde die stabile 1.13.1 mit SSE-Transport. Die
2.x-Linie mit Streamable HTTP ist der zukunftsfähige Transport, steht aber noch bei
Release Candidate; ein RC ist die falsche Grundlage für ein Fundament. Der Wechsel ist
später eine Zeile im POM, weil die `@Tool`-Klassen unverändert bleiben.

### Frontend

Entschieden: **Next.js 16 mit App Router, React 19, Tailwind 4, plus BFF.**

Der BFF ist der eigentliche Punkt. Er hält die OIDC-Sitzung serverseitig in einem
`httpOnly`-Cookie; der Browser bekommt kein Token zu sehen. Ein Token im `localStorage`
ist über jedes eingebettete Skript lesbar — bei einer Anwendung, die Kontostände führt,
der falsche Kompromiss.

### Zugriffskontrolle

Entschieden: **Postgres Row-Level-Security in der Datenbank**, nicht in der Anwendung.

Erwogen und verworfen wurde die Variante „RLS plus zusätzliche Filterung in der
Anwendung". Zwei Regelwerke laufen mit der Zeit auseinander; wenn sie sich widersprechen,
gewinnt das restriktivere, und man sucht lange nach der Ursache verschwundener Zeilen.
Ein Regelwerk an der richtigen Stelle schlägt zwei an mittelmäßigen.

Drei Umsetzungsdetails, die im Gespräch als Fallstricke benannt wurden:

1. **`FORCE ROW LEVEL SECURITY`, nicht nur `ENABLE`.** Ohne `FORCE` ist der
   Tabelleneigentümer von allen Policies ausgenommen. Läuft die Anwendung versehentlich
   unter der Eigentümerrolle, ist die Zugriffskontrolle wirkungslos — ohne Fehlermeldung.
2. **`SET LOCAL`, nicht `SET`.** Ein normales `SET` lässt den Benutzerkontext an der
   gepoolten Verbindung hängen und gibt ihn dem nächsten Benutzer mit. Der Fehler tritt im
   Test mit einer Verbindung nie auf und unter Last sofort.
3. **Fail-Closed.** Ist der Kontext nicht gesetzt, liefern die Policies nichts. Die
   umgekehrte Voreinstellung wäre ein Datenleck, das niemandem auffällt.

Damit die Regel nicht nur im Dokument steht, prüft `RlsPolicyVollstaendigkeitTest` die
Tabellen mit Kontobezug gegen `pg_policies`. Eine vergessene Policy wird ein roter Build.

### Identität

Produktion läuft gegen ein bestehendes dediziertes Keycloak unter `auth.kumbuka.ai`. Da
auf diesem Host nur ein Stack liegt, dient `auth.jbaconsult.com` der Entwicklung.

Für die CI wurde ein eigener Compose-Stack mit lokalem Keycloak beschlossen. Grund: ein
Pull Request, der rot wird, weil ein externer Identity-Provider gerade neu startet, kostet
mehr Vertrauen in die Pipeline, als die Realitätsnähe wert ist.

### Projektsprache

Deutsch — das Thema taugt nicht als internationales Projekt.

Für den Code wurde die Regel **Domäne Deutsch, Technik Englisch** gewählt. Fachbegriffe
werden nicht übersetzt: `Topf` statt `Pot`, `Ruecklage` statt `Reserve`, `Zahllast`,
`Sphaere`, `Mandatsreferenz`. Framework-Struktur bleibt englisch: `Repository`,
`Resource`, `Service`.

Der Grund ist nicht Geschmack. Diese Begriffe haben keine verlustfreie englische
Entsprechung — „Topf" ist im Sinne von HB-03 eine virtuelle Zweckbindung mit definierter
Mathematik, nicht ein Behälter. Eine Übersetzung öffnet die Tür für Fehldeutungen, die im
Ledger teuer werden.

Umlaute in Bezeichnern ausgeschrieben (`Ruecklage`), in Prosa korrekt (Rücklage).

### CI

Entschieden: **Qualitätsgate plus Multi-Stage-Images nach GHCR** mit PR-Tag.

SBOM, Signierung und Trivy-Scan wurden bewusst zurückgestellt — sinnvoll fürs
Open-Source-Zielbild, aber ein erstes Scaffolding sollte nicht unter seinem eigenen
Gewicht zusammenbrechen.

Ein einzelner `gate`-Job läuft als Required Check, damit die Branch-Protection nicht bei
jedem neuen Job angefasst werden muss.

## Ergebnis

| Entscheidung | Kumbuka-Schlüssel | ADR |
|---|---|---|
| Technologiewahl Quarkus/Next.js/Postgres | — | [ADR-0001](../doc/architektur/adr/ADR-0001-technologiewahl.md) |
| Zugriffskontrolle in der Datenbank | unter `decision.hb-05-mcp-first` | [ADR-0002](../doc/architektur/adr/ADR-0002-zugriffskontrolle-in-der-datenbank.md) |
| Projektsprache und Bezeichnerregel | `convention.projektsprache` | — |

Angelegt: `CLAUDE.md`, `backend/` als Maven-Multimodul mit `kern`, `persistenz`, `mcp`,
`api`, `app`; `frontend/` mit BFF; `docker-compose.yml` und `docker-compose.ci.yml`;
`.github/workflows/pull-request.yml`; `doc/` und `chat-context/`.

## Was die Pipeline gefunden hat

Der erste Pull Request brauchte sechs Läufe bis grün. Alle fünf Fehler waren echt und
hätten sonst später wehgetan; jede Schicht machte die nächste erst sichtbar, sobald sie
selbst funktionierte.

**1. Keycloak lehnt unbekannte Felder im Realm-Import ab.** Erläuterungen als
`_kommentar`-Felder brachen den Import mit `Unrecognized field ... not marked as
ignorable`, der Container beendete sich mit Exit-Code 1. JSON hat keine Kommentarsyntax,
und Keycloak bietet keinen Ersatz — die Erläuterungen stehen jetzt in
`infra/keycloak/README.md`.

**2. Ein Healthcheck darf nicht die Abhängigkeiten prüfen.** Der Frontend-Healthcheck rief
die Startseite auf, und die befragt das Backend. Damit galt das Frontend als krank, sobald
eine Abhängigkeit nicht antwortete — obwohl Next.js sauber lief. Ausfälle kaskadieren so,
und ein Neustart behebt nichts. Neu ist `/api/gesundheit`, der ausschliesslich aus dem
eigenen Prozess antwortet.

**3. `quarkus.flyway.locations` ist eine Build-Zeit-Property.** Der Versuch, den
Demo-Datensatz per Umgebungsvariable im Compose einzuschalten, wirkte nicht; Quarkus
meldete beim Start nur `build time fixed to 'db/migration'` und migrierte weiter. Die
Zuordnung der Keycloak-Kennungen fehlte, jede Anmeldung sah null Konten. Jetzt lädt ein
eigener Compose-Dienst die Daten per `psql` — mit dem Nebeneffekt, dass das
Produktionsimage keine Demodaten kennt.

**4. Der Principal-Name ist nicht der Subject.** Quarkus nimmt standardmässig `upn`,
ersatzweise `preferred_username`. Der Filter bekam `demo-eins` statt der Kennung und fand
nichts. `quarkus.oidc.token.principal-claim=sub` stellt das richtig — der Subject ist
ohnehin die richtige Wahl, weil er unveränderlich ist.

**5. `|| true` über einem Cache-Schritt ist eine Zeitbombe.** Das vorgeschaltete
`dependency:go-offline ... || true` im Backend-Dockerfile verschluckte einen
Teil-Fehlschlag und schrieb negative Einträge ins lokale Maven-Repository. Der eigentliche
Build übernahm sie und scheiterte an `quarkus-bom ... (absent)` — an einem Artefakt, das es
sehr wohl gibt. In Lauf 1 war derselbe Schritt grün, weil der Download vollständig
durchlief. Ersetzt durch einen BuildKit-Cache-Mount.

**Ausserdem, nicht unser Fehler:** die Auslieferung der `docker/*`-Actions über
`codeload.github.com` antwortete in drei von vier Läufen mit HTTP 429 und liess den Job
umfallen, bevor ein Byte gebaut war. `max-parallel: 1` half nicht. Die Image-Jobs rufen
jetzt die vorinstallierte Docker-CLI direkt auf und hängen von nichts ab, das erst
heruntergeladen werden muss.

Die Fehler 3 und 4 sind bemerkenswert, weil sie sich beide als „null Konten" zeigten. Das
ist kein Zufall, sondern die Fail-Closed-Voreinstellung bei der Arbeit: fehlt der
Benutzerkontext, kommt nichts zurück — kein Fehler, keine Teilmenge. Der Test hat den
Datenmangel sichtbar gemacht, statt ihn zu überdecken.

## Offen geblieben

**Der Ledger-Kern.** `status.projektphase` nennt ihn als offene Kernentscheidung für
Phase 3. Das Scaffolding legt ihn bewusst **nicht** fest — es schafft nur das Schema für
Benutzer, Konto und Kontozugriff, so viel wie die Zugriffskontrolle zum Nachweis braucht.
Buchungen, Töpfe und die Berechnung von `verfuegbar` sind eine eigene Entscheidung, keine
Nebenwirkung eines Scaffoldings.

**Realm-Namen und Client-IDs** in Keycloak. Im Repo stehen Platzhalter in `.env.example`;
die tatsächliche Realm-Konfiguration auf `auth.jbaconsult.com` und `auth.kumbuka.ai` ist
nicht abgeglichen.

**Die Parser aus Phase 1.** Sprache und Ablageort sind nicht geklärt. Sie müssen nach Java
portiert werden; die MT940-Fallen (Feld 61, MMTT ohne Jahr) sind im Glossar festgehalten,
damit die Portierung sie nicht erneut findet.

**Der Umgang mit Lexware Office und Paperless.** Phase 4, hier nicht berührt.
