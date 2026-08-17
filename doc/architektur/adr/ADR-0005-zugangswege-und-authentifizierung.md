# ADR-0005 — Zugangswege und Authentifizierung

| | |
|---|---|
| Status | Angenommen |
| Datum | 2026-08-17 |
| Kumbuka | `constraint.zugangswege-und-auth` |
| Verhältnis | Ergänzt ADR-0001. Vorbedingung für ADR-0006 |

## Kontext

Der namensgebende Anwendungsfall ist mobil: im Laden stehen, ein Foto machen, das System
fragen, ob die Anschaffung tragbar ist. Daraus folgt zwingend, dass der MCP-Server **remote
und aus dem Internet erreichbar** ist. Ein lokaler Server auf dem Arbeitsrechner leistet dort
nichts.

Damit ist Authentifizierung keine späte Ausbaustufe. MCP über HTTP autorisiert OAuth-basiert;
ein statisches Client-Secret ohne Benutzerbezug wäre ein Maschine-zu-Maschine-Fluss und
könnte nicht beantworten, **wer** gerade fragt — was für die Sichtbarkeitsstufen aus ADR-0006
aber gebraucht wird.

Dazu kommen zwei weitere Konsumenten: das Dashboard als mobile-first Web-Anwendung und ein
REST-Zugang für eine Hausautomatisierung, aus der ein eigenes Finanz-Dashboard entstehen
soll.

## Entscheidung

**Drei Konsumenten, aber nur zwei Adapter über einer gemeinsamen Domänenschicht.** Dashboard
und Hausautomatisierung sprechen beide REST; es sind die Module `mcp` und `api`, nicht drei
getrennte Berechtigungspfade. Beide liegen im selben Backend und rufen dieselben
Domain-Services.

**Die Anwendung bleibt reiner Resource Server beziehungsweise OIDC-Client und enthält keinen
Identity-Provider-Code.** Der Issuer ist Konfiguration; das Compose bringt für
Fremdinstallationen einen Keycloak als Profil mit. Der Vorteil ist nicht Flexibilität,
sondern dass Authentifizierung nie gewartet werden muss.

**Kein Payment Initiation.** Die Bankanbindung liest Kontoinformationen und löst keine
Zahlungen aus. Überweisungen und ein vollständiges Banking-Frontend sind ausdrücklich kein
Ziel. Die Anbindung selbst bleibt konfigurierbar; eine TAN-Eingabe ist erlaubt und wird nicht
wegoptimiert.

## Alternativen

### A1 — Kein Identity-Provider, statisches Client-Secret für den MCP-Zugang

Der naheliegende Sparansatz: das System ist nicht mandantenfähig, es gibt eine Handvoll
Nutzer, wozu ein Provider?

Verworfen, weil ein Client-Secret ohne Benutzerbezug die Frage „wer fragt" nicht beantwortet.
Sobald es Sichtbarkeitsstufen gibt, muss der Aufrufer identifiziert sein. Die Alternative
wäre, die Identität in Anwendungscode zu verwalten — also einen Provider zu bauen, nur ohne
es so zu nennen.

### A2 — Einen minimalen Authorization Server selbst betreiben

Zwei konkrete Kandidaten wurden geprüft und beide verworfen.

`authlete/java-oauth-server` ist keine eigenständige Implementierung, sondern ein Frontend zu
einem kommerziellen Cloud-Dienst. Die als Vorteil beworbene Datenbankfreiheit bedeutet, dass
Autorisierungsdaten, Servereinstellungen und Client-Konfiguration beim Anbieter liegen und
der Betrieb API-Credentials von dort voraussetzt. Für ein selbst gehostetes Finanzsystem mit
Open-Source-Zielbild heißt das: jeder Anmeldevorgang jeder Installation läuft über einen
Dritten, bei dem sich jeder Installierende erst registrieren muss.

`AzIdP4J` ist ehrlicher und dadurch unbrauchbarer: Alpha-Stand, und die Liste dessen, was die
einbettende Anwendung selbst beisteuern muss, umfasst Webserver, Persistenz,
Client-Authentifizierung sowie Benutzerverwaltung und -authentifizierung. Das ist kein
Auth-Baustein, sondern ein Protokoll-Parser mit der Einladung, den Rest zu schreiben —
Anmeldeformular, Consent, Benutzertabelle, Passwort-Hashing, Token-Persistenz,
Schlüsselrotation.

### A3 — Keycloak, Anwendung als Resource Server (gewählt)

Der Einwand „Keycloak ist für diesen Zweck zu groß" richtet sich gegen das *Installieren*
eines Providers, nicht gegen das *Benutzen* eines bereits laufenden. Wo eine Instanz vorhanden
ist, sind die Grenzkosten ein Realm-Import.

### A4 — MCP nur lokal betreiben und die Authentifizierung ganz vermeiden

Formal die billigste Lösung. Verworfen, weil sie den Anwendungsfall entfernt, der die ganze
MCP-Ausrichtung begründet.

## Konsequenzen

- Die Berechtigung hängt an einer Identität aus dem Token, nicht an einem Netzwerkort. Das
  ist die Vorbedingung für ADR-0006.
- MCP-Aufrufe laufen nicht durch die JAX-RS-Filterkette. Der Benutzerkontext muss dort
  eigenständig gesetzt werden — bewusst sichtbar, nicht in einem Interceptor versteckt.
- Mit dem Verzicht auf Zahlungsauslösung entfällt eine ganze Regulierungsdimension: starke
  Kundenauthentifizierung je Zahlungsauftrag, Haftungsfragen, die Rolle eines
  Zahlungsauslösedienstes. Für ein Open-Source-Projekt ist das der Unterschied zwischen
  „liest Kontoumsätze" und einer Aufsichtsfrage.
- Der Preis: ein zusätzlicher Container in der Fremdinstallation. Vertretbar, weil er
  optional konfigurierbar bleibt und die Alternativen teurer sind.
