# Rückgabe — Sub-Sprint 2: Durchstich Bankzugang

| | |
|---|---|
| Datum | 2026-08-18 |
| Apparat | Code |
| Auftrag | Dispatch „Sub-Sprint 2: Durchstich Bankzugang", 2026-08-18 |
| Zustand | ausgeführt, Pull Request offen |
| Ablage | PR [#9](https://github.com/jbaconsult/mcp-haushaltsbuch/pull/9), Branch `feat/durchstich-bankzugang` |

## Ergebnis in einem Satz

Der Pfad steht durch alle Schichten — Port in `kern`, Adapter im neuen Modul `bankzugang`,
Schema in `V3`, Endpunkte in `api`, zwei Werkzeuge in `mcp`, drei Seiten im Frontend —, und
die eine Messung, die mehr entscheidet als der Rest, ist gebaut, aber noch nicht gelaufen.

---

## Akzeptanzkriterien

| Nr. | Kriterium | Stand | Nachweis |
|---|---|---|---|
| 1 | Autorisierung → Konten mit Salden in der Datenbank | gebaut, End-to-End gegen die Sandbox steht aus | `BankzugangResourceTest#rueckleitungUebernimmtKonten` über den ganzen Stapel |
| 2 | Unangemeldet liefern Dashboard, REST und MCP nichts | erfüllt | `%prod`-Auth-Policy auf `/api/*` und `/mcp/*`; RLS fail-closed |
| 3 | MCP antwortet wie das Dashboard | erfüllt | derselbe `BankzugangService`, kein zweiter Rechenweg |
| 4 | Kein `uid` als dauerhafter Schlüssel | erfüllt | `BankzugangPersistenzTest#zweiteAutorisierungErzeugtKeinenZweitenDatensatz`; `V3` hat keine Spalte dafür |
| 5 | `state` unbekannt, verbraucht, fremd → abgelehnt | erfüllt | `#zustandIstEinmalig`, `#zustandWirdGeprueft`, `BankzugangServiceTest#fremderZustandWirdAbgelehnt` |
| 6 | `error` statt `code` → sichtbarer Fehlzustand | erfüllt | `#abbruchUebernimmtMeldung`, `BankzugangResourceTest#fehlerFuehrtZuFehlzustand` |
| 7 | Abgelaufener Zugang → sichtbarer Status | erfüllt | `#abgelaufenerZugangBleibtSichtbar` |
| 8 | RLS vollständig, ohne Kontext null Zeilen | erfüllt | `RlsPolicyVollstaendigkeitTest`, `#ohneKontextNichtsSichtbar` |
| 9 | Feldabdeckungsbericht liegt vor | **nicht erfüllt** | siehe unten |
| 10 | `make pruefen` grün, Compose aus frischem Klon | erfüllt | 131 Tests; das Abbild baute zunächst nicht, siehe Befunde |

### Zu Kriterium 9

Die Messung ist vollständig gebaut — `BankanbieterPort#feldabdeckungMessen`, im Adapter
implementiert, über `POST /api/bankzugaenge/konten/{id}/feldabdeckung` erreichbar, im Test
abgedeckt. Sie braucht Anwendungs-ID und privaten Schlüssel; beides liegt nicht im
Repositorium und stand hier nicht zur Verfügung. **Der Bericht entsteht beim ersten
Sandbox-Lauf.**

Was sich ohne Lauf sagen lässt, steht unter „Befunde".

---

## Rote Probe

Nicht durchgeführt: sie setzt einen autorisierten Sandbox-Zugang voraus, dessen Sitzung sich
anbieterseitig löschen lässt.

Der Pfad ist gebaut und im Domänentest abgedeckt
(`BankzugangServiceTest#erlosceneSitzungLaesstDatenStehen`): erlischt die Sitzung, wechselt
der Zugang nach `FEHLGESCHLAGEN` mit der Meldung des Anbieters, und Konten wie Salden bleiben
unverändert stehen. `BankzugangPersistenzTest#saldenUeberlebenDenFehlschlag` prüft dasselbe
gegen die Datenbank.

Die Entwurfsentscheidung dahinter: Salden sind **Historie, kein Überschreiben**. Jeder Abruf
legt eine Zeile mit Abrufzeitpunkt an. Ein Saldo wird durch einen Fehlschlag nicht falsch, er
wird alt — und das ist ablesbar statt verloren.

---

## Abweichungen vom Auftrag

### Die Werkzeuge heißen anders

Der Auftrag nennt sie `konten_auflisten` und `konto_details`. Der erste Name ist seit
Sub-Sprint 1 vergeben — dort für die fachlichen Konten aus `V1`, die etwas anderes sind als
die von einer Bank gemeldeten. Zwei Werkzeuge desselben Namens kann es nicht geben, und ein
Modell wählt sie nach dem Namen aus.

Sie heißen deshalb **`bankkonten_auflisten`** und **`bankkonto_details`**. Die Begründung
steht im Javadoc der Klasse, damit sie beim nächsten Lesen nicht als Nachlässigkeit erscheint.

Falls Concept den ursprünglichen Zuschnitt bevorzugt, wäre die Alternative, das bestehende
Werkzeug umzubenennen — das berührt dann aber Sub-Sprint 1 und dessen Tests.

### Das Dockerfile wurde angefasst

Das Regime nennt es nicht. Ohne die Ergänzung baut das Abbild nicht: die Modulliste dort ist
eine zweite, handgepflegte Kopie der `<modules>` aus `backend/pom.xml`. Das ist keine
Ausweitung des Auftrags, sondern seine Voraussetzung — Kriterium 10 verlangt ein Compose, das
aus einem frischen Klon startet.

---

## Stoppbedingung und Rückfragepunkt

**Keiner von beiden ausgelöst.**

Der Rückfragepunkt betraf die Anmeldung für MCP-Aufrufe. Sie funktioniert über den
vorgesehenen Weg: `McpBenutzerkontext` setzt den Benutzer sichtbar zu Beginn jedes Werkzeugs,
außerhalb der JAX-RS-Filterkette. Ein unauthentifizierter MCP-Endpunkt entstand zu keinem
Zeitpunkt.

Die Stoppbedingung betraf fehlende Salden beim Sandbox-Institut. Sie ist noch nicht prüfbar,
weil kein Lauf gegen die Sandbox stattgefunden hat.

---

## Befunde außerhalb des Auftrags

### Der PSD2-Weg führt die beiden entscheidenden Felder nicht

Die Anbieterdokumentation ist eindeutig: Das Transaktionsmodell kennt `entry_reference`,
`booking_date`, `value_date`, `transaction_amount`, `creditor`/`debtor` samt Konto,
`remittance_information`, `bank_transaction_code`, `merchant_category_code` und
`balance_after_transaction` — aber **weder ein Feld für die Mandatsreferenz noch eines für die
Gläubigerkennung**.

Damit bestätigt sich die Erwartung aus dem Frame auf der Modellebene. Bestätigt sie sich auch
an Daten, ist der PSD2-Weg auf der Feldebene **schlechter als FinTS**: jener liefert
Mandatsreferenzen, nur die Gläubigerkennung fehlt dort.

Die Messung sucht deshalb zusätzlich im **Rohtext** nach beiden — in allen Schreibweisen, die
üblicherweise vorkommen. Nur so lässt sich klären, ob sie doch irgendwo auftauchen, etwa im
Verwendungszweck.

**Konsequenz, falls sich der Befund bestätigt:** `constraint.klassifikation-iban-mref` ist
über beide Kanäle nur teilweise erfüllbar, und die Acquirer-Regel aus
`constraint.dauermandat-vs-pos` ist über keinen von beiden anwendbar, weil sie über
Gläubiger-IDs gruppiert. Eine Ersatzbildung über die Gegenpartei-IBAN ist denkbar, aber
fachlich nicht dasselbe — und sie gehört entschieden, nicht unterstellt. E2 wird damit zu
einer Abwägung zwischen Bequemlichkeit und Datenqualität.

### Drei Fehler, die erst spät aufgefallen wären

**Der Persist-Zeitpunkt.** `kontoUebernehmen` rief `persist()` auf einer Entität, deren
Pflichtfelder noch nicht standen. Der Fehler tritt erst beim nächsten Flush auf, und seine
Meldung weist nicht auf die Reihenfolge hin.

**Die harte CDI-Abhängigkeit.** `BankzugangService` verlangte einen `BankanbieterPort`. Das
ließ das gesamte Deployment der **Persistenztests** scheitern — mit einem Fehler, der auf
`RoteProbeTest` zeigte, also auf einen Test, der mit Bankzugängen nichts zu tun hat. Der
Anbieter wird jetzt als `Instance` gehalten: fehlt er, läuft alles andere weiter und sagt beim
Aufruf, was fehlt.

**Der Content-Type auf rumpflosen Aufrufen.** Die beiden POST-Endpunkte ohne Rumpf erbten das
`@Consumes` der Klasse und antworteten mit 415 auf einen Aufruf, der gar nichts sendet. Das
hätte beim ersten Klick im Browser zugeschlagen.

### Zwei Dinge, die die CI lehrte

Die Modulliste im Dockerfile ist eine zweite Wahrheit neben `backend/pom.xml` und wird beim
lokalen Bauen nie geprüft.

Sonar beanstandete zwölf Null-Prüfungen auf `Optional`-Parametern — und lag richtig. Ein
`null` an einer `Optional`-Stelle ist ein Vertragsbruch, kein Sonderfall; ihn still in
`Optional.empty()` umzudeuten verdeckt den Fehler beim Aufrufer. Jetzt steht dort
`requireNonNull` mit einer Meldung, die sagt, was gilt.

### Ein Testmuster, das täuscht

Eine Attrappe als `@ApplicationScoped`-Bean wird als Proxy injiziert. Ein **Feldzugriff**
darauf liest immer `null`, während der Bean daneben den richtigen Wert trägt. Der Test schlägt
mit „expected not blank but was null" fehl, und die Ursache steht nirgends. Zugriff
ausschließlich über Methoden.

---

## Regime

**Berührt wie vorgesehen:** neues Modul `backend/bankzugang`; Port und Domänenobjekte in
`kern`; `V3__bankzugang.sql`, drei Entitäten und ein Repository in `persistenz`; Werkzeuge in
`mcp`; Endpunkte in `api`; drei Seiten und eine Bibliothek im Frontend; `docker-compose.yml`,
`.env.example`, `.gitignore`, `backend/pom.xml`.

**Zusätzlich berührt:** `backend/app/src/main/docker/Dockerfile` (Begründung oben),
`backend/app/pom.xml` (bindet den Adapter), `frontend/src/app/page.tsx` (ein Link).

**Unverändert:** `V1__grundschema.sql`, `V2__ledger.sql`, `RlsKontext`, `Importdienst` und die
Parser, `doc/`, `infra/`.

**Nicht gebaut, jeweils mit Absicht:** keine Zahlungsauslösung — auch keine ungenutzte Methode
und kein auskommentierter Rest; keine Sichtbarkeitsstufen und keine Spalte, die auf sie
vorbereitet; keine automatische Zuordnung externer Konten auf `konto`; kein Abrufplan; keine
Buchung im Ledger.

---

## Geprüft

`make pruefen` grün, Backend und Frontend. 131 Tests: 66 `kern`, 22 `persistenz`, 10
`bankzugang`, 33 `app`. Abdeckung des neuen Codes 86 Prozent.

---

## Offen geblieben

1. **Der Feldabdeckungsbericht.** Braucht einen Sandbox-Lauf. Er entscheidet mehr als der
   gesamte Rest dieses Durchstichs — siehe oben.
2. **Die Rote Probe.** Setzt einen autorisierten Zugang voraus.
3. **Der Name der MCP-Werkzeuge.** Abweichung vom Auftrag, begründet, aber Concepts
   Entscheidung.
4. **Die Zuordnung externer Konten auf `konto`.** Bleibt leer und von Hand setzbar. Wann und
   wodurch sie gesetzt wird, ist offen — geraten wird sie nicht.
5. **Produktionsregistrierung beim Anbieter.** Eigene Anwendung, eigenes Schlüsselpaar, und
   sie setzt Datenschutzerklärung und Nutzungsbedingungen unter der Anwendungsadresse voraus,
   die es noch nicht gibt.
6. **Ein zweiter Anbieter.** Der Port ist dafür gebaut, aber die Annahme ist ungeprüft: ob er
   trägt, zeigt sich erst am zweiten Adapter. Bis dahin ist die Kapselung eine begründete
   Vermutung.
