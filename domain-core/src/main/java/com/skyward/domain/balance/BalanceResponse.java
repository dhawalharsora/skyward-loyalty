package com.skyward.domain.balance;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API response for a member's points balance. A record (immutable DTO) — the project favours records
 * for DTOs and events. {@code asOf} makes explicit that a derived balance is a point-in-time read.
 */
public record BalanceResponse(UUID memberId, long balance, OffsetDateTime asOf) {
}
