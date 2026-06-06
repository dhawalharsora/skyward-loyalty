package com.skyward.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.skyward.accrual.AccrualRequest;
import com.skyward.accrual.AccrualService;
import com.skyward.domain.balance.BalanceResponse;
import com.skyward.domain.balance.MemberBalanceRepository;
import com.skyward.domain.ledger.LedgerEntryRepository;
import com.skyward.domain.member.Member;
import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.member.Tier;
import com.skyward.support.AbstractIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * End-to-end: an accrual flows accrual -> outbox -> relay -> Kafka -> consumer -> materialized balance.
 * The balance is eventually consistent (it lags the synchronous accrual response), so we await it.
 * We also assert the reconciliation invariant: the materialized balance equals the ledger SUM.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BalanceProjectionIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AccrualService accrualService;

    @Autowired
    private MemberRepository members;

    @Autowired
    private LedgerEntryRepository ledger;

    @Autowired
    private MemberBalanceRepository balances;

    @Test
    void accrualEventuallyUpdatesMaterializedBalance() {
        Member member = members.save(Member.enrol("Ada Lovelace", Tier.GOLD)); // x1.5
        accrualService.accrue(new AccrualRequest(member.getId(), 1_000, "flight:SY1", "proj-e2e-1"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            BalanceResponse body = rest.getForObject(
                    "/members/{id}/balance", BalanceResponse.class, member.getId());
            assertThat(body.balance()).isEqualTo(1_500L);
        });

        // Reconciliation invariant: the materialized read-model matches the ledger source of truth.
        long materialized = balances.findById(member.getId()).orElseThrow().getBalance();
        assertThat(materialized).isEqualTo(ledger.balanceOf(member.getId()));
    }
}
