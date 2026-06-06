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
 * Restart recovery: a saga interrupted mid-flight (a process crash) is resumed by the recovery
 * scheduler and driven to a terminal state. Also covers the "don't compensate on an indeterminate
 * failure" rule: a timeout leaves the saga RESERVED (hold preserved) until recovery resolves it.
 */
@SpringBootTest
class RedemptionRecoveryIT extends AbstractIntegrationTest {

    @Autowired
    private RedemptionOrchestrator orchestrator;

    @Autowired
    private RedemptionService redemptionService;

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
    void recoveryResumesAReservedSagaThatNeverFulfilled() {
        Member member = memberWithBalance(1_000);
        // Simulate a crash right after reserve: the hold exists but fulfilment never ran.
        Redemption reserved = redemptionService.reserve(member.getId(), "FLIGHT_UPGRADE", 600, "rec-1");
        assertThat(reserved.getStatus()).isEqualTo(RedemptionStatus.RESERVED);

        partner.setMode(FlakyPartnerStub.Mode.SUCCEED);
        recovery.recoverStuckSagas(Duration.ZERO);

        assertThat(redemptions.findById(reserved.getId()).orElseThrow().getStatus())
                .isEqualTo(RedemptionStatus.COMPLETED);
        assertThat(ledger.balanceOf(member.getId())).isEqualTo(400L);
    }

    @Test
    void recoveryCommitsAFulfilledSagaThatNeverCommitted() {
        Member member = memberWithBalance(1_000);
        // Simulate a crash after fulfilment but before commit.
        Redemption reserved = redemptionService.reserve(member.getId(), "FLIGHT_UPGRADE", 600, "rec-2");
        redemptionService.markFulfilled(reserved.getId(), "PARTNER-REF-EXISTING");

        recovery.recoverStuckSagas(Duration.ZERO);

        assertThat(redemptions.findById(reserved.getId()).orElseThrow().getStatus())
                .isEqualTo(RedemptionStatus.COMPLETED);
        assertThat(ledger.balanceOf(member.getId())).isEqualTo(400L); // points burned on commit
    }

    @Test
    void indeterminateFailureLeavesSagaReservedThenRecoveryResolvesIt() {
        Member member = memberWithBalance(1_000);
        partner.setMode(FlakyPartnerStub.Mode.SLOW); // every attempt times out -> indeterminate
        partner.setLatencyMs(2_000);

        Redemption redemption =
                orchestrator.redeem(member.getId(), "FLIGHT_UPGRADE", 600, "rec-3");

        // Indeterminate outcome: NOT compensated, hold preserved, nothing burned.
        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.RESERVED);
        assertThat(ledger.balanceOf(member.getId())).isEqualTo(1_000L);

        // The partner recovers; recovery drives the saga to completion.
        partner.setMode(FlakyPartnerStub.Mode.SUCCEED);
        recovery.recoverStuckSagas(Duration.ZERO);

        assertThat(redemptions.findById(redemption.getId()).orElseThrow().getStatus())
                .isEqualTo(RedemptionStatus.COMPLETED);
        assertThat(ledger.balanceOf(member.getId())).isEqualTo(400L);
    }
}
