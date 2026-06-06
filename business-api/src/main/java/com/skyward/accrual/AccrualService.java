package com.skyward.accrual;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyward.common.event.PointsAccrued;
import com.skyward.domain.ledger.LedgerEntry;
import com.skyward.domain.ledger.LedgerEntryRepository;
import com.skyward.domain.member.Member;
import com.skyward.domain.member.MemberNotFoundException;
import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.outbox.OutboxEvent;
import com.skyward.domain.outbox.OutboxEventRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for accrual. Orchestrates the earning rules and the <b>transactional outbox</b>
 * write: within a single transaction it writes the ledger entry AND the outbox event, so the event to
 * publish is committed atomically with the business change (no dual-write problem).
 *
 * <p>Idempotency: a fast-path lookup short-circuits a replayed accrual, and a UNIQUE constraint on the
 * ledger's idempotency key is the data-integrity backstop. (A truly concurrent double-submit would hit
 * that constraint and fail the second transaction — correct, since no double credit occurs; gracefully
 * returning DUPLICATE in that race is a future refinement.)
 */
@Service
public class AccrualService {

    private final MemberRepository members;
    private final LedgerEntryRepository ledger;
    private final OutboxEventRepository outbox;
    private final EarningRules earningRules;
    private final ObjectMapper objectMapper;

    public AccrualService(MemberRepository members, LedgerEntryRepository ledger,
            OutboxEventRepository outbox, EarningRules earningRules, ObjectMapper objectMapper) {
        this.members = members;
        this.ledger = ledger;
        this.outbox = outbox;
        this.earningRules = earningRules;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccrualResponse accrue(AccrualRequest request) {
        // Fast-path idempotency: if we've already recorded this key, return the original result.
        Optional<LedgerEntry> existing = ledger.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            LedgerEntry entry = existing.get();
            return new AccrualResponse(
                    AccrualStatus.DUPLICATE, entry.getMemberId(), entry.getAmount(), entry.getId());
        }

        Member member = members.findById(request.memberId())
                .orElseThrow(() -> new MemberNotFoundException(request.memberId()));

        long earned = earningRules.earnedPointsFor(member, request.basePoints(), request.source());

        // 1) the business change: an append-only ledger entry
        LedgerEntry entry = LedgerEntry.earn(
                member.getId(), earned, request.source(), request.idempotencyKey());
        ledger.save(entry);

        // 2) the event to publish, written in the SAME transaction
        PointsAccrued event = new PointsAccrued(
                UUID.randomUUID(),
                member.getId(),
                earned,
                request.basePoints(),
                member.getTier().name(),
                request.source(),
                OffsetDateTime.now(ZoneOffset.UTC));
        outbox.save(OutboxEvent.pointsAccrued(member.getId(), toJson(event)));

        return new AccrualResponse(AccrualStatus.ACCRUED, member.getId(), earned, entry.getId());
    }

    private String toJson(PointsAccrued event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Serialization failure must abort the transaction — never persist an unpublishable event.
            throw new IllegalStateException("Failed to serialize PointsAccrued event", e);
        }
    }
}
