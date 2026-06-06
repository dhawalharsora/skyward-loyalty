package com.skyward.common.partner;

import java.util.UUID;

/**
 * Request to fulfil a reward with a partner. {@code redemptionId} doubles as the partner-side
 * idempotency key, so a retried fulfilment for the same redemption is not double-issued.
 */
public record FulfilmentRequest(UUID redemptionId, UUID memberId, String rewardCode, long points) {
}
