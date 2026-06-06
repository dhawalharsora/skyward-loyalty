#!/usr/bin/env bash
# Drives the three Skyward flows against the running stack. Run ./scripts/up.sh first.
#
# Usage:  ./scripts/demo.sh [all|accrual|redeem|compensate|strangler|shadow]   (default: all)
#
# Each sub-demo is self-contained (it enrols/seeds the members it needs), so you can run them in any
# order. 'compensate' restarts the core in FAIL mode and restores it; 'shadow' flips the edge into
# shadow mode and restores it — both leave the stack back in its default state.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
detect_java  # needed because compensate/shadow restart services

CORE="http://localhost:$CORE_PORT"
LEGACY="http://localhost:$LEGACY_PORT"
EDGE="http://localhost:$EXPERIENCE_PORT"

preflight() {
  curl -fsS "$CORE/actuator/health" >/dev/null 2>&1 || die "core is not up. Run ./scripts/up.sh first."
  curl -fsS "$LEGACY/actuator/health" >/dev/null 2>&1 || die "legacy is not up. Run ./scripts/up.sh first."
  curl -fsS "$EDGE/actuator/health" >/dev/null 2>&1 || die "experience edge is not up. Run ./scripts/up.sh first."
}

# enrol_member NAME TIER -> echoes the new member id
enrol_member() {
  http POST "$CORE/members" "{\"fullName\":\"$1\",\"tier\":\"$2\"}"
  printf '%s' "$HTTP_BODY" | json_str id
}

# ===================================================================================================
demo_accrual() {
  printf '\n%s════════ FLOW 1 — Accrual via Transactional Outbox ════════%s\n' "$C_BOLD" "$C_RESET"
  say "Partner reports points → earning rules → ledger+outbox (1 tx) → relay → Kafka → balance projection."

  step "Enrol a SILVER member"
  local id; id="$(enrol_member 'Ada (accrual demo)' SILVER)"; print_body
  ok "member id: $id"

  step "Accrue 1000 base points from a HOTEL partner (SILVER ×1.25, hotel campaign ×2.0 ⇒ 2500)"
  local key; key="$(uniq_key)"
  http POST "$CORE/accruals" \
    "{\"memberId\":\"$id\",\"basePoints\":1000,\"source\":\"partner:hotel:HILTON\",\"idempotencyKey\":\"$key\"}"
  print_body
  say "${C_DIM}status=$(printf '%s' "$HTTP_BODY" | json_str status), earnedPoints=$(printf '%s' "$HTTP_BODY" | json_num earnedPoints)${C_RESET}"

  step "Balance is async (outbox → Kafka → projection) — poll until it catches up"
  wait_balance "$id" 2500

  step "Idempotency: replay the SAME accrual (same idempotencyKey) — must NOT double-credit"
  http POST "$CORE/accruals" \
    "{\"memberId\":\"$id\",\"basePoints\":1000,\"source\":\"partner:hotel:HILTON\",\"idempotencyKey\":\"$key\"}"
  print_body
  local st; st="$(printf '%s' "$HTTP_BODY" | json_str status)"
  [ "$st" = DUPLICATE ] && ok "replay returned DUPLICATE — balance unchanged" || warn "expected DUPLICATE, got $st"
}

# ===================================================================================================
demo_redeem() {
  printf '\n%s════════ FLOW 2 — Redemption Saga (happy path) ════════%s\n' "$C_BOLD" "$C_RESET"
  say "Reserve hold → fulfil with partner (Resilience4j) → commit (burn). Steps idempotent."

  step "Enrol a GOLD member and give them 1500 points"
  local id; id="$(enrol_member 'Grace (redeem demo)' GOLD)"; ok "member id: $id"
  http POST "$CORE/accruals" \
    "{\"memberId\":\"$id\",\"basePoints\":1000,\"source\":\"partner:flight:QF1\",\"idempotencyKey\":\"$(uniq_key)\"}" >/dev/null
  wait_balance "$id" 1500   # GOLD ×1.5, no campaign

  step "Redeem 600 points for FLIGHT_UPGRADE"
  local key; key="$(uniq_key)"
  http POST "$CORE/redemptions" \
    "{\"memberId\":\"$id\",\"rewardCode\":\"FLIGHT_UPGRADE\",\"points\":600,\"idempotencyKey\":\"$key\"}"
  print_body
  local st; st="$(printf '%s' "$HTTP_BODY" | json_str status)"
  [ "$st" = COMPLETED ] && ok "redemption COMPLETED (partnerReference set)" || warn "expected COMPLETED, got $st"

  step "Balance drops via PointsBurned → projection (1500 − 600 = 900)"
  wait_balance "$id" 900

  step "Idempotency: replay the SAME redemption — must NOT burn twice"
  http POST "$CORE/redemptions" \
    "{\"memberId\":\"$id\",\"rewardCode\":\"FLIGHT_UPGRADE\",\"points\":600,\"idempotencyKey\":\"$key\"}"
  st="$(printf '%s' "$HTTP_BODY" | json_str status)"
  ok "replay returned status=$st (same saga, no second burn)"
  wait_balance "$id" 900
}

# ===================================================================================================
demo_compensate() {
  printf '\n%s════════ FLOW 2b — Redemption Saga (compensation path) ════════%s\n' "$C_BOLD" "$C_RESET"
  say "Force a DEFINITE partner failure → saga compensates (release hold), points are NOT burned."

  step "Restart core with partner mode = FAIL"
  stop_service core
  PARTNER_MODE=FAIL start_core

  step "Enrol a GOLD member and give them 1500 points"
  local id; id="$(enrol_member 'Alan (compensation demo)' GOLD)"; ok "member id: $id"
  http POST "$CORE/accruals" \
    "{\"memberId\":\"$id\",\"basePoints\":1000,\"source\":\"partner:flight:QF9\",\"idempotencyKey\":\"$(uniq_key)\"}" >/dev/null
  wait_balance "$id" 1500

  step "Redeem 600 — the partner will reject, so the saga must COMPENSATE"
  http POST "$CORE/redemptions" \
    "{\"memberId\":\"$id\",\"rewardCode\":\"FLIGHT_UPGRADE\",\"points\":600,\"idempotencyKey\":\"$(uniq_key)\"}"
  print_body
  local st; st="$(printf '%s' "$HTTP_BODY" | json_str status)"
  [ "$st" = COMPENSATED ] && ok "redemption COMPENSATED (hold released, reason recorded)" || warn "expected COMPENSATED, got $st"

  step "Balance is UNCHANGED (no burn) — still 1500"
  wait_balance "$id" 1500

  step "Restore core to partner mode = SUCCEED"
  stop_service core
  start_core
}

# ===================================================================================================
demo_strangler() {
  printf '\n%s════════ FLOW 3 — Strangler Fig (config-driven routing) ════════%s\n' "$C_BOLD" "$C_RESET"
  say "Edge routes GET /members/{id}/tier between legacy SOAP and new REST, sticky per member."

  step "Ensure the edge is in ROUTING mode (legacy %=${STRANGLER_LEGACY_PERCENT}, shadow off)"
  stop_service experience; SHADOW_ENABLED=false start_experience

  step "Enrol 6 members in core as GOLD, then seed the SAME ids in legacy as BRONZE (deliberate drift)"
  local ids="" id
  local n=0
  while [ "$n" -lt 6 ]; do
    id="$(enrol_member "Strangler member $n" GOLD)"
    http PUT "$LEGACY/admin/members/$id/tier" "{\"tier\":\"BRONZE\"}" >/dev/null
    ids="$ids $id"; n=$((n+1))
  done
  ok "enrolled + seeded 6 members"

  step "Call the edge for each — 'source' shows which system served (legacy⇒BRONZE, domain⇒GOLD)"
  printf '   %-10s  %-8s  %s\n' "member" "source" "tier"
  printf '   %-10s  %-8s  %s\n' "--------" "------" "----"
  for id in $ids; do
    http GET "$EDGE/members/$id/tier"
    printf '   %-10s  %-8s  %s\n' "${id%%-*}" "$(printf '%s' "$HTTP_BODY" | json_str source)" "$(printf '%s' "$HTTP_BODY" | json_str tier)"
  done
  say "${C_DIM}(at ${STRANGLER_LEGACY_PERCENT}% you should see a mix; cohorts are deterministic by member id)${C_RESET}"

  step "Stickiness: the SAME member always takes the same path"
  id="${ids##* }"
  local i=0; while [ "$i" -lt 3 ]; do
    http GET "$EDGE/members/$id/tier"
    printf '   call %d → source=%s\n' "$((i+1))" "$(printf '%s' "$HTTP_BODY" | json_str source)"
    i=$((i+1))
  done
  ok "consistent path for member ${id%%-*} (no flapping)"
}

# ===================================================================================================
demo_shadow() {
  printf '\n%s════════ FLOW 3b — Strangler Shadow-compare (pre-cutover) ════════%s\n' "$C_BOLD" "$C_RESET"
  say "Edge calls BOTH paths, serves the authoritative (legacy), and logs where new disagrees."

  step "Flip the edge into SHADOW mode (authoritative = legacy)"
  stop_service experience; SHADOW_ENABLED=true SHADOW_AUTHORITATIVE=legacy start_experience

  step "Enrol a member as GOLD in core, seed legacy as BRONZE (they disagree on purpose)"
  local id; id="$(enrol_member 'Shadow member' GOLD)"
  http PUT "$LEGACY/admin/members/$id/tier" "{\"tier\":\"BRONZE\"}" >/dev/null
  ok "member id: $id  (core=GOLD, legacy=BRONZE)"

  step "Call the edge — it serves the AUTHORITATIVE legacy answer (BRONZE)"
  http GET "$EDGE/members/$id/tier"; print_body
  local src; src="$(printf '%s' "$HTTP_BODY" | json_str source)"
  [ "$src" = legacy ] && ok "served source=legacy (new path never served during shadow)" || warn "expected legacy, got $src"

  step "...and the mismatch was detected and logged for the migration team"
  sleep 1
  if grep -q "shadow mismatch.*$id" "$LOG_DIR/experience.log" 2>/dev/null; then
    printf '   %s%s%s\n' "$C_DIM" "$(grep "shadow mismatch.*$id" "$LOG_DIR/experience.log" | tail -1 | sed -E 's/.*(strangler shadow mismatch.*)/\1/')" "$C_RESET"
    ok "shadow mismatch logged (legacy BRONZE vs domain GOLD)"
  else
    warn "did not find a mismatch log line yet — check $LOG_DIR/experience.log"
  fi

  step "Restore the edge to ROUTING mode (shadow off)"
  stop_service experience; SHADOW_ENABLED=false start_experience
}

# ===================================================================================================
main() {
  preflight
  case "${1:-all}" in
    accrual)    demo_accrual ;;
    redeem)     demo_redeem ;;
    compensate) demo_compensate ;;
    strangler)  demo_strangler ;;
    shadow)     demo_shadow ;;
    all)        demo_accrual; demo_redeem; demo_compensate; demo_strangler; demo_shadow ;;
    *)          die "unknown demo '$1' (use: all|accrual|redeem|compensate|strangler|shadow)" ;;
  esac
  printf '\n%s✓ demo complete.%s  Tip: tail -f scripts/.run/logs/*.log to watch the services.\n' "$C_GREEN" "$C_RESET"
}
main "$@"
