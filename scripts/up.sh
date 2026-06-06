#!/usr/bin/env bash
# Brings the whole stack up: builds jars, starts Postgres + Kafka (Docker), then the three services.
# Idempotent-ish: safe to re-run (it restarts the app services).
#
# Usage:  ./scripts/up.sh
# Env:    SKYWARD_POSTGRES_PORT (default 5433), STRANGLER_LEGACY_PERCENT (default 50),
#         PARTNER_MODE (SUCCEED|FAIL|SLOW|TIMEOUT_BUT_FULFILS|FAIL_THEN_SUCCEED)

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

detect_java
require_docker

step "Starting infrastructure (Postgres + Kafka, KRaft) via docker compose"
( cd "$ROOT" && SKYWARD_POSTGRES_PORT="$SKYWARD_POSTGRES_PORT" SKYWARD_KAFKA_PORT="$SKYWARD_KAFKA_PORT" \
    docker compose up -d )
wait_container_healthy skyward-postgres
wait_container_healthy skyward-kafka

build_jars

# Start fresh app services
stop_service experience; stop_service core; stop_service legacy
start_core
start_legacy
start_experience

cat <<EOF

$(ok "Skyward is up.")
  core (business+domain)  http://localhost:$CORE_PORT     (Swagger: /swagger-ui)
  legacy SOAP service     http://localhost:$LEGACY_PORT     (WSDL:   /ws/tiers.wsdl)
  experience / edge       http://localhost:$EXPERIENCE_PORT     (strangler facade)
  Postgres                localhost:$SKYWARD_POSTGRES_PORT   Kafka  localhost:$SKYWARD_KAFKA_PORT
  logs                    scripts/.run/logs/{core,legacy,experience}.log

Next:  ./scripts/demo.sh all      (or: accrual | redeem | compensate | strangler | shadow)
Down:  ./scripts/down.sh
EOF
