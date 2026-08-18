# ADR-0009 — MCP-Konnektor-Onboarding und die Keycloak-Vorbedingung

| | |
|---|---|
| Status | Angenommen |
| Datum | 2026-08-18 |
| Kumbuka | — |
| Verhältnis | **Revidiert ADR-0005** in zwei Punkten: der Aufwandsaussage und dem Onboarding-Modell |

## Kontext

ADR-0005 hat festgelegt, dass die Anwendung reiner Resource Server bleibt und Keycloak als
Identity Provider dient. Zwei Aussagen dieser ADR haben sich als unhaltbar erwiesen, und
zwar nicht durch neue Präferenzen, sondern durch Messung und durch fremde Erfahrung.

**Erstens die Aufwandsaussage.** ADR-0005 begründete die Wahl unter anderem damit, dass eine
Instanz bereits laufe und die Grenzkosten „ein Realm-Import" seien. Das war eine Annahme im
Gewand eines Befunds. Beim ersten Inbetriebnahmeversuch stellte sich heraus, dass unter der
vorgesehenen Adresse keine Instanz existiert. Und selbst mit Instanz stimmt die Aussage
nicht, siehe zweitens.

**Zweitens das Onboarding.** ADR-0005 ließ offen, wie ein KI-Client an den MCP-Endpunkt
kommt; an anderer Stelle war von einer Client-ID mit Secret die Rede. Eine
produktionsverifizierte Referenzumgebung, die auf derselben Keycloak-Instanz einen MCP-Server
betreibt, hat dafür belastbare Befunde geliefert, die dieses Muster überholen.

## Entscheidung

### Konnektor-Onboarding ist reine URL-Eingabe

Wer einen KI-Client anbindet, trägt **die MCP-Endpunkt-URL ein und sonst nichts**. Keine
Client-ID, kein Secret, keine Rotationsfunktion. Die Client-Identität stammt aus dem Client ID
Metadata Document des Anbieters oder aus anonymer Dynamic Client Registration.

Das ist keine Bequemlichkeitsentscheidung, sondern die Anpassung an die Arbeitsweise aktueller
KI-Clients. Es ersetzt jedes ältere Muster, einschließlich statischer öffentlicher Clients mit
handeingetragener Kennung und vertraulicher Clients mit rotierbarem Secret.

Konsequenzen, die von Anfang an eingeplant sein müssen, weil sie sich nur als Umbau
nachrüsten lassen:

- Die Verwaltungsoberfläche hat **kein Feld für eine Client-ID und keine Secret-Rotation**.
  Ein Entwurf, der eines von beiden zeigt, ist veraltet.
- Der Notausschalter ist **Client-Deaktivierung oder Entfernen aus der Freigabeliste**, nicht
  Secret-Rotation.
- Missbrauchsschutz greift bei der **Registrierung über die Anbieterfreigabe**, nicht auf
  Zugangsdatenebene.

### Keycloak braucht eine Erweiterung, sonst trägt der MCP-Fluss nicht

Eine unveränderte Keycloak-Distribution kann den MCP-OAuth-Fluss für aktuelle KI-Clients
nicht bedienen. Erforderlich sind zwei eigene Client-Policy-Executors als SPI-Erweiterung —
eine Erweiterung, kein Fork — im Image, auf dem der Realm läuft:

**Ein Grant-Normalizer**, der nicht konforme Grant-Types aus Registrierungsnutzlasten
entfernt. Er ist **ausschließlich entfernend**: er fügt niemals einen Grant-Type hinzu.
Wechselt ein Anbieter künftig auf `private_key_jwt`, wird sein Client dadurch vertraulich —
das ist eine Entwurfsänderung und ausdrücklich nichts, was der Executor auffängt.

**Ein Scope-Attacher**, der die Audience-Scope als **Default**-Scope anhängt. Er ist
ausschließlich hinzufügend, entfernt nichts und lässt fremde optionale Scopes in Ruhe. Die
entscheidende Feinheit: ist die Scope bereits als *optional* zugewiesen, weil die
Registrierungsnutzlast danach gefragt hat, muss die Zuweisung auf *default* **angehoben**
werden. Keycloak ignoriert das erneute Hinzufügen einer bereits zugewiesenen Scope
stillschweigend — eine naive Prüfung auf „fehlt, also hinzufügen" lässt sie optional, und die
Audience landet nie im Token.

Beide hängen an einer Client-Policy mit der Bedingung *client-updater-context*
(`ByAnonymous` und `ByRegistrationAccessToken`). Sie dürfen **nicht** an einer
CIMD-Profilbedingung hängen: eine Bedingung auf die Client-ID-URI enthält sich bei
CRUD-Operationen, und die Executors würden auf genau den Pfaden nie auslösen, auf die es
ankommt.

### Realm-Objekte

Ein Realm je Anwendung. Darin:

| Objekt | Art | Zweck |
|---|---|---|
| Backend-Client | vertraulich, Service Account | Resource Server für den MCP-Endpunkt und einziger Weg zur Keycloak-Admin-API |
| BFF-Client | vertraulich, Authorization Code mit PKCE | Hält die Anmeldesitzung für Dashboard und REST |
| Audience-Scope | Client Scope | Wird vom Scope-Attacher an dynamisch registrierte MCP-Clients gehängt |
| Realm-Rollen | Rollen | Autorisierung für beide Oberflächen |

**Kein statischer MCP-Client.** Er wäre das Muster, das diese ADR gerade ersetzt.

**Die Audience-Scope trägt einen anwendungsspezifischen Namen.** Beherbergt eine
Keycloak-Instanz mehr als einen MCP-bedienenden Realm, ist ein geteilter Scope-Name keine
Benennungsnachlässigkeit, sondern ein Audience-Confusion-Defekt: zwei Resource Server, die
gegenseitig ihre Token akzeptieren.

**Keine Kontosichtbarkeit in Keycloak.** Rollen bleiben grob. Die Zuordnung von Rechten auf
einzelne Konten lebt in der Datenbank; Keycloak weiß nichts von Konten und soll es nicht
lernen.

### Realm-Konfiguration als Vorlage, niemals als Export

Die Realm-Konfiguration lebt als **templatierte Datei mit Platzhaltern**, die beim Start
gefüllt werden. Ein Keycloak-Realm-Export wird **nie** eingecheckt: er trägt aktive
OAuth-Client-Secrets und Zugangsdaten im Klartext. Das Verzeichnis mit der gerenderten
Fassung gehört in die Ignorierliste.

Diese Regel stammt aus einem realen Vorfall in der Referenzumgebung, bei dem ein
eingecheckter Realm-Export aktive Geheimnisse offengelegt hat, die unter Zeitdruck getauscht
werden mussten.

### Protokollneutralität als Bauregel

MCP-Vokabular bleibt auf ein dünnes Adapterpaket beschränkt: Annotationen,
Werkzeugsignaturen und Transport-Objekte leben dort und nirgends sonst. Domäne, Dienste und
Repositories tragen keine MCP-Abhängigkeit.

Autorschaft und Identität werden **serverseitig** aus der Sicherheitsidentität abgeleitet,
niemals als Protokollargument übergeben und niemals über ein vom Client gesetztes Kennzeichen
bestimmt.

Durchgesetzt wird das durch einen Architekturtest, nicht durch einen Kommentar. Ein zweiter
Protokolladapter wird **nicht** vorsorglich gebaut — neutralitätsbewusst, nicht
neutralitätsimplementiert.

## Alternativen

**Statischer MCP-Client mit Client-ID und Secret**, wie ursprünglich angenommen. Verworfen,
weil aktuelle KI-Clients ihre Identität über CIMD oder DCR mitbringen und ein handgepflegter
Client den Pfad gar nicht erst erreicht. Zusätzlich verlangt er eine Rotationsfunktion in der
Oberfläche, die anschließend nichts absichert.

**Standard-Keycloak ohne Erweiterung.** Verworfen, weil der Fluss dann nicht trägt. Die
Audience landet ohne den Scope-Attacher nicht im Token, und nicht konforme Grant-Types aus
Anbieterregistrierungen werden abgewiesen. Eine separate Instanz ohne die Erweiterung ist
ausdrücklich keine zulässige Variante.

**Erweiterung selbst nachbauen.** Nicht verworfen, aber unnötig: sie existiert samt
Dokumentation in der Referenzumgebung. Deren Verhalten ist aus einer Zusammenfassung nicht
rekonstruierbar; wer an einem der beiden Executors etwas ändert, liest vorher die
Originaldokumentation.

## Konsequenzen

**Leichter:**

- Anbinden eines KI-Clients ist eine URL-Eingabe. Keine Zugangsdatenverwaltung in der
  Oberfläche, kein Rotationsverfahren, kein Ablauf von Secrets.
- Die Verwaltungsoberfläche wird kleiner als ursprünglich gedacht.

**Schwerer:**

- Die Wahl des Identity Providers ist keine reine Konfigurationsfrage mehr. Für die
  MCP-Fähigkeit ist ein Image mit der Erweiterung Voraussetzung. Für die eigene Installation
  ist das gelöst; für **Fremdinstallationen** heißt es: entweder liefert das Projekt ein
  solches Image mit, oder die MCP-Anbindung funktioniert dort nicht. Das bleibt offen.
- Die Aufwandsaussage aus ADR-0005 ist zurückgezogen. Der Aufbau eines Realms ist ein
  Infrastrukturschritt, kein Handgriff.

**Was den Durchstich nicht blockiert:** Der OAuth-Fluss wird zuerst mit dem MCP Inspector
verifiziert und erst danach mit einem Anbieter-Client. Realm, beide Clients und die
Audience-Scope reichen für den Durchstich. Die Registrierungskette mit der Erweiterung ist
die Stufe danach.

## Betriebsentscheidung: eine Instanz, ein Realm

Der Realm läuft auf der **bestehenden Keycloak-Instanz**, die bereits einen anderen
MCP-Server bedient und die Erweiterung aus Abschnitt 1 damit nachweislich trägt. Die
Vorbedingung für die MCP-Fähigkeit ist erfüllt, ohne dass ein eigenes Image gebaut werden
muss.

Daraus folgt, dass die Warnung vor Audience Confusion nicht theoretisch ist: auf dieser
Instanz liegt mehr als ein MCP-bedienender Realm. Der anwendungsspezifische Name der
Audience-Scope ist deshalb keine Benennungsfrage, sondern die Trennung zwischen zwei
Resource Servern.

**Es gibt genau einen Realm.** Keine Trennung zwischen Entwicklung und Produktion, kein
Staging. Das ist bewusst gewählt und keine Auslassung: das System hat zwei Nutzer, und ein
zweiter Realm wäre ein zweites Paar Zugangsdaten und eine zweite Stelle, an der etwas
auseinanderlaufen kann, ohne dass ihm ein Risiko gegenübersteht, das diesen Aufwand trägt.
Sollte das System später fremde Installationen bedienen, ist diese Abwägung neu zu führen.

## Offene Punkte

1. **Ort der BFF-Sitzung.** Die Referenzumgebung hält sie im Backend, mit Rückleitung auf
   einen Backend-Endpunkt; der aktuelle Bau hält sie im Frontend. Beides sind BFF-Muster,
   aber die Rückleitungsadresse im Realm hängt daran. Bis zur Entscheidung wird für den
   gebauten Stand konfiguriert.
2. **Zwei Prüfpunkte, die zu messen und nicht zu unterstellen sind:** ob die eingesetzte
   Keycloak-Version den `iss`-Parameter in der Autorisierungsantwort tatsächlich ausgibt, und
   wie sich `application_type` in Registrierungsnutzlasten mit der Grant-Normalisierung
   verträgt.
3. **MCP-Fähigkeit bei Fremdinstallationen**, siehe Konsequenzen.

## Nicht übernommen

Die Referenzumgebung trägt Produktentscheidungen, die hier nicht gelten: ihr Mandantenmodell
mit organisationsbezogenen Gruppenansprüchen — dieses System ist ausdrücklich nicht
mandantenfähig —, ihr Sichtbarkeits- und Scope-Modell, ihre Editionsgrenzen und ihre
Namenskonventionen.

Ein Punkt verdient dagegen einen bewussten Blick statt einer Übernahme: die Referenzumgebung
**erhebt strukturell keine** nutzerbezogenen Aufrufzahlen, Raten oder Aktivitätshäufigkeiten,
weil das in Deutschland als Verhaltens- und Leistungskontrolle einzuordnen wäre. Für ein
Haushaltssystem ist die arbeitsrechtliche Begründung gegenstandslos, die Bauweise aber
trotzdem richtig: nicht erhobene Daten sind billiger zu gestalten als nachträglich entfernte.
