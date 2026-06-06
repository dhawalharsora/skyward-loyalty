-- V5 (Day 3.1): redemption saga state.
--
-- A redemption is an orchestrated saga; this row IS its persisted state machine. The status encodes
-- progress: RESERVED (hold placed) -> FULFILLED (partner confirmed) -> COMPLETED (points burned), or
-- COMPENSATED (hold released, no burn), or FAILED (could not reserve). A row in RESERVED/FULFILLED is
-- an active "hold" that reduces the member's available balance.
--
-- (Migrations for the whole core database live here in domain-core for a single linear history, even
-- though the Redemption entity is owned by the business-api saga orchestrator.)

CREATE TABLE redemption (
    id                UUID         PRIMARY KEY,
    member_id         UUID         NOT NULL REFERENCES member (id),
    reward_code       VARCHAR(100) NOT NULL,
    points            BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    idempotency_key   VARCHAR(200) UNIQUE,
    partner_reference VARCHAR(200),
    failure_reason    VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT redemption_points_chk CHECK (points > 0),
    CONSTRAINT redemption_status_chk CHECK (status IN
        ('RESERVED', 'FULFILLED', 'COMPLETED', 'COMPENSATED', 'FAILED'))
);

-- Availability check sums active holds per member; recovery scans by status.
CREATE INDEX idx_redemption_member_status ON redemption (member_id, status);
CREATE INDEX idx_redemption_status ON redemption (status);
