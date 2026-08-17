# Keycloak-Realm für die CI

`realm-haushaltsbuch-ci.json` wird von `docker-compose.ci.yml` beim Start importiert.

**Diese Datei ist ausschließlich für die CI und für lokale Arbeit ohne externen
Identity-Provider.** Die Passwörter sind offensichtliche Testwerte und sollen es bleiben.
Sie darf nie Grundlage einer echten Umgebung werden.

| Umgebung | Identity-Provider |
|---|---|
| Produktion | `auth.kumbuka.ai` |
| Entwicklung | `auth.jbaconsult.com` |
| CI | dieser Realm, lokal im Compose |

## Warum die Erläuterungen hier stehen und nicht in der Datei

Keycloak lehnt beim Realm-Import **jedes unbekannte Feld ab**. Ein Kommentarfeld wie
`_kommentar` bricht den Import mit `Unrecognized field ... not marked as ignorable` ab —
der Container beendet sich mit Exit-Code 1, und der ganze Stapel kommt nicht hoch.

JSON hat keine Kommentarsyntax, und Keycloak bietet keinen Ersatz. Deshalb diese Datei.

## Feste Benutzerkennungen

Die `id`-Felder der Benutzer sind fest vergeben, nicht zufällig:

| Benutzer | Kennung |
|---|---|
| `demo-eins` | `aaaaaaaa-0000-0000-0000-000000000001` |
| `demo-zwei` | `aaaaaaaa-0000-0000-0000-000000000002` |

Das OIDC-Subject **ist** diese Kennung. Nur weil sie vorab feststeht, lässt sich die
Zuordnung auf den fachlichen Benutzer in `V900__demodaten.sql` überhaupt schreiben — bei
zufällig erzeugten Kennungen wäre sie erst nach dem ersten Start bekannt.

Wer die Kennungen hier ändert, muss `V900__demodaten.sql` mitändern. Sonst kann sich
jemand zwar anmelden, sieht aber nichts: die Zugriffskontrolle findet keinen passenden
Benutzer und liefert fail-closed nichts.

## Clients

| Client | Zweck |
|---|---|
| `haushaltsbuch-backend` | Vertraulicher Client. Das Backend prüft damit Tokens |
| `haushaltsbuch-bff` | Vertraulicher Client für den Anmeldefluss des Dashboards |

Bei `haushaltsbuch-bff` ist **Direct Access Grants eingeschaltet**, damit die Pipeline
ohne Browser an ein Token kommt. In einer echten Umgebung gehört das aus — der
Passwort-Grant umgeht den Autorisierungscode-Flow samt PKCE.

## Anmeldung

```
demo-eins / ci-passwort
demo-zwei / ci-passwort
```

Keycloak-Administration unter http://localhost:8081 mit `admin` / `ci-passwort`.

## Änderungen wirken erst nach `down -v`

Der Import läuft nur, wenn Keycloak noch keine Daten hat. Nach einer Änderung an der
Realm-Datei:

```bash
docker compose -f docker-compose.ci.yml down -v
make hoch-ci
```
