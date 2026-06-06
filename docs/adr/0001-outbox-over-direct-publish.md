# ADR-0001: Transactional Outbox over direct publish

- **Status:** Accepted
- **Date:** 2026-06-04
- **Context:** Day 2 — event-driven accrual

## Context

An accrual must do two things: persist a business change (an append-only `ledger_entry`) **and**
publish a `PointsAccrued` event to Kafka so the rest of the system (the balance projection, future
consumers) can react. The database and Kafka are two separate systems with no shared transaction.

The naive approach — commit to the DB, then publish to Kafka — is the **dual-write problem**: a crash
between the two steps leaves them inconsistent.
- Commit DB, **then** crash before publish → points recorded, event never sent → **lost event**.
- Publish, **then** crash before commit → event sent for a change that rolled back → **phantom event**.

There is no ordering of two independent commits that is crash-safe.

## Decision

Use the **Transactional Outbox**. In one local DB transaction we write the `ledger_entry` **and** an
`outbox_event` row. A separate **relay** polls unpublished outbox rows with
`SELECT … FOR UPDATE SKIP LOCKED`, publishes each to Kafka synchronously (`acks=all`), then marks the
row published — all within a transaction. Consumers dedupe on event id (an inbox table) because the
relay is at-least-once.

Component placement (a deliberate, debated choice):
- **Outbox table** → `domain-core`. It must share the domain's transaction; no alternative.
- **Relay** → `domain-core` (`outbox.relay` package), fenced as the infrastructure corner of the domain
  module. It is tightly coupled to the outbox table it serves.
- **Consumer** → `business-api` (an inbound adapter), to keep Kafka subscription wiring out of the
  domain. The idempotent *apply* logic and the read-model live in `domain-core`.

## Consequences

**Positive**
- No lost or phantom events: the event is committed atomically with the business change.
- The relay's crash-safety is "publish then mark" → at-least-once, never lost. Duplicates are absorbed
  by idempotent consumers.
- `SKIP LOCKED` allows multiple relay instances with no contention and no double-publish.

**Negative / costs**
- Eventual consistency: the materialized balance lags the synchronous accrual response.
- The relay holds DB row locks across the Kafka publish (mitigated by small batches + send timeout;
  alternatives: batch the sends, or claim-then-publish-then-mark in separate transactions).
- Per-member ordering can be disturbed if multiple relay instances publish the same member's events out
  of order; mitigation is to shard the poll by `member_id` hash so each member is owned by one relay.

## Alternatives considered

- **Direct publish after commit** — rejected: the dual-write problem (above).
- **Kafka transactions / XA 2PC across DB + Kafka** — rejected: heavier, still typically needs
  idempotent consumers, and XA is operationally painful.
- **Log-based CDC (Debezium tailing the Postgres WAL)** — the **production-grade** evolution of this
  relay: no polling, no application-held locks, near-real-time. Not adopted now because it adds Kafka
  Connect + logical-replication-slot operations (a stuck slot can fill the disk). The polling relay is
  dependency-free, demonstrates the pattern with real SQL, and the seam (the outbox table) is identical,
  so swapping in CDC later is low-risk.
