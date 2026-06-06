#!/usr/bin/env bash
# Stops the three services and tears down the Docker infrastructure.
#
# Usage:  ./scripts/down.sh            (stop services + containers, keep the DB volume)
#         ./scripts/down.sh --purge    (also delete the Postgres volume — fresh DB next time)

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

step "Stopping application services"
stop_service experience
stop_service core
stop_service legacy

step "Stopping infrastructure"
if [ "${1:-}" = "--purge" ]; then
  ( cd "$ROOT" && docker compose down -v )
  ok "containers stopped and Postgres volume removed (fresh DB next time)"
else
  ( cd "$ROOT" && docker compose down )
  ok "containers stopped (DB volume kept)"
fi
