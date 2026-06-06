package com.skyward.domain.balance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Materialized balance read-model: one mutable row per member, maintained by the projection consumer.
 *
 * <p>Deliberately the opposite of the append-only ledger — this row is overwritten as events arrive.
 * That is safe because it is <em>derived</em>: it can be rebuilt at any time by summing the ledger
 * (the source of truth). Reads are O(1) here instead of an aggregation over the ledger.
 */
@Entity
@Table(name = "member_balance")
public class MemberBalance {

    @Id
    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "balance", nullable = false)
    private long balance;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MemberBalance() {
        // for JPA only
    }

    private MemberBalance(UUID memberId, long balance, OffsetDateTime updatedAt) {
        this.memberId = memberId;
        this.balance = balance;
        this.updatedAt = updatedAt;
    }

    /** A fresh zero balance for a member. */
    public static MemberBalance zero(UUID memberId) {
        return new MemberBalance(memberId, 0L, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** Applies a points delta (positive for earn, negative for burn) and bumps the timestamp. */
    public void add(long points) {
        this.balance += points;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getMemberId() {
        return memberId;
    }

    public long getBalance() {
        return balance;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
