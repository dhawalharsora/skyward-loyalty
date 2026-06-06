# ADR 0001: Translate External Accrual Events in Adapters

## Status

Proposed

## Context

The Skyward platform needs to award loyalty points from multiple aviation and partner sources:

- flown flight segments
- hotel/car partner stays
- credit-card transactions
- retro-claim workflows
- manual/admin corrections

Those sources do not share one canonical event shape. A `FlightFlown` event, a hotel stay feed, and a
card transaction all carry different identifiers, eligibility rules, idempotency keys, and failure
semantics.

The current core accrual API is `POST /accruals`. It represents a normalized business command:

```text
Award points to this member for this source activity.
```

It is not intended to be a raw external event ingestion endpoint.

## Decision

External source events will be consumed in the adapter layer, translated into a normalized accrual
command, and then sent to the business/core service.

Conceptually:

```text
Source system / partner feed
        |
        v
Kafka topic / webhook / batch file
        |
        v
Adapter layer
        |
        v
POST /accruals
        |
        v
Ledger entry + outbox event in one transaction
```

The domain/business core will not subscribe directly to every external aviation or partner topic.

The adapter owns:

- source-specific payload parsing
- source-specific idempotency key construction
- partner/source authentication details
- mapping raw events into `AccrualRequest`
- retry/DLQ behavior for ingestion failures

The business/core service owns:

- earning rule application
- member/tier lookup
- append-only ledger writes
- duplicate accrual protection
- transactional outbox creation

## Rationale

This keeps source-specific integration complexity outside the core loyalty domain. Airline ecosystems
usually have many event producers with different contracts: PSS, DCS, partner platforms, card issuers,
settlement systems, and operational feeds. Letting the core consume all of those directly would couple
the loyalty ledger to external schemas and partner churn.

The normalized accrual command gives the business layer one stable contract while still allowing each
adapter to handle the messy details of its source.

## Alternatives Considered

### Core consumes all external Kafka topics directly

This would reduce one network hop, but it would make the business/core service responsible for
source-specific schemas, topic contracts, parsing, and ingestion failure modes. That weakens the layer
boundary and makes the core harder to evolve.

### One global `loyalty.accrual.requested` topic only

This can be useful as an internal normalized event, but it should not erase the need for source-specific
adapters. External systems still need translation, eligibility checks, idempotency key strategy, and
dead-letter handling before a command is safe to process.

### Partners call `POST /accruals` directly

This is acceptable for a trusted internal producer or a thin demo, but it is risky as a general design.
Partners typically need authentication, schema validation, throttling, replay handling, and contract
isolation. Those concerns belong at the adapter/edge boundary.

## Consequences

Positive:

- The core accrual flow stays stable even as partner/source formats change.
- Idempotency keys can be tailored per source, such as flight coupon id, stay id, or card transaction id.
- Adapter failures can be isolated with retries and dead-letter topics.
- The business/core service remains focused on loyalty rules, ledger integrity, and outbox safety.

Negative:

- There is an extra service boundary and network hop.
- Adapter-to-core retries must be designed carefully to avoid duplicate accrual attempts.
- The normalized command contract must be versioned and documented.

## Follow-Up Questions

- Should adapters call `POST /accruals`, publish an internal `loyalty.accrual.requested` command topic,
  or support both paths?
- Where should eligibility rules live when they are source-specific but business-owned?
- What is the canonical idempotency key for a flown flight segment: ticket number, coupon number, flight
  instance, member id, or a composite?
- What timeout/retry policy should the adapter use when the core accepts the accrual but the adapter does
  not receive the response?
- Should raw source events be stored for audit/reconciliation before translation?
