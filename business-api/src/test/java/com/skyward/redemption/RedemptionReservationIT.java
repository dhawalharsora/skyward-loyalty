package com.skyward.redemption;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyward.domain.ledger.LedgerEntry;
import com.skyward.domain.ledger.LedgerEntryRepository;
import com.skyward.domain.member.Member;
import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.member.Tier;
import com.skyward.support.AbstractIntegrationTest;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The reserve step of the redemption saga: it places a "hold" against the member's <em>strongly
 * consistent</em> available balance (ledger sum minus in-flight holds), is idempotent, and is safe
 * under concurrent redemptions (a per-member lock prevents double-spend).
 */
@SpringBootTest
class RedemptionReservationIT extends AbstractIntegrationTest {

    @Autowired
    private RedemptionService redemptions;

    @Autowired
    private RedemptionRepository redemptionRepository;

    @Autowired
    private MemberRepository members;

    @Autowired
    private LedgerEntryRepository ledger;

    private Member memberWithBalance(long points) {
        Member member = members.save(Member.enrol("Ada Lovelace", Tier.GOLD));
        ledger.save(LedgerEntry.earn(member.getId(), points, "seed"));
        return member;
    }

    @Test
    void reserveCreatesHoldWhenBalanceIsSufficient() {
        Member member = memberWithBalance(1_000);

        Redemption redemption =
                redemptions.reserve(member.getId(), "FLIGHT_UPGRADE", 600, "redeem-1");

        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.RESERVED);
        assertThat(redemption.getPoints()).isEqualTo(600L);
        assertThat(redemption.getMemberId()).isEqualTo(member.getId());
    }

    @Test
    void reserveFailsWhenBalanceIsInsufficient() {
        Member member = memberWithBalance(500);

        Redemption redemption = redemptions.reserve(member.getId(), "FLIGHT_UPGRADE", 600, "redeem-2");

        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.FAILED);
    }

    @Test
    void reserveIsIdempotent() {
        Member member = memberWithBalance(1_000);

        Redemption first = redemptions.reserve(member.getId(), "FLIGHT_UPGRADE", 600, "redeem-3");
        Redemption second = redemptions.reserve(member.getId(), "FLIGHT_UPGRADE", 600, "redeem-3");

        assertThat(second.getId()).isEqualTo(first.getId());
        long count = redemptionRepository.findAll().stream()
                .filter(r -> r.getMemberId().equals(member.getId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void concurrentReservesDoNotOverspend() throws Exception {
        Member member = memberWithBalance(1_000); // only enough for ONE 600-point hold

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Redemption> reserveA =
                    () -> redemptions.reserve(member.getId(), "FLIGHT_UPGRADE", 600, "conc-A");
            Callable<Redemption> reserveB =
                    () -> redemptions.reserve(member.getId(), "FLIGHT_UPGRADE", 600, "conc-B");

            List<Future<Redemption>> results = pool.invokeAll(List.of(reserveA, reserveB));
            List<RedemptionStatus> statuses = List.of(
                    results.get(0).get().getStatus(), results.get(1).get().getStatus());

            // The per-member lock serializes them: exactly one reserves, the other is rejected.
            assertThat(statuses)
                    .containsExactlyInAnyOrder(RedemptionStatus.RESERVED, RedemptionStatus.FAILED);
        } finally {
            pool.shutdownNow();
        }
    }
}
