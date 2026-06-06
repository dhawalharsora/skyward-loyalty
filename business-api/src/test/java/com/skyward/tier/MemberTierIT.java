package com.skyward.tier;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyward.domain.member.Member;
import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.member.Tier;
import com.skyward.domain.tier.TierResponse;
import com.skyward.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The <b>new path</b> of the Day 4 strangler: a synchronous REST read of a member's tier served by the
 * new domain service. The strangler routing facade (slice 4.3, in experience-api) routes a config-driven
 * cohort of {@code GET /members/{id}/tier} traffic here, and the remainder to the legacy SOAP service.
 *
 * <p>Tier lives directly on the {@link Member} aggregate (no separate read-model like balance), so this
 * is a straight lookup: 200 with the tier, or 404 for an unknown member — the same not-found contract as
 * the balance endpoint, so both paths of the facade behave consistently.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberTierIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MemberRepository members;

    @Test
    void endpointReturnsMemberTier() {
        Member member = members.save(Member.enrol("Ada Lovelace", Tier.GOLD));

        ResponseEntity<TierResponse> response =
                rest.getForEntity("/members/{id}/tier", TierResponse.class, member.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().memberId()).isEqualTo(member.getId());
        assertThat(response.getBody().tier()).isEqualTo(Tier.GOLD);
    }

    @Test
    void unknownMemberReturns404() {
        ResponseEntity<String> response =
                rest.getForEntity("/members/{id}/tier", String.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
