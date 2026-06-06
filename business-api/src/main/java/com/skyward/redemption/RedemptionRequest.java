package com.skyward.redemption;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** Inbound redemption command. The idempotency key makes the whole saga safely retryable. */
public record RedemptionRequest(
        @NotNull UUID memberId,
        @NotBlank String rewardCode,
        @Positive long points,
        @NotBlank String idempotencyKey) {
}
