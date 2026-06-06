package com.skyward.accrual;

import java.util.UUID;

/** Result of an accrual: the outcome, the member, the points credited, and the ledger entry id. */
public record AccrualResponse(
        AccrualStatus status,
        UUID memberId,
        long earnedPoints,
        UUID ledgerEntryId) {
}
