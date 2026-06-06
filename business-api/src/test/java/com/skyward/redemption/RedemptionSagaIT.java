package com.skyward.redemption;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyward.adapter.partner.FlakyPartnerStub;
import com.skyward.domain.ledger.EntryType;
import com.skyward.domain.ledger.LedgerEntryRepository;
import com.skyward.domain.member.Member;
import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.member.Tier;
import com.skyward.support.AbstractIntegrationTest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.skyward.domain.ledger.LedgerEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The redemption saga end-to-end at the orchestrator level: reserve -> fulfil -> commit (burn) on
 * success, or compensate (release the hold, no burn) on partner failure. Assertions use the
 * strongly-consistent ledger so they are deterministic (no awaiting the async projection).
 */
@SpringBootTest
class RedemptionSagaIT extends AbstractIntegrationTest {

    @Autowired
    private RedemptionOrchestrator orchestrator;

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
    void happyPathFulfilsAndBurnsPoints() {
        Member member = memberWithBalance(1_000);
        partner.setMode(FlakyPartnerStub.Mode.SUCCEED);

        Redemption redemption =
                orchestrator.redeem(member.getId(), "FLIGHT_UPGRADE", 600, "rdm-happy");

        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.COMPLETED);
        assertThat(redemption.getPartnerReference()).isNotBlank();
        // Points were burned: balance dropped, and a BURN entry exists.
        assertThat(ledger.balanceOf(member.getId())).isEqualTo(400L);
        assertThat(ledger.findByMemberIdOrderByCreatedAtAsc(member.getId()))
                .anyMatch(e -> e.getEntryType() == EntryType.BURN && e.getAmount() == 600L);
    }

    @Test
    void partnerFailureCompensatesAndReleasesHold() {
        Member member = memberWithBalance(1_000);
        partner.setMode(FlakyPartnerStub.Mode.FAIL);

        Redemption redemption =
                orchestrator.redeem(member.getId(), "FLIGHT_UPGRADE", 600, "rdm-comp");

        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.COMPENSATED);
        // No burn: balance unchanged.
        assertThat(ledger.balanceOf(member.getId())).isEqualTo(1_000L);
        assertThat(ledger.findByMemberIdOrderByCreatedAtAsc(member.getId()))
                .noneMatch(e -> e.getEntryType() == EntryType.BURN);

        // The hold was released: a fresh redemption for the FULL balance can still be reserved
        // (it would be FAILED-insufficient if the previous 600 hold were still active).
        Redemption again =
                orchestrator.redeem(member.getId(), "FLIGHT_UPGRADE", 1_000, "rdm-after");
        assertThat(again.getStatus()).isEqualTo(RedemptionStatus.COMPENSATED);
    }

    @Test
    void insufficientBalanceFailsWithoutCallingPartner() {
        Member member = memberWithBalance(500);
        partner.setMode(FlakyPartnerStub.Mode.SUCCEED);

        Redemption redemption = orchestrator.redeem(member.getId(), "FLIGHT_UPGRADE", 600, "rdm-insuff");

        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.FAILED);
        assertThat(partner.invocations()).isZero(); // never reached fulfilment
        assertThat(ledger.balanceOf(member.getId())).isEqualTo(500L);
    }
}
