package com.skyward.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.member.MemberResponse;
import com.skyward.domain.member.Tier;
import com.skyward.domain.tier.TierResponse;
import com.skyward.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Member enrolment over HTTP — the entry point every live demo needs (you cannot accrue, redeem, or read
 * a tier without a member, and nothing else creates one). A legitimate missing domain operation: the
 * {@code Member.enrol} factory already existed, it just wasn't exposed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberEnrolmentIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MemberRepository members;

    @Test
    void enrolCreatesMemberAndReturns201() {
        ResponseEntity<MemberResponse> response = rest.postForEntity(
                "/members", Map.of("fullName", "Ada Lovelace", "tier", "GOLD"), MemberResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().fullName()).isEqualTo("Ada Lovelace");
        assertThat(response.getBody().tier()).isEqualTo(Tier.GOLD);
        assertThat(members.existsById(response.getBody().id())).isTrue();
        // The created resource location points at the member's tier read used by the strangler.
        assertThat(response.getHeaders().getLocation()).isNotNull();
    }

    @Test
    void enrolledMemberIsImmediatelyReadableViaTierEndpoint() {
        ResponseEntity<MemberResponse> created = rest.postForEntity(
                "/members", Map.of("fullName", "Grace Hopper", "tier", "PLATINUM"), MemberResponse.class);
        assertThat(created.getBody()).isNotNull();

        ResponseEntity<TierResponse> tier = rest.getForEntity(
                "/members/{id}/tier", TierResponse.class, created.getBody().id());

        assertThat(tier.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tier.getBody()).isNotNull();
        assertThat(tier.getBody().tier()).isEqualTo(Tier.PLATINUM);
    }

    @Test
    void blankNameIsRejectedWith400() {
        ResponseEntity<String> response = rest.postForEntity(
                "/members", Map.of("fullName", "", "tier", "GOLD"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownTierIsRejectedWith400() {
        ResponseEntity<String> response = rest.postForEntity(
                "/members", Map.of("fullName", "Alan Turing", "tier", "DIAMOND"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
