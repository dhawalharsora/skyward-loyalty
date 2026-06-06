package com.skyward.redemption;

import java.util.UUID;

/** Result of a redemption saga: its final (or current) state. */
public record RedemptionResponse(
        UUID redemptionId,
        UUID memberId,
        RedemptionStatus status,
        long points,
        String partnerReference,
        String failureReason) {

    public static RedemptionResponse from(Redemption redemption) {
        return new RedemptionResponse(
                redemption.getId(),
                redemption.getMemberId(),
                redemption.getStatus(),
                redemption.getPoints(),
                redemption.getPartnerReference(),
                redemption.getFailureReason());
    }
}
