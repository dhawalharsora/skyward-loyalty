package com.skyward.balance;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyward.domain.balance.BalanceResponse;
import com.skyward.domain.balance.MemberBalance;
import com.skyward.domain.balance.MemberBalanceRepository;
import com.skyward.domain.member.Member;
import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.member.Tier;
import com.skyward.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The balance endpoint now reads the <em>materialized</em> {@code member_balance} read-model (O(1)),
 * which the projection consumer maintains. Here we seed the read-model directly to test the read path
 * in isolation; the full accrual -> event -> projection pipeline is covered by {@link BalanceProjectionIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberBalanceIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MemberBalanceRepository balances;

    @Test
    void endpointReturnsMaterializedBalance() {
        Member member = members.save(Member.enrol("Ada Lovelace", Tier.GOLD));
        MemberBalance balance = MemberBalance.zero(member.getId());
        balance.add(1_300);
        balances.save(balance);

        ResponseEntity<BalanceResponse> response =
                rest.getForEntity("/members/{id}/balance", BalanceResponse.class, member.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().memberId()).isEqualTo(member.getId());
        assertThat(response.getBody().balance()).isEqualTo(1_300L);
    }

    @Test
    void memberWithNoBalanceRowReturnsZero() {
        Member member = members.save(Member.enrol("Grace Hopper", Tier.SILVER));

        ResponseEntity<BalanceResponse> response =
                rest.getForEntity("/members/{id}/balance", BalanceResponse.class, member.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().balance()).isZero();
    }

    @Test
    void unknownMemberReturns404() {
        ResponseEntity<String> response =
                rest.getForEntity("/members/{id}/balance", String.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
