# Module und Schichten

## Maven-Multimodul

```
backend/
├── pom.xml          Eltern-POM: Versionen, Plugins, Java-Release
├── kern/            Domäne und Berechnungslogik — frameworkfrei
├── persistenz/      JPA-Entitäten, Repositories, Flyway, RLS-Kontext
├── mcp/             MCP-Tools
├── api/             REST-Ressourcen für das Dashboard
└── app/             Quarkus-Runner, Konfiguration, Container-Image
```

## Abhängigkeitsrichtung

```
app ──► api ──┐
              ├──► kern ◄── persistenz
app ──► mcp ──┘
```

`app` ist das einzige Modul, das ein ausführbares Artefakt erzeugt. Es hat selbst keine
Fachlogik, sondern bündelt die anderen und hält die Konfiguration.

## Was in welches Modul gehört

### `kern`

Die Domäne. Wertobjekte (`Betrag`, `KontoId`), Aufzählungen (`Sphaere`, `Topfart`) und die
Berechnungslogik. **Keine Framework-Annotationen** — kein Quarkus, kein JPA, kein Jackson.

Der Grund ist Testbarkeit: `kern` lässt sich mit einfachen JUnit-Tests ohne Containerstart
prüfen. Die Berechnung von `verfuegbar` ist die kritischste Logik im System; sie muss in
Millisekunden testbar sein, damit man sie auch tatsächlich oft testet.

Zugriff auf Daten läuft über Ports — Interfaces, die `kern` definiert und `persistenz`
implementiert. Damit zeigt die Abhängigkeit nach innen, nicht nach außen.

```java
// in kern definiert
public interface KontoPort {
    Optional<Konto> findeNachId(KontoId id);
    List<Konto> alleSichtbaren();
}
```

### `persistenz`

JPA-Entitäten, Repositories, Flyway-Migrationen und der RLS-Kontext. Implementiert die
Ports aus `kern`.

Entitäten sind **nicht** dasselbe wie Domänenobjekte. Eine `KontoEntity` trägt technische
Belange (Fremdschlüssel, Versionsspalte, Lazy Loading); ein `Konto` aus `kern` trägt
fachliche. Die Abbildung dazwischen ist Aufgabe des Repositories.

### `mcp`

MCP-Tools. Jedes Tool ist eine dünne Übersetzungsschicht: Parameter entgegennehmen,
Domain-Service aufrufen, Ergebnis in eine für das Gespräch taugliche Form bringen.

Ein Tool, das rechnet, ist ein Fehler. Die Rechnung gehört in `kern`, damit REST und MCP
dieselbe Zahl liefern.

Tool-Beschreibungen sind auf Deutsch und beschreiben, **wann** ein Tool anzuwenden ist:

```java
@Tool(description = """
    Liefert die Konten, auf die der angemeldete Benutzer Zugriff hat.
    Nutze dieses Tool, bevor du eine kontobezogene Frage beantwortest —
    die Zugriffsrechte unterscheiden sich je Benutzer.
    """)
```

### `api`

REST für das Dashboard. Ebenfalls dünn: DTO rein, Domain-Service rufen, DTO raus.

DTOs sind eigene Typen, nicht die Domänenobjekte selbst. Sonst zieht eine Änderung an der
Domäne stillschweigend eine Änderung am öffentlichen Vertrag nach sich.

### `app`

Quarkus-Runner. Enthält `application.properties`, das Dockerfile und die
Integrationstests, die den ganzen Stapel benötigen — etwa der RLS-Test.

## Jandex

Quarkus findet CDI-Beans in einem Abhängigkeits-Modul nur, wenn dieses einen Jandex-Index
mitbringt. Deshalb läuft in `kern`, `persistenz`, `mcp` und `api` das
`jandex-maven-plugin`.

Ohne den Index startet die Anwendung — aber die Beans aus den Modulen fehlen, und der
Fehler zeigt sich erst zur Laufzeit als unerklärliches `UnsatisfiedResolutionException`.
Wenn ein neues Modul angelegt wird, gehört das Plugin dazu.

## Geldbeträge

`BigDecimal` mit Skalierung 2 und `RoundingMode.HALF_UP`, in der Datenbank `numeric(14,2)`.
Niemals `double` oder `float`.

Der Domänentyp ist `Betrag`. Er kapselt die Skalierung, damit sie nicht an jeder
Rechenstelle einzeln richtig gemacht werden muss — was bedeutet, dass sie irgendwo einzeln
falsch gemacht würde.
