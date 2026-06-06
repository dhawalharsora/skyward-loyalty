package com.skyward.domain.member;

/**
 * Loyalty tiers and their points earn-multipliers.
 *
 * <p>Modelled as an enum (not a reference table) deliberately: tiers and their multipliers are stable
 * reference data, and an enum gives compile-time safety everywhere a tier is used. The trade-off is
 * that changing a multiplier requires a redeploy; if the business ever needs to tune multipliers at
 * runtime (e.g. a temporary "double status" promotion), this becomes a config-driven reference table.
 *
 * <p>The multiplier is applied by the earning-rules engine on accrual (Day 2), not here.
 */
public enum Tier {
    BRONZE(1.0),
    SILVER(1.25),
    GOLD(1.5),
    PLATINUM(2.0);

    private final double earnMultiplier;

    Tier(double earnMultiplier) {
        this.earnMultiplier = earnMultiplier;
    }

    public double earnMultiplier() {
        return earnMultiplier;
    }
}
