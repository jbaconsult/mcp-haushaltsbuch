# =============================================================================
# Haushaltsbuch
#
#   make hilfe    zeigt alle Ziele
# =============================================================================

.DEFAULT_GOAL := hilfe

MVN     := ./mvnw
BACKEND := backend
FRONTEND := frontend

.PHONY: hilfe
hilfe: ## Diese Übersicht
	@grep -hE '^[a-z-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

# --- Stack ------------------------------------------------------------------

.PHONY: hoch
hoch: ## Entwicklungsstack starten (OIDC extern)
	docker compose up -d --build

.PHONY: hoch-ci
hoch-ci: ## Vollständigen Stack mit eigenem Keycloak starten
	docker compose -f docker-compose.ci.yml up -d --build --wait
	docker compose -f docker-compose.ci.yml run --rm demodaten

.PHONY: runter
runter: ## Stack stoppen
	docker compose down
	docker compose -f docker-compose.ci.yml down

.PHONY: sauber
sauber: ## Stack stoppen UND Daten löschen
	docker compose down -v
	docker compose -f docker-compose.ci.yml down -v

.PHONY: logs
logs: ## Protokolle des Entwicklungsstacks
	docker compose logs -f

# --- Entwicklung ------------------------------------------------------------

.PHONY: backend-dev
backend-dev: ## Quarkus Dev Mode - startet Postgres selbst, lädt Änderungen ohne Neustart
	cd $(BACKEND) && $(MVN) quarkus:dev

.PHONY: frontend-dev
frontend-dev: ## Next.js Dev Server
	cd $(FRONTEND) && npm run dev

.PHONY: db-shell
db-shell: ## psql gegen den Entwicklungsstack
	docker compose exec postgres psql -U $${POSTGRES_USER:-haushaltsbuch_eigentuemer} -d $${POSTGRES_DB:-haushaltsbuch}

# --- Qualität ---------------------------------------------------------------

.PHONY: test
test: ## Alle Tests
	cd $(BACKEND) && $(MVN) -B test
	cd $(FRONTEND) && npm run test

.PHONY: pruefen
pruefen: ## Das, was auch die CI im Pull Request prüft
	cd $(BACKEND) && $(MVN) -B verify
	cd $(FRONTEND) && npm ci && npm run lint && npm run typecheck && npm run test && npm run build

.PHONY: formatieren
formatieren: ## Java-Code formatieren
	cd $(BACKEND) && $(MVN) -B spotless:apply

.PHONY: bauen
bauen: ## Container-Images lokal bauen
	docker compose build
