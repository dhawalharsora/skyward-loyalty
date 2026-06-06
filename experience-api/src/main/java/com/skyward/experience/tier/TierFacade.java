package com.skyward.experience.tier;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves a member's tier through the strangler, in one of two migration modes:
 *
 * <ul>
 *   <li><b>Shadow</b> (pre-cutover): call both paths, serve the authoritative one, report any mismatch.
 *       The new path is exercised on live traffic but never served, so disagreements are found safely.</li>
 *   <li><b>Routing</b> (cutover): a config-driven, sticky-per-member percentage decides the single path
 *       to call. This is the gradual traffic shift once shadow has built confidence.</li>
 * </ul>
 *
 * The two are sequential stages of the same migration, selected by config alone.
 */
@Component
public class TierFacade {

    private final LegacyTierProvider legacy;
    private final DomainTierProvider domain;
    private final StranglerRouter router;
    private final StranglerProperties properties;
    private final ShadowComparisonListener shadowListener;

    public TierFacade(
            LegacyTierProvider legacy,
            DomainTierProvider domain,
            StranglerRouter router,
            StranglerProperties properties,
            ShadowComparisonListener shadowListener) {
        this.legacy = legacy;
        this.domain = domain;
        this.router = router;
        this.properties = properties;
        this.shadowListener = shadowListener;
    }

    public TierView resolve(UUID memberId) {
        if (properties.getShadow().isEnabled()) {
            return resolveWithShadow(memberId);
        }
        return router.select(memberId).tierFor(memberId);
    }

    private TierView resolveWithShadow(UUID memberId) {
        TierProvider authoritative = authoritativeProvider();
        TierProvider shadow = (authoritative == legacy) ? domain : legacy;

        // The served answer comes from the authoritative path; its failure is a real failure for the
        // caller and propagates (e.g. a 404). We resolve it before touching the shadow path.
        TierView served = authoritative.tierFor(memberId);

        compareAgainstShadow(memberId, served, shadow);
        return served;
    }

    private void compareAgainstShadow(UUID memberId, TierView served, TierProvider shadow) {
        try {
            TierView shadowView = shadow.tierFor(memberId);
            if (!Objects.equals(served.tier(), shadowView.tier())) {
                shadowListener.onMismatch(
                        memberId, served.source(), served.tier(),
                        shadowView.source(), shadowView.tier());
            }
        } catch (RuntimeException shadowFailure) {
            // The shadow path is best-effort: never let it affect the served response. A shadow failure
            // (including the new system not knowing a member legacy knows) is itself a finding.
            shadowListener.onShadowError(memberId, shadow.source(), shadowFailure);
        }
    }

    private TierProvider authoritativeProvider() {
        return domain.source().equalsIgnoreCase(properties.getShadow().getAuthoritative())
                ? domain
                : legacy;
    }
}
