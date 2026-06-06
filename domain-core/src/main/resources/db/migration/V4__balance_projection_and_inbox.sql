-- V4 (Day 2.3): the materialized balance read-model and the consumer inbox (dedupe).

-- member_balance is a MUTABLE projection (unlike the append-only ledger): the consumer overwrites it
-- as events arrive. It is derived and disposable — it can always be rebuilt from the ledger, which
-- remains the source of truth. Reads hit this O(1) row instead of summing the ledger.
CREATE TABLE member_balance (
    member_id  UUID        PRIMARY KEY REFERENCES member (id),
    balance    BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- processed_event is the "inbox": the consumer records each handled event id here, in the SAME
-- transaction as the balance update. A redelivered event (relay is at-least-once) is detected here
-- and skipped, making the consumer idempotent.
CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
