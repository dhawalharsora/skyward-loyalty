package com.skyward.experience.tier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The routing decision in isolation — no Spring, no I/O. This is where "the config-driven percentage is
 * honoured" is actually proven. Routing is <b>sticky per member</b> (deterministic hash of the member id),
 * which is the property that makes a strangler cutover safe: a member gets a consistent path, cohorts roll
 * forward/back cleanly, and an incident is reproducible.
 */
class StranglerRoutingTest {

    private StranglerRouter routerAt(int legacyPercent) {
        StranglerProperties props = new StranglerProperties();
        props.setLegacyPercent(legacyPercent);
        // Providers are irrelevant to the decision itself, so the routing logic is testable with nulls.
        return new StranglerRouter(null, null, props);
    }

    @Test
    void zeroPercentSendsEveryoneToTheNewPath() {
        StranglerRouter router = routerAt(0);
        boolean anyLegacy = IntStream.range(0, 1_000)
                .anyMatch(i -> router.routesToLegacy(UUID.randomUUID()));
        assertThat(anyLegacy).isFalse();
    }

    @Test
    void hundredPercentSendsEveryoneToLegacy() {
        StranglerRouter router = routerAt(100);
        boolean allLegacy = IntStream.range(0, 1_000)
                .allMatch(i -> router.routesToLegacy(UUID.randomUUID()));
        assertThat(allLegacy).isTrue();
    }

    @Test
    void decisionIsStickyForTheSameMember() {
        StranglerRouter router = routerAt(50);
        UUID member = UUID.randomUUID();
        boolean first = router.routesToLegacy(member);
        for (int i = 0; i < 100; i++) {
            assertThat(router.routesToLegacy(member)).isEqualTo(first);
        }
    }

    @Test
    void fiftyPercentSplitsTheCohortRoughlyInHalf() {
        StranglerRouter router = routerAt(50);
        long legacy = IntStream.range(0, 10_000)
                .filter(i -> router.routesToLegacy(UUID.randomUUID()))
                .count();
        // A deterministic hash over random ids should land near 50%; allow a generous band for variance.
        assertThat(legacy).isBetween(4_400L, 5_600L);
    }
}
