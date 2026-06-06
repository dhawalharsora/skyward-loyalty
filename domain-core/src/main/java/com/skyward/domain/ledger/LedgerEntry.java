package com.skyward.domain.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * An immutable, append-only entry in the points ledger.
 *
 * <p>There are intentionally no setters and no public mutators: once written, a ledger entry never
 * changes. A member's balance is derived by summing their entries (see
 * {@code LedgerEntryRepository#balanceOf}). The {@code amount} is always a positive magnitude; the
 * {@link EntryType} carries the direction. Members are referenced by id (not a JPA association) so the
 * ledger stays an independent aggregate; the foreign key is still enforced in the database.
 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private EntryType entryType;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "source", length = 200)
    private String source;

    /**
     * Optional idempotency key. For accrual-created entries it carries the partner's idempotency key and
     * has a UNIQUE constraint, so a replayed accrual cannot create a second entry. Non-accrual entries
     * (e.g. seeds, future burns) leave it null — Postgres UNIQUE permits multiple NULLs.
     */
    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected LedgerEntry() {
        // for JPA only
    }

    private LedgerEntry(UUID id, UUID memberId, EntryType entryType, long amount, String source,
            String idempotencyKey, OffsetDateTime createdAt) {
        this.id = id;
        this.memberId = memberId;
        this.entryType = entryType;
        this.amount = amount;
        this.source = source;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    /** Creates an EARN entry with no idempotency key (seeds / non-accrual use). */
    public static LedgerEntry earn(UUID memberId, long amount, String source) {
        return create(memberId, EntryType.EARN, amount, source, null);
    }

    /** Creates an EARN entry carrying an idempotency key (accrual path). */
    public static LedgerEntry earn(UUID memberId, long amount, String source, String idempotencyKey) {
        return create(memberId, EntryType.EARN, amount, source, idempotencyKey);
    }

    /** Creates a BURN entry debiting {@code amount} points from a member. */
    public static LedgerEntry burn(UUID memberId, long amount, String source) {
        return create(memberId, EntryType.BURN, amount, source, null);
    }

    /** Creates a BURN entry carrying an idempotency key (redemption commit path). */
    public static LedgerEntry burn(UUID memberId, long amount, String source, String idempotencyKey) {
        return create(memberId, EntryType.BURN, amount, source, idempotencyKey);
    }

    private static LedgerEntry create(UUID memberId, EntryType type, long amount, String source,
            String idempotencyKey) {
        if (amount <= 0) {
            // Defend the invariant in code too, not only via the DB CHECK constraint.
            throw new IllegalArgumentException("ledger amount must be positive, was " + amount);
        }
        return new LedgerEntry(UUID.randomUUID(), memberId, type, amount, source, idempotencyKey,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public long getAmount() {
        return amount;
    }

    public String getSource() {
        return source;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
