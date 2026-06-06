package com.skyward.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Event emitted when points are burned by a committed redemption. Published via the outbox (same
 * mechanism as {@link PointsAccrued}) and consumed by the balance projection, which applies a negative
 * delta. {@code eventId} is the consumer idempotency key.
 */
public record PointsBurned(
        UUID eventId,
        UUID memberId,
        long points,
        UUID redemptionId,
        String rewardCode,
        OffsetDateTime occurredAt) {
}
