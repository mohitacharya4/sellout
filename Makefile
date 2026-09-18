# Sellout — the single entrypoint for every dev command (Constitution §2).
# `make check` runs exactly what CI gates on. Without GNU make, run the ./gradlew lines directly.

.DEFAULT_GOAL := help
SHELL := /bin/sh
GRADLE := ./gradlew
COMPOSE := docker compose

.PHONY: help install-hooks check format test test-int build scan up up-infra down logs e2e load clean

help: ## Show this help
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

install-hooks: ## Point git at the versioned pre-commit hook
	git config core.hooksPath .githooks

check: ## Everything CI gates on: compile, Spotless, Checkstyle, Error Prone, unit + property + ArchUnit, coverage
	$(GRADLE) check
	@echo "✅ all gates green"

format: ## Auto-fix formatting
	$(GRADLE) spotlessApply

test: ## Unit, property, and architecture tests
	$(GRADLE) test

test-int: ## Integration tests (Testcontainers — Docker required)
	$(GRADLE) integrationTest

build: ## Build distroless images into the local Docker daemon
	$(GRADLE) jibDockerBuild

scan: ## Vulnerability-scan the images (fails on HIGH/CRITICAL)
	trivy image --severity HIGH,CRITICAL --exit-code 1 --ignore-unfixed sellout/inventory-service:local
	trivy image --severity HIGH,CRITICAL --exit-code 1 --ignore-unfixed sellout/psp-simulator:local

up-infra: ## Backing services only (run services from the IDE)
	$(COMPOSE) up -d --wait

up: build ## Full stack, waits until every health check is green
	$(COMPOSE) --profile full up -d --wait
	@echo "inventory → http://localhost:8081/healthz   keycloak → http://localhost:8180   grafana → http://localhost:3000"

down: ## Stop the stack (keeps volumes)
	$(COMPOSE) --profile full down

logs: ## Follow stack logs
	$(COMPOSE) --profile full logs -f

e2e: ## kind end-to-end smoke — lands with spec 005
	@echo "kind e2e arrives with spec 005 (specs/005-kubernetes.md)"

load: ## k6 spike — lands with spec 006
	@echo "k6 load test arrives with spec 006 (specs/006-load-chaos.md)"

clean: ## Remove volumes and build output
	$(COMPOSE) --profile full down -v
	$(GRADLE) clean
