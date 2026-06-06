package com.skyward.accrual;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyward.domain.member.Member;
import com.skyward.domain.member.Tier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for the earning-rules math: tier multiplier, HALF_UP rounding, and config-driven
 * campaign multiplier/bonus. No Spring, no DB — just the calculation.
 */
class EarningRulesTest {

    private static EarningRules withCampaigns(EarningProperties.Campaign... campaigns) {
        return new EarningRules(new EarningProperties(List.of(campaigns)));
    }

    @Test
    void appliesTierMultiplier() {
        EarningRules rules = withCampaigns();
        Member gold = Member.enrol("Ada", Tier.GOLD); // x1.5

        assertThat(rules.earnedPointsFor(gold, 1_000, "flight:SY1")).isEqualTo(1_500L);
    }

    @Test
    void roundsHalfUp() {
        EarningRules rules = withCampaigns();
        Member silver = Member.enrol("Grace", Tier.SILVER); // x1.25

        // 10 * 1.25 = 12.5 -> 13 (HALF_UP)
        assertThat(rules.earnedPointsFor(silver, 10, "x")).isEqualTo(13L);
    }

    @Test
    void appliesMatchingCampaignMultiplierAndBonus() {
        EarningRules rules = withCampaigns(
                new EarningProperties.Campaign("Hotel double", "partner:hotel", 2.0, 100L));
        Member gold = Member.enrol("Ada", Tier.GOLD); // x1.5

        // 1000 * 1.5 (tier) * 2.0 (campaign) = 3000, + 100 bonus = 3100
        assertThat(rules.earnedPointsFor(gold, 1_000, "partner:hotel:HILTON")).isEqualTo(3_100L);
    }

    @Test
    void ignoresNonMatchingCampaign() {
        EarningRules rules = withCampaigns(
                new EarningProperties.Campaign("Hotel double", "partner:hotel", 2.0, 100L));
        Member gold = Member.enrol("Ada", Tier.GOLD);

        // source does not match the campaign prefix -> tier multiplier only
        assertThat(rules.earnedPointsFor(gold, 1_000, "flight:SY1")).isEqualTo(1_500L);
    }
}
