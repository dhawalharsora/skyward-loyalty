package com.skyward.experience.tier;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Decides, per request, whether a member's tier read is served by the legacy path or the new path.
 *
 * <p>Routing is <b>sticky per member</b>: a stable hash of the member id is bucketed into 0–99, and a
 * member falls on the legacy side iff their bucket is below {@code legacyPercent}. Consequences that make
 * this the right strategy for a cutover:
 * <ul>
 *   <li><b>Consistency</b> — a member always gets the same path, so reads don't flap between systems.</li>
 *   <li><b>Controllability</b> — lowering the percentage moves whole cohorts to new; raising it rolls
 *       them back. The decision is deterministic and reproducible.</li>
 *   <li><b>Debuggability</b> — given a member id you can say exactly which system served them.</li>
 * </ul>
 * The alternative, per-request random sampling, hits the population percentage too but loses all three.
 */
@Component
public class StranglerRouter {

    private final LegacyTierProvider legacy;
    private final DomainTierProvider domain;
    private final StranglerProperties properties;

    public StranglerRouter(
            LegacyTierProvider legacy, DomainTierProvider domain, StranglerProperties properties) {
        this.legacy = legacy;
        this.domain = domain;
        this.properties = properties;
    }

    /** True if this member falls in the legacy cohort at the configured percentage. */
    public boolean routesToLegacy(UUID memberId) {
        int bucket = Math.floorMod(memberId.hashCode(), 100);
        return bucket < properties.getLegacyPercent();
    }

    /** Selects the provider for this member according to the sticky routing decision. */
    public TierProvider select(UUID memberId) {
        return routesToLegacy(memberId) ? legacy : domain;
    }
}
