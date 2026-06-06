package com.skyward.experience.tier;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link ShadowComparisonListener}: structured WARN logs. Mismatches and shadow errors are the
 * artefacts a migration team reviews before cutover, so they are logged with stable, greppable fields
 * (member id, both sources, both tiers) rather than buried in a free-text message.
 */
@Component
public class LoggingShadowComparisonListener implements ShadowComparisonListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingShadowComparisonListener.class);

    @Override
    public void onMismatch(
            UUID memberId,
            String authoritativeSource,
            String authoritativeTier,
            String shadowSource,
            String shadowTier) {
        log.warn(
                "strangler shadow mismatch memberId={} {}Tier={} {}Tier={}",
                memberId,
                authoritativeSource,
                authoritativeTier,
                shadowSource,
                shadowTier);
    }

    @Override
    public void onShadowError(UUID memberId, String shadowSource, Throwable error) {
        log.warn(
                "strangler shadow path error memberId={} shadowSource={} error={}",
                memberId,
                shadowSource,
                error.toString());
    }
}
