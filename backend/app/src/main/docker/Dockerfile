# =============================================================================
# Backend - Quarkus im JVM-Modus
#
# Zweistufig: bauen mit dem JDK, laufen mit der JRE. Das fertige Image enthaelt
# weder Maven noch Quellcode - relevant, weil das Zielbild Open Source ist und
# ein Image mit Buildumgebung unnoetig Angriffsflaeche mitschleppt.
#
# Kontext ist das Verzeichnis backend/. Aufruf aus dem Projektwurzelverzeichnis:
#   docker build -f backend/app/src/main/docker/Dockerfile backend/
# =============================================================================

# --- Bauen -------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS bau

WORKDIR /bau

# Jedes Modul einzeln, damit eine Aenderung an einem nicht die Schichten der
# anderen verwirft. Die Liste muss zu den <modules> in pom.xml passen - fehlt
# eines, bricht der Build mit "Child module ... does not exist", und zwar erst
# im Container, nicht beim lokalen Bauen.
COPY pom.xml .
COPY kern       kern/
COPY persistenz persistenz/
COPY bankzugang bankzugang/
COPY mcp        mcp/
COPY api        api/
COPY app        app/

# Ein einziger Maven-Aufruf, und das lokale Repository liegt in einem
# BuildKit-Cache statt in einer Image-Schicht.
#
# Frueher stand hier ein vorgeschaltetes "dependency:go-offline ... || true", um
# die Abhaengigkeiten in einer eigenen, selten wechselnden Schicht zu halten. Das
# war ein Fehler: schlug der Download teilweise fehl, verschluckte das "|| true"
# den Fehler und hinterliess im lokalen Repository negative Eintraege. Der
# eigentliche Build uebernahm sie und brach mit
#
#     Non-resolvable import POM: ... quarkus-bom:pom:3.33.3.1 (absent)
#
# ab - "absent" ist Mavens Marker fuer einen zwischengespeicherten Fehlversuch,
# nicht fuer ein fehlendes Artefakt. Der Cache-Mount erfuellt denselben Zweck,
# ohne dass ein Fehlschlag stillschweigend festgeschrieben wird.
#
# retryHandler faengt vereinzelte Netzwerkfehler ab, statt den ganzen Build daran
# scheitern zu lassen.
#
# Tests laufen in der CI, nicht hier: sie brauchen Docker fuer die Testcontainer,
# und Docker-in-Docker im Buildschritt ist mehr Aufwand als Nutzen.
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B package -DskipTests \
        -Dmaven.wagon.http.retryHandler.count=3 \
        -Dmaven.wagon.httpconnectionManager.ttlSeconds=120


# --- Laufen ------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Nicht als root. Ein Prozess, der Kontodaten haelt, braucht keine Rootrechte.
RUN addgroup -S haushalt && adduser -S haushalt -G haushalt

WORKDIR /anwendung

# Reihenfolge nach Aenderungshaeufigkeit: Bibliotheken aendern sich selten,
# die eigene Anwendung bei jedem Commit.
COPY --from=bau --chown=haushalt:haushalt /bau/app/target/quarkus-app/lib/          lib/
COPY --from=bau --chown=haushalt:haushalt /bau/app/target/quarkus-app/*.jar         ./
COPY --from=bau --chown=haushalt:haushalt /bau/app/target/quarkus-app/app/          app/
COPY --from=bau --chown=haushalt:haushalt /bau/app/target/quarkus-app/quarkus/      quarkus/

USER haushalt

EXPOSE 8080

ENV JAVA_OPTS_APPEND="-XX:MaxRAMPercentage=75"

# Die Version, die die Anwendung ueber MCP und OpenAPI meldet. Wird vom
# Release-Build aus dem Git-Tag gesetzt; wer das Image von Hand baut, bekommt
# "dev" - und das ist ehrlicher als eine Zahl, die nichts benennt.
ARG HB_VERSION=dev
ENV HB_VERSION=${HB_VERSION}

# Der Health-Endpunkt kommt aus quarkus-smallrye-health und ist in der
# Konfiguration ausdruecklich von der Anmeldepflicht ausgenommen.
#
# 127.0.0.1 statt localhost: letzteres kann im Container auf ::1 auflösen, wo
# der Server nicht lauscht.
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=6 \
    CMD wget -qO- http://127.0.0.1:8080/q/health/ready >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/anwendung/quarkus-run.jar"]
