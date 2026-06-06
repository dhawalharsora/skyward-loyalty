package com.skyward.experience.tier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
 * Shadow-compare mode — the pre-cutover migration phase. The facade calls <b>both</b> paths, serves the
 * configured authoritative one (here: legacy, the current source of truth), and reports mismatches without
 * affecting the response. This is how a real migration de-risks a cutover: it surfaces exactly where the
 * new system disagrees with the old, on live traffic, before any user is served the new answer.
 *
 * <p>The percentage split is irrelevant in shadow mode (both paths are always called), so it is not set.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "skyward.strangler.shadow.enabled=true",
            "skyward.strangler.shadow.authoritative=legacy"
        })
class StranglerShadowIT {

    @Autowired
    private TestRestTemplate rest;

    @MockBean
    private LegacyTierProvider legacy;

    @MockBean
    private DomainTierProvider domain;

    @MockBean
    private ShadowComparisonListener listener;

    private static TierView view(UUID id, String tier, String source) {
        return new TierView(id, tier, source, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void servesAuthoritativeAndReportsMismatch() {
        UUID memberId = UUID.randomUUID();
        when(legacy.source()).thenReturn("legacy");
        when(domain.source()).thenReturn("domain");
        when(legacy.tierFor(memberId)).thenReturn(view(memberId, "GOLD", "legacy"));
        when(domain.tierFor(memberId)).thenReturn(view(memberId, "SILVER", "domain"));

        ResponseEntity<TierView> response =
                rest.getForEntity("/members/{id}/tier", TierView.class, memberId);

        // Caller gets the authoritative (legacy) answer — the new path's disagreement is never served.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().source()).isEqualTo("legacy");
        assertThat(response.getBody().tier()).isEqualTo("GOLD");

        // ...but the mismatch is recorded for the migration team.
        verify(listener).onMismatch(eq(memberId), eq("legacy"), eq("GOLD"), eq("domain"), eq("SILVER"));
    }

    @Test
    void agreementIsNotReportedAsMismatch() {
        UUID memberId = UUID.randomUUID();
        when(legacy.source()).thenReturn("legacy");
        when(domain.source()).thenReturn("domain");
        when(legacy.tierFor(memberId)).thenReturn(view(memberId, "PLATINUM", "legacy"));
        when(domain.tierFor(memberId)).thenReturn(view(memberId, "PLATINUM", "domain"));

        ResponseEntity<TierView> response =
                rest.getForEntity("/members/{id}/tier", TierView.class, memberId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().tier()).isEqualTo("PLATINUM");
        verify(listener, never()).onMismatch(any(), any(), any(), any(), any());
    }

    @Test
    void shadowFailureDoesNotBreakTheServedResponse() {
        UUID memberId = UUID.randomUUID();
        when(legacy.source()).thenReturn("legacy");
        when(domain.source()).thenReturn("domain");
        when(legacy.tierFor(memberId)).thenReturn(view(memberId, "GOLD", "legacy"));
        when(domain.tierFor(memberId)).thenThrow(new MemberNotFoundException(memberId));

        ResponseEntity<TierView> response =
                rest.getForEntity("/members/{id}/tier", TierView.class, memberId);

        // The shadow (new) path failing is isolated: the authoritative answer is still served...
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().tier()).isEqualTo("GOLD");
        // ...and the shadow failure is recorded, not raised.
        verify(listener).onShadowError(eq(memberId), eq("domain"), any(Throwable.class));
        verify(listener, never()).onMismatch(any(), any(), any(), any(), any());
    }

    @Test
    void authoritativeFailurePropagates() {
        UUID memberId = UUID.randomUUID();
        when(legacy.source()).thenReturn("legacy");
        when(domain.source()).thenReturn("domain");
        when(legacy.tierFor(memberId)).thenThrow(new MemberNotFoundException(memberId));

        ResponseEntity<String> response =
                rest.getForEntity("/members/{id}/tier", String.class, memberId);

        // If the system we actually serve from can't answer, that is a real 404 for the caller.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
