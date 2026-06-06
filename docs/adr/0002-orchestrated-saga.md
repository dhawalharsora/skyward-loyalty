# ADR-0002: Orchestrated saga (over choreography) for redemption

- **Status:** Accepted
- **Date:** 2026-06-04
- **Context:** Day 3 — redemption

## Context

Redeeming points spans multiple steps with no shared transaction: reserve a hold, fulfil with an
external partner, then burn the points (or release the hold on failure). We need consistency without a
distributed transaction, plus crash-safety and a clean compensation story.

## Decision

Implement redemption as an **orchestrated saga**: a single orchestrator (`RedemptionOrchestrator`)
drives a sequence of **local transactions** — reserve → fulfil → commit/compensate — with the saga
state persisted in a `redemption` row (a state machine: RESERVED → FULFILLED → COMPLETED, or
COMPENSATED, or FAILED).

Key choices:
- **Orchestration, not choreography.** One component owns the flow and the state, so the logic is in one
  readable place and the current state is queryable.
- **Strongly-consistent reservation.** Availability = ledger balance (synchronous truth) − in-flight
  holds, guarded by a per-member `SELECT … FOR UPDATE` lock to prevent double-spend.
- **Commit reuses the transactional outbox.** It writes a BURN ledger entry + a `PointsBurned` outbox
  event in one transaction; the existing relay/consumer apply it to the materialized balance.
- **Resilience4j** (timeout + retry + circuit breaker) wraps the partner call.
- **Definite vs indeterminate failure.** A partner rejection is definite → compensate. A timeout/open
  circuit is indeterminate → never compensate blindly; leave RESERVED for recovery.
- **Idempotent fulfilment** keyed by `redemptionId`, so retry/recovery cannot double-issue.
- **Restart recovery.** A scheduled task resumes sagas stuck in RESERVED/FULFILLED.

## Consequences

**Positive**
- Clear, centralised flow and state; easy to reason about and to demo (a flaky-partner toggle forces the
  compensation path).
- No double-spend (lock + strong-consistency check), no lost reward / no double-issue (idempotent
  fulfil + don't-compensate-on-indeterminate), survives restart (recovery).

**Negative**
- The orchestrator is a focal point of coupling (it knows every step). At large scale, choreography
  decentralises that — at the cost of harder-to-trace, emergent flow.
- A persistently-indeterminate partner leaves a saga RESERVED (hold held) until recovery resolves it —
  acceptable (points are held, never lost or double-spent), but it is eventual.

## Alternatives considered

- **Choreography (events only, no central orchestrator)** — more decoupled and scalable, but the flow
  becomes emergent across services, harder to follow/debug, and compensation logic is scattered. For a
  single well-defined business transaction, orchestration is clearer.
- **One distributed transaction (2PC/XA across DB + partner)** — partners don't support it; XA is
  operationally painful and couples availability.
- **Compensate immediately on any failure** — rejected: it loses the reward when a timed-out call
  actually fulfilled (give-reward-and-keep-points). Hence the definite/indeterminate split.
