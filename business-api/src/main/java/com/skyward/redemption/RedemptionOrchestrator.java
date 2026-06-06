package com.skyward.redemption;

import com.skyward.common.partner.FulfilmentException;
import com.skyward.common.partner.FulfilmentRequest;
import com.skyward.common.partner.FulfilmentResult;
import com.skyward.common.partner.PartnerFulfilmentClient;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The redemption saga orchestrator. It drives a sequence of <b>local transactions</b> — never one
 * transaction spanning the partner call:
 *
 * <pre>
 *   reserve (hold) ──▶ fulfil with partner ──success──▶ commit (burn points)
 *                                          └──failure──▶ compensate (release hold)
 * </pre>
 *
 * <p>The transactional steps live on {@link RedemptionService} (a separate bean) so each runs in its
 * own transaction through the proxy. Idempotency: reserve returns the existing saga for a replayed
 * request, so this method is safe to re-invoke.
 */
@Component
public class RedemptionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RedemptionOrchestrator.class);

    private final RedemptionService redemptionService;
    private final PartnerFulfilmentClient partnerClient;

    public RedemptionOrchestrator(RedemptionService redemptionService,
            PartnerFulfilmentClient partnerClient) {
        this.redemptionService = redemptionService;
        this.partnerClient = partnerClient;
    }

    public Redemption redeem(UUID memberId, String rewardCode, long points, String idempotencyKey) {
        Redemption redemption = redemptionService.reserve(memberId, rewardCode, points, idempotencyKey);

        // Not RESERVED means either insufficient balance (FAILED) or an already-resolved replay.
        if (redemption.getStatus() != RedemptionStatus.RESERVED) {
            return redemption;
        }
        return fulfilReserved(redemption);
    }

    /**
     * Drives a RESERVED saga forward: fulfil with the partner, then commit. On a <b>definite</b> failure
     * we compensate (release the hold). On an <b>indeterminate</b> failure (timeout / open circuit) we do
     * NOT compensate — the partner may have fulfilled — and leave the saga RESERVED for the recovery
     * scheduler to retry via an idempotent re-fulfil. Reused by both the live request and recovery.
     */
    public Redemption fulfilReserved(Redemption redemption) {
        try {
            FulfilmentResult result = partnerClient.fulfil(new FulfilmentRequest(
                    redemption.getId(), redemption.getMemberId(),
                    redemption.getRewardCode(), redemption.getPoints()));
            redemptionService.markFulfilled(redemption.getId(), result.partnerReference());
            return redemptionService.commit(redemption.getId());
        } catch (FulfilmentException e) {
            if (e.indeterminate()) {
                log.warn("Indeterminate fulfilment for redemption {} — leaving RESERVED for recovery: {}",
                        redemption.getId(), e.getMessage());
                return redemption; // hold preserved; do not compensate blindly
            }
            log.warn("Definite fulfilment failure for redemption {} — compensating: {}",
                    redemption.getId(), e.getMessage());
            return redemptionService.compensate(redemption.getId(), e.getMessage());
        }
    }
}
