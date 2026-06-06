package com.skyward.redemption;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyward.common.event.PointsBurned;
import com.skyward.domain.ledger.LedgerEntry;
import com.skyward.domain.ledger.LedgerEntryRepository;
import com.skyward.domain.member.MemberNotFoundException;
import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.outbox.OutboxEvent;
import com.skyward.domain.outbox.OutboxEventRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists each saga step as its own local transaction (the orchestrator coordinates them). Reserve
 * places a hold; markFulfilled records the partner reference; commit burns the points; compensate
 * releases the hold. Steps are idempotent so they are safe to retry / replay after a restart.
 *
 * <p>Reservation is strongly consistent: available = ledger balance (synchronous source of truth)
 * minus in-flight holds, guarded by a per-member pessimistic lock to prevent double-spend.
 */
@Service
public class RedemptionService {

    /** Redemptions in these states hold points against the member's balance. */
    private static final List<RedemptionStatus> ACTIVE_HOLDS =
            List.of(RedemptionStatus.RESERVED, RedemptionStatus.FULFILLED);

    private final RedemptionRepository redemptions;
    private final MemberRepository members;
    private final LedgerEntryRepository ledger;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public RedemptionService(RedemptionRepository redemptions, MemberRepository members,
            LedgerEntryRepository ledger, OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.redemptions = redemptions;
        this.members = members;
        this.ledger = ledger;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Redemption reserve(UUID memberId, String rewardCode, long points, String idempotencyKey) {
        Optional<Redemption> existing = redemptions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        members.findAndLockById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));

        long balance = ledger.balanceOf(memberId);
        long held = redemptions.sumPointsByMemberIdAndStatusIn(memberId, ACTIVE_HOLDS);
        long available = balance - held;

        Redemption redemption = available >= points
                ? Redemption.reserved(memberId, rewardCode, points, idempotencyKey)
                : Redemption.failed(memberId, rewardCode, points, idempotencyKey,
                        "insufficient balance: available=" + available + ", requested=" + points);

        return redemptions.save(redemption);
    }

    @Transactional
    public Redemption markFulfilled(UUID redemptionId, String partnerReference) {
        Redemption redemption = load(redemptionId);
        redemption.markFulfilled(partnerReference);
        return redemptions.save(redemption);
    }

    /**
     * Commit: burn the points. Writes a BURN ledger entry AND a PointsBurned outbox event in one
     * transaction (the same dual-write/outbox pattern as accrual), then marks the saga COMPLETED.
     * Idempotent: a replayed commit short-circuits, and the BURN's idempotency key guards the ledger.
     */
    @Transactional
    public Redemption commit(UUID redemptionId) {
        Redemption redemption = load(redemptionId);
        if (redemption.getStatus() == RedemptionStatus.COMPLETED) {
            return redemption;
        }

        LedgerEntry burn = LedgerEntry.burn(
                redemption.getMemberId(),
                redemption.getPoints(),
                "redemption:" + redemption.getRewardCode(),
                "redemption:" + redemption.getId());
        ledger.save(burn);

        PointsBurned event = new PointsBurned(
                UUID.randomUUID(),
                redemption.getMemberId(),
                redemption.getPoints(),
                redemption.getId(),
                redemption.getRewardCode(),
                OffsetDateTime.now(ZoneOffset.UTC));
        outbox.save(OutboxEvent.pointsBurned(redemption.getMemberId(), toJson(event)));

        redemption.markCompleted();
        return redemptions.save(redemption);
    }

    /** Compensate: release the hold without burning points. Idempotent / terminal-safe. */
    @Transactional
    public Redemption compensate(UUID redemptionId, String reason) {
        Redemption redemption = load(redemptionId);
        if (redemption.getStatus() == RedemptionStatus.COMPENSATED
                || redemption.getStatus() == RedemptionStatus.COMPLETED) {
            return redemption;
        }
        redemption.markCompensated(reason);
        return redemptions.save(redemption);
    }

    private Redemption load(UUID redemptionId) {
        return redemptions.findById(redemptionId)
                .orElseThrow(() -> new IllegalStateException("redemption not found: " + redemptionId));
    }

    private String toJson(PointsBurned event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize PointsBurned event", e);
        }
    }
}
