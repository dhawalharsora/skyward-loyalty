# Skyward Loyalty — common tasks. Run `make` (or `make help`) to list targets.
# The demo scripts auto-detect Java 21; gradle invocations use $(JAVA) (override: make test JAVA=/path/to/jdk21).

.DEFAULT_GOAL := help
SHELL := /usr/bin/env bash
JAVA  ?= /opt/homebrew/opt/openjdk@21

.PHONY: help up down purge demo demo-accrual demo-redeem demo-compensate demo-strangler demo-shadow build test logs

help: ## Show this help
	@printf "Skyward Loyalty — make targets:\n\n"
	@awk 'BEGIN{FS=":.*## "} /^[a-zA-Z0-9_-]+:.*## /{printf "  \033[36m%-16s\033[0m %s\n",$$1,$$2}' $(MAKEFILE_LIST)
	@printf "\nTypical: \033[1mmake up && make demo && make down\033[0m  (Docker must be running)\n"

up: ## Build jars + start Postgres, Kafka, and all 3 services
	@./scripts/up.sh

down: ## Stop services + containers (keep the DB volume)
	@./scripts/down.sh

purge: ## Stop everything and wipe the Postgres volume (fresh DB)
	@./scripts/down.sh --purge

demo: ## Run ALL flows end-to-end (needs: make up first)
	@./scripts/demo.sh all

demo-accrual: ## Flow 1 — accrual via transactional outbox
	@./scripts/demo.sh accrual

demo-redeem: ## Flow 2 — redemption saga (happy path)
	@./scripts/demo.sh redeem

demo-compensate: ## Flow 2b — saga compensation (partner FAIL → release hold)
	@./scripts/demo.sh compensate

demo-strangler: ## Flow 3 — config-driven sticky routing (legacy vs new)
	@./scripts/demo.sh strangler

demo-shadow: ## Flow 3b — shadow-compare (serve legacy, log mismatch vs new)
	@./scripts/demo.sh shadow

build: ## Build the three runnable bootJars
	@JAVA_HOME="$(JAVA)" ./gradlew :business-api:bootJar :adapter-legacy:bootJar :experience-api:bootJar

test: ## Run the full test suite (Testcontainers; needs Docker)
	@JAVA_HOME="$(JAVA)" ./gradlew test

logs: ## Tail the running service logs
	@tail -f scripts/.run/logs/*.log
