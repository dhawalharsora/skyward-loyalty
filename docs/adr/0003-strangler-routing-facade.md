# ADR-0003: Strangler routing facade for tier reads (shadow → percentage cutover)

- **Status:** Accepted
- **Date:** 2026-06-05
- **Context:** Day 4 — strangler fig (tier-status modernisation)

## Context

Member *tier* is served by a legacy SOAP service (the "old SOA") and, now, by a new domain REST service.
We need to migrate reads of `GET /members/{id}/tier` from old to new **incrementally and reversibly**,
without a big-bang cutover and without callers having to know a migration is happening.

## Decision

Put a **strangler routing facade** in the Experience layer (`experience-api`, a bootable edge service)
that fronts both backends behind one REST endpoint and normalizes both into a single `TierView`. The
backend that answered is exposed as `source` so the cutover is observable.

The facade runs in one of two **config-selected migration modes**:

1. **Shadow-compare (pre-cutover).** Call *both* paths, serve the configured **authoritative** one
   (legacy, initially), and report mismatches. The new path is exercised on live traffic but never
   served, so disagreements surface safely. The shadow call is **best-effort**: its failure is recorded,
   never propagated. Mismatch reporting is behind a `ShadowComparisonListener` seam (logging today; a
   mismatch-rate metric/alert is the natural next step).
2. **Percentage routing (cutover).** A config-driven percentage shifts traffic to the new path. Routing
   is **sticky per member**: `floorMod(memberId.hashCode(), 100) < legacyPercent` → legacy, else domain.

Both modes are toggled by config alone (`skyward.strangler.*`) — no redeploy to advance or roll back.

Other choices:
- **Routing lives at the edge**, not in the domain service — it is a traffic/edge concern, not a domain
  rule. The domain service stays oblivious to the migration.
- **The legacy service is an independent system of record** (its own in-memory store), so it *can*
  disagree with the new path — which is the entire point of shadow-compare.
- **The edge owns its own SOAP client bindings** (as if generated from the published WSDL), coupling the
  Experience layer to the *contract*, not to the legacy deployable.
- **Each path's not-found is normalized to a REST 404** (legacy SOAP *client fault* → 404; domain REST
  404 → 404), so callers get one consistent contract regardless of path.

## Consequences

**Positive**
- Incremental, **reversible** cutover with a per-environment/runtime dial; instant rollback by lowering
  the percentage.
- **Sticky routing** gives a consistent member experience (no flapping between systems), cohort-based
  rollout/rollback, and reproducibility ("member X is on the new path").
- **Shadow-compare de-risks** the cutover: real divergence is found on live traffic before any user is
  served the new answer.
- Callers are insulated — one endpoint, one shape, one not-found contract; `source` makes the migration
  observable without changing the contract.

**Negative**
- Sticky routing can't load-split a *single* member across paths (fine for a read; cohort-level is the
  right granularity here).
- Synchronous shadow doubles downstream calls for shadowed traffic; best-effort isolation caps the blast
  radius, but production would sample and/or run the shadow call asynchronously.
- A persistent tier *disagreement* between systems is only surfaced (logged), not auto-reconciled — by
  design; reconciliation is a separate migration workstream.

## Alternatives considered

- **Per-request random sampling** instead of sticky hash — hits the same population percentage but loses
  consistency (a member flaps between systems request to request), clean cohort rollback, and
  reproducibility. Rejected for a stateful migration.
- **Routing inside the domain service** — leaks a migration/traffic concern into the domain and couples
  it to the legacy system; rejected. The edge is the correct seam.
- **Big-bang cutover (flip 100% at once)** — no safety net, no shadow validation, painful rollback.
- **Sharing the legacy server's binding classes / a shared DB** — would couple the edge to the legacy
  deployable and make shadow-compare a no-op (the two could never disagree).
