# Realm `haushaltsbuch` — rendern und importieren

`realm-haushaltsbuch.json.tmpl` ist eine **Vorlage mit Platzhaltern**, keine importierbare
Datei. Sie enthaelt bewusst keine Geheimnisse und keine Hostnamen.

Die gerenderte Fassung traegt aktive Client-Secrets. Sie gehoert in die Ignorierliste und
wird **nie** eingecheckt — siehe ADR-0009.

Nicht zu verwechseln mit `realm-haushaltsbuch-ci.json`. Jene Datei ist ausschliesslich fuer
die CI und fuer lokale Arbeit ohne externen Identity Provider; ihre Passwoerter sind
offensichtliche Testwerte und sollen es bleiben.

## 1. Werte erzeugen

```bash
export OIDC_CLIENT_SECRET="$(openssl rand -base64 32)"
export BFF_CLIENT_SECRET="$(openssl rand -base64 32)"
export BFF_BASIS_URL_LOKAL="http://localhost:3000"
export BFF_BASIS_URL_ZIEL="https://<zieladresse-des-dashboards>"
```

Beide Secrets wandern anschliessend unveraendert in die `.env` der Anwendung, nach
`OIDC_CLIENT_SECRET` und `BFF_CLIENT_SECRET`. Sie existieren an genau zwei Stellen: im Realm
und in der `.env`.

## 2. Rendern

```bash
mkdir -p realms-rendered
envsubst '${OIDC_CLIENT_SECRET} ${BFF_CLIENT_SECRET} ${BFF_BASIS_URL_LOKAL} ${BFF_BASIS_URL_ZIEL}' \
  < realm-haushaltsbuch.json.tmpl > realms-rendered/realm-haushaltsbuch.json
```

**Die Variablenliste ist nicht optional.** `envsubst` ohne Liste ersetzt *jeden* Ausdruck der
Form `${...}` — auch die, die Keycloak selbst zur Laufzeit ausfuellt. Die Vorlage enthaelt
davon einen: `resource_access.${client_id}.roles` im Rollen-Mapper. Ohne Liste wird daraus
`resource_access..roles`, und Client-Rollen landen unter einem kaputten Anspruchsnamen — ein
Fehler, der erst auffaellt, wenn jemand Rollen auswertet.

Auf macOS liefert Homebrew `envsubst` im Paket `gettext`. Ohne `envsubst` tut es auch:

```bash
python3 -c '
import os, re, sys
eigene = ("OIDC_CLIENT_SECRET", "BFF_CLIENT_SECRET", "BFF_BASIS_URL_LOKAL", "BFF_BASIS_URL_ZIEL")
text = sys.stdin.read()
for name in eigene:
    text = text.replace("${" + name + "}", os.environ[name])
sys.stdout.write(text)
' < realm-haushaltsbuch.json.tmpl > realms-rendered/realm-haushaltsbuch.json
```

Danach pruefen, dass kein *eigener* Platzhalter uebrig geblieben ist — die Grossschreibung
unterscheidet sie von denen, die Keycloak gehoeren:

```bash
grep -n '\${[A-Z_]*}' realms-rendered/realm-haushaltsbuch.json && echo "NICHT VOLLSTAENDIG GERENDERT"
```

Und dass es gueltiges JSON ist:

```bash
python3 -m json.tool realms-rendered/realm-haushaltsbuch.json > /dev/null && echo "JSON ok"
```

## 3. Importieren

Ueber die Administrationsoberflaeche: *Realms* -> *Create realm* -> *Browse* -> die
gerenderte Datei waehlen.

Oder beim Start des Containers, wenn das Verzeichnis eingehaengt ist:

```
/opt/keycloak/bin/kc.sh start --import-realm
```

Der Import legt Realm, beide Clients, die Audience-Scope und die zwei Realm-Rollen an.

## 4. Was der Import bewusst nicht anlegt

**Keinen Benutzer.** Ein Benutzer mit Passwort in einer Vorlage waere ein Geheimnis an der
falschen Stelle. Nach dem Import von Hand anlegen, Passwort setzen, Haken bei *Temporary*
entfernen, und die Rollen `benutzer` und `verwalter` zuweisen.

**Keine Rechte auf die Keycloak-Admin-API.** Der Service Account des Backends ist aktiviert,
bekommt aber keine `realm-management`-Rollen. Solange die Anwendung keine Benutzer einlaedt
oder verwaltet, braucht sie diese Rechte nicht, und ungenutzte Rechte sind schlicht Angriffs-
flaeche. Wenn die Verwaltung spaeter dazukommt, werden sie gezielt und einzeln vergeben.

**Keinen statischen MCP-Client.** Nach ADR-0009 kommt die Client-Identitaet aus dem
Metadatendokument des Anbieters oder aus dynamischer Registrierung. Ein handgepflegter
MCP-Client waere genau das Muster, das jene ADR ersetzt.

**Keine Instanz-Einstellungen.** Der oeffentliche Hostname der Keycloak-Instanz und das
Vertrauen in Proxy-Header sind Eigenschaften der Instanz, nicht des Realms, und werden ueber
deren Umgebung gesetzt.

## 5. Nach dem Import pruefen

```bash
curl -s https://<auth-adresse>/realms/haushaltsbuch/.well-known/openid-configuration \
  | python3 -m json.tool | head -5
```

Der Wert von `issuer` muss **exakt** dem entsprechen, was in `OIDC_AUTH_SERVER_URL` steht.
Weicht er ab — etwa weil die Instanz ihre interne Adresse zurueckgibt —, passt der
`iss`-Anspruch im Token nicht zur Konfiguration. Der Fehler zeigt dann auf das Token statt
auf die Adresse und ist der teuerste Irrweg in dieser Ecke.

## 6. Zwei Stellen, die im Zweifel nachzujustieren sind

**Die Standard-Scopes des BFF.** Die Vorlage listet `acr`, `basic`, `email`, `profile`,
`roles` und `web-origins` — die eingebauten Scopes von Keycloak — plus
`haushaltsbuch-mcp-audience`. Letztere sorgt dafuer, dass Token des BFF die Audience des
Backends tragen und vom Backend akzeptiert werden. Sollte eine der eingebauten Scopes in der
eingesetzten Version anders heissen oder fehlen, meldet der Import das eindeutig; dann diesen
Eintrag streichen, den Rest belassen.

**Rollen im Token.** Die Vorlage setzt `fullScopeAllowed` nicht und uebernimmt damit die
Vorgabe. Sollten die Realm-Rollen wider Erwarten nicht im Token auftauchen, ist das der erste
Ort zum Nachsehen — nicht der Rollen-Mapper.
