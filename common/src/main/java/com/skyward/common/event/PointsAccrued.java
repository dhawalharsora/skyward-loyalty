package com.skyward.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Event emitted when points are accrued to a member. This is the contract published to Kafka (via the
 * outbox) and consumed by the balance-projection consumer. Lives in {@code common} because it is shared
 * across modules/services. An immutable record — the project favours records for events and DTOs.
 *
 * @param eventId     unique id for this event (also the idempotency key for consumers)
 * @param memberId    the member credited
 * @param points      the final points credited (after tier multiplier + campaigns)
 * @param basePoints  the raw points before rules were applied
 * @param tier        the member's tier at accrual time
 * @param source      the activity source (e.g. "flight:SY123", "partner:hotel:HILTON")
 * @param occurredAt  when the accrual happened (UTC)
 */
public record PointsAccrued(
        UUID eventId,
        UUID memberId,
        long points,
        long basePoints,
        String tier,
        String source,
        OffsetDateTime occurredAt) {
}
