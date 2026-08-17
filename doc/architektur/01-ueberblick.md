# Systemüberblick

## Leitgedanke

Das System beantwortet eine einzige Frage gut: **„geht das oder nicht?"** — beantwortet aus
der berechneten Kennzahl `verfuegbar`, nie aus einer Modellschätzung.

Daraus folgt die gesamte Bauordnung. Die MCP-Tooloberfläche ist die primäre Schnittstelle
und wird zuerst entworfen (HB-05). Das Dashboard ist die gemeinsame Sicht für beide
Ehepartner und damit sekundär, aber notwendig — nicht weglassbar.

## Bausteine

```
                     ┌──────────────────┐
   Gespräch ────────►│   MCP-Server     │──┐
                     │  (Modul mcp)     │  │
                     └──────────────────┘  │
                                           ▼
   Browser ─► ┌──────────┐   ┌──────────┐  ┌──────────────┐   ┌──────────────┐
              │ Next.js  │──►│   BFF    │─►│  REST (api)  │──►│    kern      │
              │ Dashboard│   │ Session  │  │              │   │  Domäne +    │
              └──────────┘   └──────────┘  └──────────────┘   │  Berechnung  │
                                                              └──────┬───────┘
                     ┌──────────────────┐                            │
                     │    Keycloak      │                            ▼
                     │  OIDC / Identität│                    ┌──────────────┐
                     └──────────────────┘                    │  persistenz  │
                                                             └──────┬───────┘
                                                                    ▼
                                                             ┌──────────────┐
                                                             │  PostgreSQL  │
                                                             │  + RLS       │
                                                             └──────────────┘
```

Beide Zugänge — Gespräch und Dashboard — laufen durch dieselbe Berechnungslogik in `kern`.
Es gibt keinen zweiten Rechenweg. Zwei Implementierungen derselben Kennzahl würden
irgendwann auseinanderlaufen, und der Fehler fiele erst beim Jahresabschluss auf.

## Warum der MCP-Server im Backend lebt

Er ist ein eigenes Maven-Modul, aber Teil desselben Deployments. Damit ruft er die
Domain-Services direkt auf, statt über die REST-API zu gehen.

Der Grund ist nicht Bequemlichkeit, sondern Genauigkeit: jede Netzwerkgrenze bedeutet
Serialisierung, und Serialisierung von Geldbeträgen ist eine Fehlerquelle. Ein `BigDecimal`
mit Skalierung 2, das durch JSON und zurück läuft, kann als `double` ankommen. Bei einer
Kennzahl, die über „geht das oder nicht" entscheidet, ist das nicht hinnehmbar.

Die Trennung als eigenes Modul bleibt trotzdem, weil HB-05 die Tooloberfläche zum
erstklassigen Design-Artefakt erklärt. Ein MCP-Tool enthält keine Fachlogik — es übersetzt
zwischen Gesprächsebene und `kern`.

## Rollenteilung mit den Nachbarsystemen

| System | Rolle |
|---|---|
| Haushaltsbuch | System of Record für die private Sphäre, Berechnung, Gesprächsschnittstelle |
| Lexware Office | System of Record für die freiberufliche Sphäre, GoBD-relevant |
| Paperless | Eingangskanal und privates Belegarchiv — **kein** GoBD-konformes Archiv |
| n8n | Ablaufsteuerung, Trigger, Idempotenz, Fehlerbehandlung |
| Kumbuka | Gedächtnis, Policies, gelernte Zuordnungsregeln |

Das Sprachmodell entscheidet **Einzelfälle** und berichtet **berechnete** Zahlen. Es
schätzt nicht, und es bucht nicht.

## Was das System bewusst nicht tut

- **Keine Steuerübermittlung.** Es erzeugt eine abgabereife Datenlage; der
  Übermittlungsakt bleibt bei Lexware Office beziehungsweise beim Steuerberater (HB-04).
  Eine eigene ELSTER/ERiC-Anbindung wäre ein Teilprojekt mit jährlichen Versionssprüngen
  und Zertifikatshandling — ohne Nutzengewinn und mit dem Risiko einer Doppelübermittlung.
- **Kein autonomes Buchen.** Ein Agent bucht nie eigenständig und bucht nie zwischen
  Töpfen um. Fehler dieser Klasse erzeugen plausible, gut formulierte, falsche Zahlen.
- **Keine Haushaltsdurchschnitte.** Ausgewertet wird gegen die eigene Historie und nach
  Entscheidungscharakter, nie nach gut/schlecht.
