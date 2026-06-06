package com.skyward.redemption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.skyward.accrual.AccrualRequest;
import com.skyward.adapter.partner.FlakyPartnerStub;
import com.skyward.domain.balance.BalanceResponse;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Full lifecycle through the HTTP endpoints: accrue points (balance rises via the accrual pipeline),
 * then redeem (balance falls via the burn flowing through the SAME outbox -> Kafka -> projection
 * pipeline). Proves the materialized balance reflects both earns and burns.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedemptionLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MemberRepository members;

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

    private long balanceOf(Member member) {
        return rest.getForObject("/members/{id}/balance", BalanceResponse.class, member.getId())
                .balance();
    }

    @Test
    void accrueThenRedeemUpdatesMaterializedBalance() {
        Member member = members.save(Member.enrol("Ada Lovelace", Tier.GOLD)); // x1.5

        // Accrue: base 1000 * 1.5 = 1500, propagated to the materialized balance.
        rest.postForEntity("/accruals",
                new AccrualRequest(member.getId(), 1_000, "flight:SY1", "life-acc"), Void.class);
        await().atMost(Duration.ofSeconds(20)).until(() -> balanceOf(member) == 1_500L);

        // Redeem 600: fulfilled by the partner, points burned via the outbox pipeline.
        ResponseEntity<RedemptionResponse> response = rest.postForEntity("/redemptions",
                new RedemptionRequest(member.getId(), "FLIGHT_UPGRADE", 600, "life-rdm"),
                RedemptionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(RedemptionStatus.COMPLETED);

        await().atMost(Duration.ofSeconds(20)).until(() -> balanceOf(member) == 900L);
    }
}
