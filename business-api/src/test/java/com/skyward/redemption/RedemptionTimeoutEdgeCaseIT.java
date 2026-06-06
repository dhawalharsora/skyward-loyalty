package com.skyward.redemption;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyward.adapter.partner.FlakyPartnerStub;
import com.skyward.domain.ledger.LedgerEntry;
import com.skyward.domain.ledger.LedgerEntryRepository;
import com.skyward.domain.member.Member;
import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.member.Tier;
import com.skyward.support.AbstractIntegrationTest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The dangerous saga edge case: the partner fulfils, but our call times out. We must never end up
 * having given the reward AND kept the points (a blind compensation). The safety invariant is that a
 * timed-out-but-fulfilled redemption is never COMPENSATED; the idempotent partner (keyed by redemption
 * id) then lets retry/recovery complete it with the reward issued exactly once.
 */
@SpringBootTest
class RedemptionTimeoutEdgeCaseIT extends AbstractIntegrationTest {

    @Autowired
    private RedemptionOrchestrator orchestrator;

    @Autowired
    private RedemptionRecoveryService recovery;

    @Autowired
    private RedemptionRepository redemptions;

    @Autowired
    private MemberRepository members;

    @Autowired
    private LedgerEntryRepository ledger;

    @Autowired
    private FlakyPartnerStub partner;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        partner.reset();
        partner.setMode(FlakyPartnerStub.Mode.SUCCEED);
        circuitBreakerRegistry.circuitBreaker("partnerFulfilment").reset();
    }

    private Member memberWithBalance(long points) {
        Member member = members.save(Member.enrol("Ada Lovelace", Tier.GOLD));
        ledger.save(LedgerEntry.earn(member.getId(), points, "seed"));
        return member;
    }

    @Test
    void timeoutAfterFulfilmentIsNeverCompensatedAndCompletesExactlyOnce() {
        Member member = memberWithBalance(1_000);
        // The partner records the fulfilment, THEN stalls — so the call times out though the reward
        // was issued.
        partner.setMode(FlakyPartnerStub.Mode.TIMEOUT_BUT_FULFILS);
        partner.setLatencyMs(2_000);

        Redemption afterRedeem =
                orchestrator.redeem(member.getId(), "FLIGHT_UPGRADE", 600, "edge-1");

        // SAFETY INVARIANT: a possibly-fulfilled redemption must never be compensated.
        assertThat(afterRedeem.getStatus()).isNotEqualTo(RedemptionStatus.COMPENSATED);

        // Recovery (idempotent re-fulfil) drives any still-RESERVED saga to completion.
        recovery.recoverStuckSagas(Duration.ZERO);

        Redemption resolved = redemptions.findById(afterRedeem.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(RedemptionStatus.COMPLETED);
        // For THIS member, points were burned exactly once (1000 - 600). A double-issue/double-burn
        // would show a different balance. (A global stub counter is unreliable here: recovery also
        // resolves other tests' leftover sagas in the shared test database.)
        assertThat(ledger.balanceOf(member.getId())).isEqualTo(400L);
        assertThat(ledger.findByMemberIdOrderByCreatedAtAsc(member.getId()))
                .filteredOn(e -> e.getEntryType() == com.skyward.domain.ledger.EntryType.BURN)
                .hasSize(1);
    }
}
