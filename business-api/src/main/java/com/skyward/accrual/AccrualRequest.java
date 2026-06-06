package com.skyward.accrual;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Inbound accrual command. The {@code idempotencyKey} is mandatory: every accrual must be safely
 * retryable, because partner delivery is at-least-once.
 */
public record AccrualRequest(
        @NotNull UUID memberId,
        @Positive long basePoints,
        @NotBlank String source,
        @NotBlank String idempotencyKey) {
}
