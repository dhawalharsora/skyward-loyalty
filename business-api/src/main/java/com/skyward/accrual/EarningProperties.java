package com.skyward.accrual;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config-driven earning campaigns (bound from {@code skyward.earning.*}). Campaign rules are
 * configuration, never hardcoded — they can change per environment without a code change.
 *
 * <p>Immutable record with constructor binding; null-safe defaults so a missing/empty config is fine.
 */
@ConfigurationProperties("skyward.earning")
public record EarningProperties(List<Campaign> campaigns) {

    public EarningProperties {
        campaigns = campaigns == null ? List.of() : campaigns;
    }

    /**
     * A single campaign rule.
     *
     * @param name        human-readable name (for logs/observability)
     * @param sourcePrefix only apply when the accrual source starts with this prefix (null = always)
     * @param multiplier  multiplicative bonus on top of the tier multiplier (default 1.0 = no change)
     * @param bonusPoints flat points added after multiplication (default 0)
     */
    public record Campaign(String name, String sourcePrefix, Double multiplier, Long bonusPoints) {

        public Campaign {
            multiplier = multiplier == null ? 1.0 : multiplier;
            bonusPoints = bonusPoints == null ? 0L : bonusPoints;
        }

        boolean matches(String source) {
            return sourcePrefix == null || (source != null && source.startsWith(sourcePrefix));
        }
    }
}
