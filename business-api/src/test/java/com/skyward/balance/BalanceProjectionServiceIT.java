package com.skyward.balance;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyward.domain.balance.BalanceProjectionService;
import com.skyward.domain.balance.MemberBalanceRepository;
import com.skyward.domain.member.Member;
import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.member.Tier;
import com.skyward.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Idempotency of the projection, tested at the service level (real DB, no Kafka timing). Applying the
 * same event id twice must credit the balance only once — this is what makes at-least-once delivery
 * from the relay safe.
 */
@SpringBootTest
class BalanceProjectionServiceIT extends AbstractIntegrationTest {

    @Autowired
    private BalanceProjectionService projection;

    @Autowired
    private MemberBalanceRepository balances;

    @Autowired
    private MemberRepository members;

    @Test
    void duplicateEventIdIsAppliedOnce() {
        Member member = members.save(Member.enrol("Ada Lovelace", Tier.GOLD));
        UUID eventId = UUID.randomUUID();

        projection.applyAccrued(eventId, member.getId(), 700);
        projection.applyAccrued(eventId, member.getId(), 700); // redelivery of the SAME event

        assertThat(balances.findById(member.getId()).orElseThrow().getBalance()).isEqualTo(700L);
    }

    @Test
    void distinctEventsAccumulate() {
        Member member = members.save(Member.enrol("Grace Hopper", Tier.SILVER));

        projection.applyAccrued(UUID.randomUUID(), member.getId(), 700);
        projection.applyAccrued(UUID.randomUUID(), member.getId(), 300);

        assertThat(balances.findById(member.getId()).orElseThrow().getBalance()).isEqualTo(1_000L);
    }
}
