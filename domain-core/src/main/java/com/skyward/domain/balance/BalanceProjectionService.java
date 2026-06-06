package com.skyward.domain.balance;

import com.skyward.domain.inbox.ProcessedEvent;
import com.skyward.domain.inbox.ProcessedEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the materialized {@code member_balance} read-model. The apply is <b>idempotent</b>: in one
 * transaction it records the event id in the inbox and updates the balance, so a redelivered event
 * (the relay is at-least-once) is detected and skipped. This is a pure domain operation — it knows
 * nothing about Kafka; the consumer (an inbound adapter in business-api) calls it.
 */
@Service
public class BalanceProjectionService {

    private final ProcessedEventRepository processedEvents;
    private final MemberBalanceRepository balances;

    public BalanceProjectionService(ProcessedEventRepository processedEvents,
            MemberBalanceRepository balances) {
        this.processedEvents = processedEvents;
        this.balances = balances;
    }

    /** Apply an accrual: credits points to the balance. */
    @Transactional
    public void applyAccrued(UUID eventId, UUID memberId, long points) {
        applyDelta(eventId, memberId, points);
    }

    /** Apply a burn: debits points from the balance. */
    @Transactional
    public void applyBurned(UUID eventId, UUID memberId, long points) {
        applyDelta(eventId, memberId, -points);
    }

    private void applyDelta(UUID eventId, UUID memberId, long delta) {
        if (processedEvents.existsById(eventId)) {
            return; // already applied — at-least-once dedupe
        }
        processedEvents.save(ProcessedEvent.of(eventId));

        MemberBalance balance = balances.findById(memberId).orElseGet(() -> MemberBalance.zero(memberId));
        balance.add(delta);
        balances.save(balance);
    }
}
