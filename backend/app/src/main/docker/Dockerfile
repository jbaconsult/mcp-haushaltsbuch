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

# Zuerst nur die POMs kopieren und Abhaengigkeiten laden. Diese Schicht bleibt im
# Cache, solange sich keine Abhaengigkeit aendert - Quellcodeaenderungen loesen
# dann keinen erneuten Download aus.
COPY pom.xml .
COPY kern/pom.xml       kern/
COPY persistenz/pom.xml persistenz/
COPY mcp/pom.xml        mcp/
COPY api/pom.xml        api/
COPY app/pom.xml        app/
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY kern       kern/
COPY persistenz persistenz/
COPY mcp        mcp/
COPY api        api/
COPY app        app/

# Tests laufen in der CI, nicht im Image-Build: sie brauchen Docker fuer die
# Testcontainer, und Docker-in-Docker im Buildschritt ist mehr Aufwand als Nutzen.
RUN mvn -B -q package -DskipTests


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

# Der Health-Endpunkt kommt aus quarkus-smallrye-health und ist in der
# Konfiguration ausdruecklich von der Anmeldepflicht ausgenommen.
#
# 127.0.0.1 statt localhost: letzteres kann im Container auf ::1 auflösen, wo
# der Server nicht lauscht.
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=6 \
    CMD wget -qO- http://127.0.0.1:8080/q/health/ready >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/anwendung/quarkus-run.jar"]
