package com.skyward.redemption;

/**
 * The redemption saga's state machine.
 *
 * <pre>
 *   RESERVED ──fulfil ok──▶ FULFILLED ──burn──▶ COMPLETED   (happy path)
 *   RESERVED ──fulfil fail─────────────────────▶ COMPENSATED (compensation path)
 *   (reserve rejected) ─────────────────────────▶ FAILED      (insufficient balance)
 * </pre>
 *
 * RESERVED and FULFILLED are "active holds" that reduce the member's available balance.
 */
public enum RedemptionStatus {
    RESERVED,
    FULFILLED,
    COMPLETED,
    COMPENSATED,
    FAILED
}
