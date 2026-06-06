-- V2 (Day 1): members, their tier, and the append-only points ledger.
--
-- Design notes:
--  * The ledger is APPEND-ONLY. We never UPDATE or DELETE a ledger_entry row. A member's balance is
--    a projection derived from these rows (SUM of earns minus burns), never a stored mutable column.
--  * amount is always a POSITIVE magnitude; direction is carried by entry_type. The CHECK constraints
--    make an invalid row (negative amount, or an unknown type) impossible at the database level.

CREATE TABLE member (
    id          UUID         PRIMARY KEY,
    full_name   VARCHAR(200) NOT NULL,
    tier        VARCHAR(20)  NOT NULL,
    enrolled_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT member_tier_chk CHECK (tier IN ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM'))
);

CREATE TABLE ledger_entry (
    id         UUID         PRIMARY KEY,
    member_id  UUID         NOT NULL REFERENCES member (id),
    entry_type VARCHAR(10)  NOT NULL,
    amount     BIGINT       NOT NULL,
    source     VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ledger_entry_type_chk   CHECK (entry_type IN ('EARN', 'BURN')),
    CONSTRAINT ledger_entry_amount_chk CHECK (amount > 0)
);

-- Balance is computed by summing a member's entries, so we always filter by member_id.
CREATE INDEX idx_ledger_entry_member_id ON ledger_entry (member_id);
