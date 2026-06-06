package com.skyward.common.event;

/**
 * Kafka topic names, kept in {@code common} so producers (the outbox relay) and consumers (the balance
 * projection) reference one constant and can never drift out of sync.
 */
public final class Topics {

    private Topics() {
    }

    /** PointsAccrued events published from the transactional outbox. */
    public static final String POINTS_ACCRUED = "points.accrued";

    /** Dead-letter topic for PointsAccrued messages that fail processing (default ".DLT" suffix). */
    public static final String POINTS_ACCRUED_DLT = "points.accrued.DLT";

    /** PointsBurned events published from the transactional outbox (committed redemptions). */
    public static final String POINTS_BURNED = "points.burned";

    /** Dead-letter topic for PointsBurned messages that fail processing. */
    public static final String POINTS_BURNED_DLT = "points.burned.DLT";
}
