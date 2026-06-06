#!/usr/bin/env bash
# Shared library for the Skyward demo scripts: config, Java detection, service lifecycle, HTTP/JSON
# helpers. Sourced by up.sh / down.sh / demo.sh. Written for macOS' default bash 3.2 (no bash-4 features).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT/scripts/.run"
LOG_DIR="$RUN_DIR/logs"
mkdir -p "$LOG_DIR"

# ---- configuration (override via env) -------------------------------------------------------------
# Postgres host port defaults to 5433 so a local Postgres already on 5432 is never shadowed.
: "${SKYWARD_POSTGRES_PORT:=5433}"
: "${SKYWARD_KAFKA_PORT:=9092}"
: "${CORE_PORT:=8080}"        # business+domain core
: "${LEGACY_PORT:=8081}"      # legacy SOAP service
: "${EXPERIENCE_PORT:=8082}"  # edge / strangler facade
: "${STRANGLER_LEGACY_PERCENT:=50}"
export SKYWARD_POSTGRES_PORT SKYWARD_KAFKA_PORT CORE_PORT LEGACY_PORT EXPERIENCE_PORT

VERSION="0.0.1-SNAPSHOT"
CORE_JAR="$ROOT/business-api/build/libs/business-api-$VERSION.jar"
LEGACY_JAR="$ROOT/adapter-legacy/build/libs/adapter-legacy-$VERSION.jar"
EXPERIENCE_JAR="$ROOT/experience-api/build/libs/experience-api-$VERSION.jar"

# ---- pretty output --------------------------------------------------------------------------------
if [ -t 1 ]; then
  C_BOLD="$(printf '\033[1m')"; C_DIM="$(printf '\033[2m')"; C_GREEN="$(printf '\033[32m')"
  C_YELLOW="$(printf '\033[33m')"; C_BLUE="$(printf '\033[34m')"; C_RED="$(printf '\033[31m')"
  C_RESET="$(printf '\033[0m')"
else
  C_BOLD=""; C_DIM=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_RED=""; C_RESET=""
fi
say()   { printf '%s\n' "$*"; }
step()  { printf '\n%s▸ %s%s\n' "$C_BOLD$C_BLUE" "$*" "$C_RESET"; }
ok()    { printf '%s✓ %s%s\n' "$C_GREEN" "$*" "$C_RESET"; }
warn()  { printf '%s! %s%s\n' "$C_YELLOW" "$*" "$C_RESET"; }
die()   { printf '%s✗ %s%s\n' "$C_RED" "$*" "$C_RESET" >&2; exit 1; }

# ---- prerequisites --------------------------------------------------------------------------------
detect_java() {
  if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then return; fi
  local cand
  for cand in /opt/homebrew/opt/openjdk@21 /usr/local/opt/openjdk@21 \
              /Library/Java/JavaVirtualMachines/*-21*/Contents/Home; do
    if [ -x "$cand/bin/java" ]; then export JAVA_HOME="$cand"; return; fi
  done
  if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -q 'version "21'; then
    export JAVA_HOME="$(cd "$(dirname "$(command -v java)")/.." && pwd)"; return
  fi
  die "Java 21 not found. Install it (e.g. 'brew install openjdk@21') or set JAVA_HOME to a JDK 21."
}
JAVA() { "$JAVA_HOME/bin/java" "$@"; }

require_docker() {
  command -v docker >/dev/null 2>&1 || die "docker not found."
  docker info >/dev/null 2>&1 || die "Docker daemon not running. Start Docker Desktop and retry."
}

# ---- HTTP / JSON (no jq required; pretty-prints with jq if present) --------------------------------
HAVE_JQ=0; command -v jq >/dev/null 2>&1 && HAVE_JQ=1

# http METHOD URL [JSON_BODY] -> sets HTTP_BODY and HTTP_CODE
http() {
  local method="$1" url="$2" data="${3:-}" raw
  if [ -n "$data" ]; then
    raw="$(curl -sS -X "$method" -H 'Content-Type: application/json' -d "$data" -w $'\n%{http_code}' "$url")"
  else
    raw="$(curl -sS -X "$method" -w $'\n%{http_code}' "$url")"
  fi
  HTTP_CODE="${raw##*$'\n'}"
  HTTP_BODY="${raw%$'\n'*}"
}

print_body() {
  [ -z "${HTTP_BODY:-}" ] && return 0
  if [ "$HAVE_JQ" = 1 ]; then printf '%s\n' "$HTTP_BODY" | jq . 2>/dev/null || printf '%s\n' "$HTTP_BODY"
  else printf '%s%s%s\n' "$C_DIM" "$HTTP_BODY" "$C_RESET"; fi
}

# json_str KEY  (reads stdin) -> first string value for KEY
json_str() { grep -oE "\"$1\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 | sed -E "s/.*:[[:space:]]*\"([^\"]*)\"/\1/"; }
# json_num KEY  (reads stdin) -> first numeric value for KEY
json_num() { grep -oE "\"$1\"[[:space:]]*:[[:space:]]*-?[0-9]+" | head -1 | sed -E "s/.*:[[:space:]]*//"; }

uniq_key() { printf 'demo-%s-%s' "$(date +%s)" "${RANDOM}"; }

# ---- waiting --------------------------------------------------------------------------------------
wait_health() { # URL NAME [timeout_s]
  local url="$1" name="$2" timeout="${3:-60}" i=0
  printf '%s  waiting for %s %s' "$C_DIM" "$name" "$C_RESET"
  while [ "$i" -lt "$timeout" ]; do
    if curl -fsS "$url" 2>/dev/null | grep -q '"status":"UP"'; then printf '\n'; ok "$name is UP ($url)"; return 0; fi
    printf '.'; sleep 1; i=$((i+1))
  done
  printf '\n'; die "$name did not become healthy within ${timeout}s. See $LOG_DIR."
}

wait_container_healthy() { # CONTAINER_NAME [timeout_s]
  local name="$1" timeout="${2:-90}" i=0 status
  printf '%s  waiting for container %s %s' "$C_DIM" "$name" "$C_RESET"
  while [ "$i" -lt "$timeout" ]; do
    status="$(docker inspect -f '{{.State.Health.Status}}' "$name" 2>/dev/null || echo missing)"
    if [ "$status" = healthy ]; then printf '\n'; ok "$name healthy"; return 0; fi
    printf '.'; sleep 2; i=$((i+2))
  done
  printf '\n'; die "container $name not healthy within ${timeout}s."
}

# wait_balance MEMBER_ID EXPECTED [timeout_s] — polls the (eventually-consistent) projection
wait_balance() {
  local id="$1" expected="$2" timeout="${3:-25}" i=0 bal
  printf '%s  waiting for balance projection to reach %s %s' "$C_DIM" "$expected" "$C_RESET"
  while [ "$i" -lt "$timeout" ]; do
    bal="$(curl -fsS "http://localhost:$CORE_PORT/members/$id/balance" 2>/dev/null | json_num balance || true)"
    if [ "${bal:-}" = "$expected" ]; then printf '\n'; ok "balance = $expected (projection caught up)"; return 0; fi
    printf '.'; sleep 1; i=$((i+1))
  done
  printf '\n'; warn "balance did not reach $expected within ${timeout}s (last seen: ${bal:-none})."
}

# ---- build & service lifecycle --------------------------------------------------------------------
build_jars() {
  step "Building runnable jars (gradlew bootJar)"
  ( cd "$ROOT" && JAVA_HOME="$JAVA_HOME" ./gradlew --quiet \
      :business-api:bootJar :adapter-legacy:bootJar :experience-api:bootJar )
  ok "jars built"
}

start_service() { # NAME JAR HEALTH_URL  (extra env passed by caller via exported vars)
  local name="$1" jar="$2" health="$3"
  [ -f "$jar" ] || die "missing jar: $jar — run ./scripts/up.sh (or gradlew bootJar) first."
  nohup "$JAVA_HOME/bin/java" -jar "$jar" >"$LOG_DIR/$name.log" 2>&1 &
  echo $! >"$RUN_DIR/$name.pid"
  wait_health "$health" "$name"
}

start_core() { # optional: PARTNER_MODE env
  step "Starting core (business+domain) on :$CORE_PORT  [partner mode: ${PARTNER_MODE:-SUCCEED}]"
  SERVER_PORT="$CORE_PORT" \
  SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$SKYWARD_POSTGRES_PORT/skyward" \
  SPRING_KAFKA_BOOTSTRAP_SERVERS="localhost:$SKYWARD_KAFKA_PORT" \
  SKYWARD_PARTNER_FULFILMENT_MODE="${PARTNER_MODE:-SUCCEED}" \
  SKYWARD_PARTNER_FULFILMENT_LATENCY_MS="${PARTNER_LATENCY_MS:-0}" \
    start_service core "$CORE_JAR" "http://localhost:$CORE_PORT/actuator/health"
}

start_legacy() {
  step "Starting legacy SOAP service on :$LEGACY_PORT  (WSDL: /ws/tiers.wsdl)"
  SERVER_PORT="$LEGACY_PORT" \
    start_service legacy "$LEGACY_JAR" "http://localhost:$LEGACY_PORT/actuator/health"
}

start_experience() { # optional: SHADOW_ENABLED, SHADOW_AUTHORITATIVE, STRANGLER_LEGACY_PERCENT
  step "Starting experience edge on :$EXPERIENCE_PORT  [legacy %=${STRANGLER_LEGACY_PERCENT}, shadow=${SHADOW_ENABLED:-false}]"
  SERVER_PORT="$EXPERIENCE_PORT" \
  SKYWARD_STRANGLER_LEGACY_PERCENT="$STRANGLER_LEGACY_PERCENT" \
  SKYWARD_STRANGLER_SHADOW_ENABLED="${SHADOW_ENABLED:-false}" \
  SKYWARD_STRANGLER_SHADOW_AUTHORITATIVE="${SHADOW_AUTHORITATIVE:-legacy}" \
  SKYWARD_DOMAIN_BASE_URL="http://localhost:$CORE_PORT" \
  SKYWARD_LEGACY_URI="http://localhost:$LEGACY_PORT/ws" \
    start_service experience "$EXPERIENCE_JAR" "http://localhost:$EXPERIENCE_PORT/actuator/health"
}

stop_service() { # NAME
  # Note: separate `local` lines on purpose — a single `local a=.. b="$a"` expands all RHS as args to
  # the builtin before any assignment, so `$a` would be unbound there under `set -u`.
  local name="$1"
  local pidfile="$RUN_DIR/$name.pid"
  local pid=""
  [ -f "$pidfile" ] || return 0
  pid="$(cat "$pidfile" 2>/dev/null || true)"
  if [ -n "${pid:-}" ] && kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    local i=0; while kill -0 "$pid" 2>/dev/null && [ "$i" -lt 10 ]; do sleep 1; i=$((i+1)); done
    kill -9 "$pid" 2>/dev/null || true
    ok "stopped $name (pid $pid)"
  fi
  rm -f "$pidfile"
}
