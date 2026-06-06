package com.skyward.accrual;

import com.skyward.domain.member.Member;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * The earning-rules engine (business layer). Computes final points from base points by applying the
 * member's tier multiplier and any matching config-driven campaigns.
 *
 * <p>All multipliers are combined first, then a single HALF_UP rounding to whole points, then flat
 * campaign bonuses are added. Rounding once at the end (rather than per-step) avoids compounding
 * rounding error — the kind of detail that matters for points/financial math.
 */
@Service
public class EarningRules {

    private final EarningProperties properties;

    public EarningRules(EarningProperties properties) {
        this.properties = properties;
    }

    public long earnedPointsFor(Member member, long basePoints, String source) {
        double multiplier = member.getTier().earnMultiplier();
        long bonus = 0L;

        for (EarningProperties.Campaign campaign : properties.campaigns()) {
            if (campaign.matches(source)) {
                multiplier *= campaign.multiplier();
                bonus += campaign.bonusPoints();
            }
        }

        long scaled = BigDecimal.valueOf(basePoints)
                .multiply(BigDecimal.valueOf(multiplier))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        return scaled + bonus;
    }
}
