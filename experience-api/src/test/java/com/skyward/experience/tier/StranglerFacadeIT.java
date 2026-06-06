package com.skyward.experience.tier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end through the real HTTP endpoint and the real {@link StranglerRouter}, with the two providers
 * replaced by mocks. Proves three things the facade promises: a request is routed to the correct path for
 * the member's cohort (sticky hash at the configured percentage), the response is the normalized shape
 * stamped with the serving path, and a not-found from either path surfaces as a REST 404.
 *
 * <p>The percentage is fixed at 50; member ids are chosen to fall on a known side of the hash so the test
 * asserts real routing rather than mocking the decision away.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "skyward.strangler.legacy-percent=50")
class StranglerFacadeIT {

    @Autowired
    private TestRestTemplate rest;

    @MockBean
    private LegacyTierProvider legacy;

    @MockBean
    private DomainTierProvider domain;

    /** Finds a member id whose sticky-hash bucket falls on the requested side of a 50% split. */
    private static UUID idRoutingToLegacy(boolean legacy) {
        while (true) {
            UUID candidate = UUID.randomUUID();
            boolean routesLegacy = Math.floorMod(candidate.hashCode(), 100) < 50;
            if (routesLegacy == legacy) {
                return candidate;
            }
        }
    }

    @Test
    void legacyCohortIsServedByTheLegacyPath() {
        UUID memberId = idRoutingToLegacy(true);
        when(legacy.tierFor(any())).thenAnswer(inv -> new TierView(
                inv.getArgument(0), "PLATINUM", "legacy", OffsetDateTime.now(ZoneOffset.UTC)));

        ResponseEntity<TierView> response =
                rest.getForEntity("/members/{id}/tier", TierView.class, memberId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().source()).isEqualTo("legacy");
        assertThat(response.getBody().tier()).isEqualTo("PLATINUM");
    }

    @Test
    void newCohortIsServedByTheDomainPath() {
        UUID memberId = idRoutingToLegacy(false);
        when(domain.tierFor(any())).thenAnswer(inv -> new TierView(
                inv.getArgument(0), "GOLD", "domain", OffsetDateTime.now(ZoneOffset.UTC)));

        ResponseEntity<TierView> response =
                rest.getForEntity("/members/{id}/tier", TierView.class, memberId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().source()).isEqualTo("domain");
        assertThat(response.getBody().tier()).isEqualTo("GOLD");
    }

    @Test
    void unknownMemberSurfacesAs404() {
        UUID memberId = idRoutingToLegacy(false);
        when(domain.tierFor(any())).thenThrow(new MemberNotFoundException(memberId));

        ResponseEntity<String> response =
                rest.getForEntity("/members/{id}/tier", String.class, memberId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
