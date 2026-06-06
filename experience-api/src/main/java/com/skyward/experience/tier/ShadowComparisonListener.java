package com.skyward.experience.tier;

import java.util.UUID;

/**
 * Sink for shadow-compare findings. Kept as an interface (not inline logging) so the reporting seam is
 * explicit and swappable — log today, a metric/alert or a reconciliation feed tomorrow. During a real
 * cutover the mismatch <em>rate</em> over time is the signal that tells you whether the new path is ready.
 */
public interface ShadowComparisonListener {

    /** The shadow path returned a different tier than the authoritative one. */
    void onMismatch(
            UUID memberId,
            String authoritativeSource,
            String authoritativeTier,
            String shadowSource,
            String shadowTier);

    /** The shadow path failed; isolated from the served response and recorded for visibility. */
    void onShadowError(UUID memberId, String shadowSource, Throwable error);
}
