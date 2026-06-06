package com.skyward.accrual;

/** Outcome of an accrual request. */
public enum AccrualStatus {
    /** A new ledger entry + outbox event were written. */
    ACCRUED,
    /** The idempotency key was already processed; nothing new was written. */
    DUPLICATE
}
