# Anmeldung

Wie sich ein Mensch am Dashboard anmeldet, was dafür konfiguriert sein muss, und der eine
Handgriff, ohne den eine gelungene Anmeldung trotzdem nichts zeigt.

Grundlage: ADR-0005 (die Anwendung bleibt reiner Client), ADR-0009 (ein Realm),
`constraint.autorisierung-der-antwort` (die Anmeldung liefert die Identität, nicht die
Sichtbarkeit).

---

## 1. Der Weg

```
Browser                 BFF (Next.js)              Keycloak            Backend (Quarkus)
   |                         |                        |                      |
   |-- GET /anmeldung ------>|                        |                      |
   |                    state + PKCE erzeugen,        |                      |
   |                    versiegelt ins Cookie         |                      |
   |<-- 307 ----------------|                         |                      |
   |------------------ Anmeldeformular -------------->|                      |
   |<----------------- 302 mit code, state, iss ------|                      |
   |-- GET /anmeldung/rueckleitung ->|                |                      |
   |                    state einloesen (einmalig),   |                      |
   |                    iss pruefen,                  |                      |
   |                         |-- code + verifier ---->|                      |
   |                         |<-- Token --------------|                      |
   |                    Sitzung versiegeln            |                      |
   |<-- 307 auf das Ziel ----|                        |                      |
   |                         |                        |                      |
   |-- Seitenaufruf -------->|-- Bearer-Token ------------------------------->|
```

**Der Browser sieht nie ein Token.** Er hält ein `httpOnly`-Cookie mit verschlüsseltem
Inhalt; das Token hängt der BFF erst beim Weiterreichen an. Ein Token im `localStorage` wäre
über jedes eingebettete Skript lesbar — bei einer Anwendung, die Kontostände führt, der
falsche Kompromiss.

Dateien: `frontend/src/app/anmeldung/`, `frontend/src/app/abmeldung/`,
`frontend/src/lib/{oidc,sitzung,anmeldezustand,siegel}.ts`.

---

## 2. Der Handgriff: Anmeldung einem Benutzer zuordnen

**Das ist der Teil, den man übersieht, und der sich am schwersten diagnostizieren lässt.**

Wer sich am Realm anmelden kann, hat damit noch keinen Zugriff auf Konten. Der `sub`-Claim
des Tokens muss in `benutzeridentitaet` stehen. Fehlt der Eintrag, gelingt die Anmeldung, der
Benutzerkontext bleibt leer, und die Row-Level-Security liefert fail-closed null Zeilen — das
sieht aus wie ein Rechteproblem und ist keines.

Das ist Absicht: **keine Selbstregistrierung.** Die Zuordnung ist ein bewusster Akt.

Das Dashboard sagt es einem angemeldeten, nicht zugeordneten Menschen direkt und nennt die
Kennung, die einzutragen ist. Sie steht auch unter `GET /api/ich`.

### Eintragen

```bash
# 1. Kennung besorgen - sie steht im Dashboard oder hier:
curl -s http://localhost:3000/api/bff/ich

# 2. Eintragen. Der zweite Wert ist der fachliche Benutzer aus der Tabelle "benutzer".
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "INSERT INTO benutzeridentitaet (oidc_subjekt, benutzer_id)
   VALUES ('<sub-aus-schritt-1>', '<uuid-aus-benutzer>');"
```

Vorhandene Benutzer anzeigen:

```bash
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT b.id, b.anzeigename, i.oidc_subjekt
     FROM benutzer b LEFT JOIN benutzeridentitaet i ON i.benutzer_id = b.id;"
```

Ein Benutzer darf **mehrere** Identitäten haben. Das ist der Normalfall, sobald derselbe
Mensch gegen verschiedene Realms arbeitet — Entwicklung, Produktion, CI.

Eine Verwaltungsoberfläche gibt es dafür bewusst nicht. Bei zwei Nutzern wäre sie
verschwendete Arbeit.

---

## 3. Konfiguration

Alle Werte in `.env`, Vorlage in `.env.example`.

| Variable | Bedeutung |
|---|---|
| `OIDC_AUTH_SERVER_URL` | Realm-Adresse, wie **der Server** sie erreicht |
| `OIDC_BROWSER_URL` | Realm-Adresse, wie **der Browser** sie erreicht. Nur nötig, wenn sie abweicht |
| `BFF_CLIENT_ID` | `haushaltsbuch-bff` |
| `BFF_CLIENT_SECRET` | Aus dem Realm; existiert an genau zwei Stellen |
| `BFF_SESSION_SECRET` | Verschlüsselt das Sitzungscookie. `openssl rand -base64 32` |
| `BFF_BASIS_URL` | Adresse dieses BFF. Daraus entsteht die Rückleitungsadresse |

`OIDC_BROWSER_URL` betrifft nur den Container-Verbund: Dort heisst Keycloak `keycloak:8080`
im Netz und `localhost:8081` auf dem Rechner. Ohne diese Trennung leitet die Anmeldung den
Browser auf einen Namen, den nur Container auflösen können. Tokentausch, Auffrischung,
Abmeldung und der Aussteller-Vergleich laufen weiter über die interne Adresse.

Im **Entwicklungsprofil** ist nichts davon nötig: Dort arbeitet das Backend ohne OIDC mit
einem festen Demo-Benutzer, und das Dashboard sagt das auch. Diese Absperrung hängt am
LaunchMode und nicht an einer Konfigurationsvariablen — siehe `BenutzerkontextFilter`.

---

## 4. Gemessenes, nicht Angenommenes

Alle Werte gegen Keycloak 26.7 mit dem Realm aus `infra/keycloak/`.

### Der `iss`-Parameter wird ausgegeben

Offener Punkt 2 aus ADR-0009, hiermit beantwortet:

```
authorization_response_iss_parameter_supported: true
```

Und in der Antwort steht er auch: `iss=http://localhost:8081/realms/haushaltsbuch`. Er wird
deshalb geprüft. Weicht er ab, endet der Vorgang ohne Sitzung.

### Die Grösse des Sitzungscookies

| | Zugriffstoken | Auffrischung | versiegelt im Cookie | Anteil an 4096 |
|---|---|---|---|---|
| ohne Rollen im Token | 837 B | 657 B | 2182 B | 53 % |
| **mit zwei Realm-Rollen** | **1599 B** | **789 B** | **3439 B** | **84 %** |
| zusätzlich mit `id_token` | — | — | ~4600 B | über der Grenze |

**Das `id_token` bleibt deshalb draussen.** Es würde nur als `id_token_hint` beim Abmelden
gebraucht; stattdessen läuft die Abmeldung serverseitig über den Logout-Endpunkt mit dem
Auffrischungstoken. Nachgewiesen: Der Aufruf quittiert mit 204, und ein anschliessender
Auffrischungsversuch scheitert mit `Session not active`.

**84 Prozent sind kein komfortabler Abstand.** Jede weitere Realm-Rolle, jede Gruppe und
jeder eigene Claim wächst mit. Wird die Grenze überschritten, verwirft der Browser das Cookie
**kommentarlos** — kein Fehler, keine Meldung, der Mensch ist einfach nicht angemeldet.
`sitzungSetzen` warnt deshalb ab 3600 Byte ins Protokoll. Schlägt die Warnung an, ist eine
serverseitige Sitzungsablage fällig; das ist eine Entwurfsentscheidung mit eigenen Folgen und
keine Ausweichlösung.

---

## 5. Zwei Fallen im Realm, beide erlebt

### Die eingebauten Client-Scopes verschwinden

Enthält die Realm-Vorlage eine `clientScopes`-Liste, legt Keycloak **ausschliesslich** diese
an. `acr`, `basic`, `email`, `profile`, `roles` und `web-origins` entstehen dann gar nicht
erst — auch wenn der Client sie als Default-Scopes auflistet.

Die Folge ist heimtückisch: Der `sub`-Mapper sitzt im Scope `basic`. Ohne ihn kommt ein Token
**ohne `sub`** heraus. Die Anmeldung gelingt, das Token ist gültig, das Backend akzeptiert
es — und findet in `benutzeridentitaet` nichts, weil es nichts zu suchen gibt. Das sieht
exakt aus wie eine fehlende Zuordnung.

Die Vorlage führt die eingebauten Scopes deshalb mit auf. Zu prüfen nach jedem Import:

```bash
curl -s "$KC/admin/realms/haushaltsbuch/client-scopes" -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import json,sys; print(sorted(s['name'] for s in json.load(sys.stdin)))"
```

Erwartet werden mindestens `acr`, `basic`, `email`, `profile`, `roles`, `web-origins` und
`haushaltsbuch-mcp-audience`.

### Default-Scopes dürfen nicht angefragt werden

`profile` und `email` stehen im Realm als **Default**-Scopes des BFF-Clients. Keycloak gewährt
sie von sich aus. Sie zusätzlich im `scope`-Parameter anzufragen ist kein Mehr an Rechten,
sondern ein Fehler: Ein Scope, der weder default noch optional ist, wird mit `invalid_scope`
abgelehnt, und der Vorgang bricht ab, bevor ein Anmeldeformular erscheint.

Die Anmeldung fragt deshalb nur `openid` an.

---

## 6. Was geprüft ist

| | |
|---|---|
| Anmelden und Konten sehen | Durchlauf gegen echten Keycloak |
| Unangemeldet: REST und MCP | 401, Health bleibt offen |
| Angemeldet, nicht zugeordnet | Hinweis mit Kennung statt leerer Liste |
| Manipuliertes Cookie | wie keine Sitzung — `siegel.test.ts`, `sitzung.test.ts` |
| `state` unbekannt, verbraucht, abgelaufen | abgelehnt, keine Sitzung — `anmeldezustand.test.ts` |
| Auffrischung vor Ablauf | mit verkürzter Lebensdauer, nicht durch Warten — `sitzung.test.ts` |
| Abmelden | lokal **und** beim Identity Provider |
| Token im Browser | in keinem Cookie, keiner Antwort, keinem Speicher |

### Rote Probe

**Greift die `state`-Prüfung?** Mit ausgebauter Prüfung und einem frei erfundenen `state`
entsteht eine Sitzung. Mit eingebauter Prüfung endet derselbe Aufruf auf
`/anmeldung/fehler?grund=zustand`, ohne Sitzung und ohne Nebenwirkung.

**Hält die Absperrung im `BenutzerkontextFilter`?** Backend im Profil `prod`, Entwicklungs-
Benutzerproperty gesetzt, HTTP-Auth-Regel entfernt, damit kein 401 das Ergebnis verdeckt:
`/api/konten` liefert **null** Konten, `/api/ich` meldet „nicht angemeldet". Die Property
wirkt dort nicht.
