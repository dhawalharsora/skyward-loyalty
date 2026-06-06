# Skyward — Run & Demo

A 3-command demo of all three integration flows on a clean machine.

## Prerequisites
- **Docker** (Desktop running) — for Postgres + Kafka.
- **JDK 21** — the scripts auto-detect `JAVA_HOME`, a Homebrew `openjdk@21`, or a JDK 21 on `PATH`.
- `curl` (always present). `jq` optional — only makes JSON output prettier.

## Quickstart

```bash
./scripts/up.sh        # build jars, start Postgres+Kafka, start the 3 services, wait for health
./scripts/demo.sh all  # run every flow end-to-end with narrated output
./scripts/down.sh      # stop everything (add --purge to also wipe the DB volume)
```

Run a single flow:

```bash
./scripts/demo.sh accrual      # Flow 1 — transactional outbox + idempotency
./scripts/demo.sh redeem       # Flow 2 — saga happy path (reserve→fulfil→commit)
./scripts/demo.sh compensate   # Flow 2b — saga compensation (partner FAIL → release hold)
./scripts/demo.sh strangler    # Flow 3 — config-driven sticky routing legacy vs new
./scripts/demo.sh shadow       # Flow 3b — shadow-compare (serve legacy, log mismatch vs new)
```

## What runs where

| Service | URL | Notes |
|---|---|---|
| Core (business+domain) | http://localhost:8080 | Swagger UI at `/swagger-ui`, health at `/actuator/health` |
| Legacy SOAP service | http://localhost:8081 | WSDL at `/ws/tiers.wsdl` |
| Experience / edge | http://localhost:8082 | The strangler facade |
| Postgres | localhost:5433 | host port; overridable via `SKYWARD_POSTGRES_PORT` |
| Kafka (KRaft) | localhost:9092 | |

Logs: `scripts/.run/logs/{core,legacy,experience}.log` (e.g. `tail -f scripts/.run/logs/*.log`).

## Useful overrides (env vars)

```bash
SKYWARD_POSTGRES_PORT=5544 ./scripts/up.sh      # if 5433 is taken too
STRANGLER_LEGACY_PERCENT=100 ./scripts/up.sh    # start the edge routing 100% to legacy
PARTNER_MODE=FAIL ./scripts/up.sh               # start core so every redemption compensates
```

`PARTNER_MODE` values: `SUCCEED` (default), `FAIL`, `SLOW`, `TIMEOUT_BUT_FULFILS`, `FAIL_THEN_SUCCEED`.

---

## The flows by hand (what the script does, so you can narrate it)

> `$CORE=http://localhost:8080  $LEGACY=http://localhost:8081  $EDGE=http://localhost:8082`

### Flow 1 — Accrual (transactional outbox)
```bash
# 1. enrol a SILVER member -> note the "id"
curl -s -X POST $CORE/members -H 'Content-Type: application/json' \
  -d '{"fullName":"Ada","tier":"SILVER"}'

# 2. accrue 1000 base points from a HOTEL partner.
#    SILVER ×1.25 and the hotel campaign ×2.0  ⇒  2500 points.
curl -s -X POST $CORE/accruals -H 'Content-Type: application/json' \
  -d '{"memberId":"<ID>","basePoints":1000,"source":"partner:hotel:HILTON","idempotencyKey":"k1"}'

# 3. balance is async (ledger+outbox → relay → Kafka → projection). Poll until it shows 2500:
curl -s $CORE/members/<ID>/balance

# 4. replay step 2 with the SAME idempotencyKey "k1" → status DUPLICATE, balance stays 2500.
```

### Flow 2 — Redemption saga (happy path)
```bash
# member with balance (GOLD ×1.5 on a flight accrual = 1500), then:
curl -s -X POST $CORE/redemptions -H 'Content-Type: application/json' \
  -d '{"memberId":"<ID>","rewardCode":"FLIGHT_UPGRADE","points":600,"idempotencyKey":"r1"}'
#   -> status COMPLETED, partnerReference set; balance drops to 900.
# replay with the same key "r1" → same saga, no second burn.
```

### Flow 2b — Compensation
Start (or restart) the core in FAIL mode, then redeem:
```bash
PARTNER_MODE=FAIL ./scripts/up.sh
curl -s -X POST $CORE/redemptions -H 'Content-Type: application/json' \
  -d '{"memberId":"<ID>","rewardCode":"FLIGHT_UPGRADE","points":600,"idempotencyKey":"r2"}'
#   -> status COMPENSATED (hold released); balance UNCHANGED (no burn).
```

### Flow 3 — Strangler routing
```bash
# enrol a member as GOLD in core, then seed the SAME id in legacy as BRONZE (deliberate drift):
curl -s -X PUT $LEGACY/admin/members/<ID>/tier -H 'Content-Type: application/json' -d '{"tier":"BRONZE"}'

# call the edge — "source" tells you who answered (legacy⇒BRONZE, domain⇒GOLD), sticky per member:
curl -s $EDGE/members/<ID>/tier
```

### Flow 3b — Shadow compare
Run the edge in shadow mode (the script flips it for you); it serves the **authoritative** legacy answer
and logs where the new path disagrees:
```bash
SKYWARD_STRANGLER_SHADOW_ENABLED=true ./scripts/up.sh   # (or just: ./scripts/demo.sh shadow)
curl -s $EDGE/members/<ID>/tier        # served = legacy (BRONZE)
grep "shadow mismatch" scripts/.run/logs/experience.log   # legacy BRONZE vs domain GOLD
```

## Troubleshooting
- **Port already in use** — override `SKYWARD_POSTGRES_PORT` / `CORE_PORT` / `LEGACY_PORT` / `EXPERIENCE_PORT`.
- **A service won't start** — read `scripts/.run/logs/<service>.log`.
- **Stale DB** — `./scripts/down.sh --purge` then `./scripts/up.sh` for a fresh schema.
