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

RUN addgroup -S haushalt && adduser -S haushalt -G haushalt

# Der Standalone-Modus buendelt den Server samt der tatsaechlich benoetigten
# Abhaengigkeiten. Das Image kommt deshalb ohne node_modules aus.
COPY --from=bau --chown=haushalt:haushalt /bau/.next/standalone ./
COPY --from=bau --chown=haushalt:haushalt /bau/.next/static ./.next/static

USER haushalt

EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=5 \
    CMD wget -qO- http://localhost:3000/ >/dev/null || exit 1

CMD ["node", "server.js"]
