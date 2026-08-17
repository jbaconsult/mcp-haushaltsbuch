# Zugriffskontrolle

## Anforderung

Julia soll sich anmelden und **nur ihre Sachen sehen**. Das ist keine Komfortfunktion,
sondern die Anforderung, an der die fertigen Kandidaten gescheitert sind: Firefly III hat
Multi-User ohne feingranulare Kontoberechtigung, Actual Budget ist ein geteiltes
Haushaltsbudget ohne Mandantentrennung.

Die Zugriffsregel liegt deshalb in der **Datenbank**, nicht in der Anwendung. Eine Regel in
der Anwendungsschicht gilt nur für die Abfragepfade, die daran gedacht haben. Eine Regel in
der Datenbank gilt für alle — auch für den Reporting-Job, den jemand in zwei Jahren
danebenstellt.

## Aufbau

### Rollentrennung

Zwei Datenbankrollen, und die Trennung ist nicht kosmetisch:

| Rolle | Zweck |
|---|---|
| `haushaltsbuch_eigentuemer` | Legt das Schema an, führt Flyway-Migrationen aus |
| `haushaltsbuch_app` | Rolle jeder fachlichen Abfrage. Weder Eigentümer noch `BYPASSRLS` |

Die Verbindung wird als Eigentümer aufgebaut, damit Flyway beim Start migrieren
kann. **Jede fachliche Transaktion wechselt dann per `SET LOCAL ROLE` in
`haushaltsbuch_app`** — siehe `RlsKontext`.

Das klingt umständlicher als „einfach mit der eingeschränkten Rolle verbinden",
löst aber ein konkretes Problem: im Dev Mode und in Tests baut Quarkus die
Verbindung über Dev Services als **Superuser** auf, und ein Superuser umgeht
Row-Level-Security immer — auch `FORCE` hilft dagegen nicht. Ohne den
Rollenwechsel wäre die Zugriffskontrolle ausgerechnet dort wirkungslos, wo man
sie beim Entwickeln bemerken würde.

Damit greifen zwei unabhängige Schutzschichten:

1. **Der Rollenwechsel pro Transaktion** — wirkt auch gegen einen Superuser.
2. **`FORCE ROW LEVEL SECURITY`** — wirkt auch dann, wenn der Wechsel ausbleibt,
   solange die Verbindung kein Superuser ist.

Für den produktiven Betrieb bleibt der sauberere Weg offen: Migration als
eigener Schritt vor dem Anwendungsstart, Anwendung verbindet dauerhaft als
`haushaltsbuch_app`. Dann entfällt die Eigentümerrolle zur Laufzeit ganz. Das
ist ein Betriebsschritt, kein Codeumbau.

### FORCE ROW LEVEL SECURITY

```sql
ALTER TABLE konto ENABLE ROW LEVEL SECURITY;
ALTER TABLE konto FORCE  ROW LEVEL SECURITY;
```

**Beide Zeilen sind nötig.** `ENABLE` allein schaltet Policies ein, aber der
Tabelleneigentümer ist davon ausgenommen — läuft die Anwendung versehentlich unter der
Eigentümerrolle, ist die Zugriffskontrolle stillschweigend wirkungslos. Nichts schlägt
fehl, keine Warnung erscheint, und Julia sieht alles.

`FORCE` schließt diese Lücke. Es ist der Unterschied zwischen einer Zugriffskontrolle und
dem Anschein einer Zugriffskontrolle.

### Nutzerkontext pro Transaktion

Der angemeldete Benutzer wird als Sitzungsvariable gesetzt:

```sql
SET LOCAL app.benutzer_id = '3f1c...';
```

`SET LOCAL` gilt bis zum Ende der Transaktion. Das ist wichtig, weil Verbindungen aus
einem Pool kommen: ein normales `SET` würde den Kontext an der Verbindung hängen lassen
und dem nächsten Benutzer mitgeben, der sie bekommt.

### Fail-Closed

```sql
CREATE POLICY konto_sichtbar ON konto
    FOR SELECT
    USING (id IN (SELECT kz.konto_id
                    FROM kontozugriff kz
                   WHERE kz.benutzer_id = aktueller_benutzer()));
```

`aktueller_benutzer()` liest `current_setting('app.benutzer_id', true)`. Ist die Variable
nicht gesetzt, liefert die Funktion `NULL`, und die Policy trifft auf **keine** Zeile zu.

Das ist die richtige Richtung: Wer vergisst, den Kontext zu setzen, sieht nichts. Der
Fehler wird sofort sichtbar und ist harmlos. Die umgekehrte Voreinstellung — bei fehlendem
Kontext alles zeigen — wäre ein Datenleck, das niemandem auffällt.

## Umsetzung im Backend

| Klasse | Modul | Aufgabe |
|---|---|---|
| `Benutzerkontext` | `kern` | Hält den Benutzer der laufenden Anfrage |
| `BenutzerkontextFilter` | `api` | Liest den Subject-Claim aus dem OIDC-Token |
| `McpBenutzerkontext` | `mcp` | Dasselbe für MCP-Werkzeugaufrufe |
| `RlsKontext` | `persistenz` | Setzt Rolle und Benutzerkennung in der Transaktion |

Warum die Eingangsschichten das getrennt tun: MCP-Aufrufe laufen nicht durch die
JAX-RS-Filterkette. Die Extension-Linie 2.x bringt mit `quarkus-mcp-server-oidc`
eine Lösung mit; bis dahin ruft jedes Tool `McpBenutzerkontext.anwenden()`
selbst auf. Bewusst sichtbar und nicht in einen Interceptor versteckt — ein
Zugriffskontext, der scheinbar von selbst entsteht, ist bei einem System mit
Kontodaten das Falsche.

Für Hintergrundverarbeitung — Importe, geplante Abgleiche — gilt dieselbe Regel:
kein Job läuft ohne ausdrücklich gesetzten Kontext. Solche Jobs gibt es noch
nicht; wenn sie kommen, ist das der Punkt, an dem darüber zu entscheiden ist.

## Regel für Migrationen

**Eine Migration, die eine Tabelle mit Kontobezug anlegt und keine Policy dazu, ist
unvollständig.** Sie wird nicht gemergt.

Das lässt sich prüfen, und die Prüfung läuft in der CI mit: der Test
`RlsPolicyVollstaendigkeitTest` vergleicht die Tabellen mit Kontobezug gegen
`pg_policies` und schlägt fehl, wenn eine Tabelle ungeschützt ist. Eine Konvention, die
nur in einem Dokument steht, wird irgendwann übersehen.

## Identität

Keycloak über OIDC. Der Issuer ist je Umgebung konfigurierbar:

| Umgebung | Issuer |
|---|---|
| Produktion | `https://auth.kumbuka.ai/realms/haushaltsbuch` |
| Entwicklung | `https://auth.jbaconsult.com/realms/haushaltsbuch` |
| CI | `http://keycloak:8080/realms/haushaltsbuch` aus `docker-compose.ci.yml` |

Die CI bringt ihren eigenen Keycloak mit, weil sie sonst von einem externen Dienst abhinge.
Ein Pull Request, der rot wird, weil ein Identity-Provider gerade neu startet, kostet mehr
Vertrauen in die Pipeline als er Realitätsnähe gewinnt.

Der Realm-Import liegt in `infra/keycloak/realm-haushaltsbuch-ci.json` — mit
Testbenutzern und offensichtlichen Testpasswörtern. Diese Datei ist ausschließlich für die
CI. Sie darf nie die Grundlage einer echten Umgebung werden.

## Tokens und der Browser

Das Dashboard bekommt kein Token zu sehen. Der BFF im Next.js App Router hält die Sitzung
in einem `httpOnly`-Cookie und hängt das Zugriffstoken serverseitig an die Anfrage ans
Backend.

Ein Token im `localStorage` ist über jedes eingebettete Skript lesbar. Bei einer Anwendung,
die Kontostände führt, ist das der falsche Kompromiss.
