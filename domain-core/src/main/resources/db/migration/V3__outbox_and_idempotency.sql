-- V3 (Day 2.1): the transactional outbox + accrual idempotency.

-- Idempotency: an accrual carries a key; a UNIQUE constraint makes a replayed accrual unable to
-- create a second ledger entry. The column is nullable, and Postgres UNIQUE permits multiple NULLs,
-- so non-accrual entries (seeds, burns) are unconstrained.
ALTER TABLE ledger_entry ADD COLUMN idempotency_key VARCHAR(200);
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_idempotency_key_uq UNIQUE (idempotency_key);

-- The outbox: one row per event, written in the SAME transaction as the ledger entry.
CREATE TABLE outbox_event (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

-- The relay only ever scans UNPUBLISHED rows, oldest first. A partial index keyed on created_at and
-- filtered to published_at IS NULL keeps that poll cheap and the index small (published rows drop out).
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
