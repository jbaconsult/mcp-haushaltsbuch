# =============================================================================
# Frontend - Next.js im Standalone-Modus
#
# Kontext ist das Verzeichnis frontend/:
#   docker build -f frontend/Dockerfile frontend/
# =============================================================================

# --- Abhaengigkeiten ---------------------------------------------------------
FROM node:24-alpine AS abhaengigkeiten

WORKDIR /bau

# Nur die Manifeste kopieren. Diese Schicht bleibt im Cache, solange sich keine
# Abhaengigkeit aendert.
COPY package.json package-lock.json ./
RUN npm ci


# --- Bauen -------------------------------------------------------------------
FROM node:24-alpine AS bau

WORKDIR /bau

COPY --from=abhaengigkeiten /bau/node_modules ./node_modules
COPY . .

ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build


# --- Laufen ------------------------------------------------------------------
FROM node:24-alpine

WORKDIR /anwendung

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# Die Version, die das Dashboard anzeigt. Wird vom Release-Build aus dem Git-Tag
# gesetzt; ohne sie steht dort "dev".
#
# Bewusst erst in der Laufzeitstufe und ohne NEXT_PUBLIC_-Praefix: die Fusszeile
# wird serverseitig gerendert. Ein Wert, der in das Browser-Buendel eingebacken
# wuerde, muesste bei jeder Version neu gebaut werden - so genuegt ein anderer
# Wert im Container.
ARG HB_VERSION=dev
ENV HB_VERSION=${HB_VERSION}

RUN addgroup -S haushalt && adduser -S haushalt -G haushalt

# Der Standalone-Modus buendelt den Server samt der tatsaechlich benoetigten
# Abhaengigkeiten. Das Image kommt deshalb ohne node_modules aus.
COPY --from=bau --chown=haushalt:haushalt /bau/.next/standalone ./
COPY --from=bau --chown=haushalt:haushalt /bau/.next/static ./.next/static

USER haushalt

EXPOSE 3000

# Prüft den eigenen Health-Endpunkt, nicht die Startseite: die ruft das Backend
# auf, und ein Frontend soll nicht als krank gelten, weil eine Abhängigkeit
# gerade neu startet.
#
# Über node statt wget, weil node im Image garantiert vorhanden ist und der
# globale fetch seit Node 18 zur Verfügung steht. Und über 127.0.0.1 statt
# localhost: letzteres kann auf ::1 auflösen, wo der Server nicht lauscht.
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD node -e "fetch('http://127.0.0.1:3000/api/gesundheit').then(a=>process.exit(a.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "server.js"]
