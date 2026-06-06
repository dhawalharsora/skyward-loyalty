package com.skyward.redemption;

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
 * Persisted state of a redemption saga. Mutable: the orchestrator transitions it through its lifecycle
 * and the row survives a restart, so an interrupted saga can be resumed. State transitions are
 * expressed as methods (reserved/failed factories; fulfil/complete/compensate transitions).
 */
@Entity
@Table(name = "redemption")
public class Redemption {

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "reward_code", nullable = false, length = 100)
    private String rewardCode;

    @Column(name = "points", nullable = false)
    private long points;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RedemptionStatus status;

    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    @Column(name = "partner_reference", length = 200)
    private String partnerReference;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Redemption() {
        // for JPA only
    }

    private Redemption(UUID id, UUID memberId, String rewardCode, long points, RedemptionStatus status,
            String idempotencyKey, String failureReason, OffsetDateTime now) {
        this.id = id;
        this.memberId = memberId;
        this.rewardCode = rewardCode;
        this.points = points;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.failureReason = failureReason;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A new RESERVED redemption (the hold is placed). */
    public static Redemption reserved(UUID memberId, String rewardCode, long points, String idempotencyKey) {
        return new Redemption(UUID.randomUUID(), memberId, rewardCode, points,
                RedemptionStatus.RESERVED, idempotencyKey, null, now());
    }

    /** A redemption that could not be reserved (e.g. insufficient balance). */
    public static Redemption failed(UUID memberId, String rewardCode, long points,
            String idempotencyKey, String reason) {
        return new Redemption(UUID.randomUUID(), memberId, rewardCode, points,
                RedemptionStatus.FAILED, idempotencyKey, reason, now());
    }

    /** Partner confirmed fulfilment; record the partner reference. */
    public void markFulfilled(String partnerReference) {
        this.status = RedemptionStatus.FULFILLED;
        this.partnerReference = partnerReference;
        touch();
    }

    /** Points burned; the saga is done. */
    public void markCompleted() {
        this.status = RedemptionStatus.COMPLETED;
        touch();
    }

    /** Fulfilment failed; the hold is released without burning points. */
    public void markCompensated(String reason) {
        this.status = RedemptionStatus.COMPENSATED;
        this.failureReason = reason;
        touch();
    }

    private void touch() {
        this.updatedAt = now();
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public String getRewardCode() {
        return rewardCode;
    }

    public long getPoints() {
        return points;
    }

    public RedemptionStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getPartnerReference() {
        return partnerReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
