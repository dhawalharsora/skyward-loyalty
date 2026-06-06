package com.skyward.redemption;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives interrupted sagas to completion after a crash/restart — this is what makes the saga "survive a
 * restart". On a timer it finds sagas stuck mid-flight (RESERVED/FULFILLED) and resumes them:
 *
 * <ul>
 *   <li>FULFILLED but never committed → commit (idempotent).</li>
 *   <li>RESERVED → re-run fulfilment. Because the partner is idempotent (keyed by redemption id), a
 *       re-fulfil that the partner already processed returns the same result and completes — and one it
 *       never processed gets fulfilled now. Either way, no double issue.</li>
 * </ul>
 *
 * <p>A minimum-age filter keeps recovery from racing the live request (which resolves within ms).
 */
@Component
public class RedemptionRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionRecoveryService.class);
    private static final List<RedemptionStatus> IN_FLIGHT =
            List.of(RedemptionStatus.RESERVED, RedemptionStatus.FULFILLED);

    private final RedemptionRepository redemptions;
    private final RedemptionOrchestrator orchestrator;
    private final RedemptionService redemptionService;
    private final Duration minAge;

    public RedemptionRecoveryService(RedemptionRepository redemptions,
            RedemptionOrchestrator orchestrator, RedemptionService redemptionService,
            @Value("${skyward.redemption.recovery.min-age-seconds:60}") long minAgeSeconds) {
        this.redemptions = redemptions;
        this.orchestrator = orchestrator;
        this.redemptionService = redemptionService;
        this.minAge = Duration.ofSeconds(minAgeSeconds);
    }

    @Scheduled(fixedDelayString = "${skyward.redemption.recovery.poll-interval-ms:5000}")
    public void recoverScheduled() {
        recoverStuckSagas(minAge);
    }

    public void recoverStuckSagas(Duration olderThan) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(olderThan);
        List<Redemption> stuck = redemptions.findByStatusInAndUpdatedAtBefore(IN_FLIGHT, cutoff);
        for (Redemption redemption : stuck) {
            try {
                resume(redemption);
            } catch (RuntimeException e) {
                log.error("Recovery failed for redemption {}; will retry next cycle",
                        redemption.getId(), e);
            }
        }
    }

    private void resume(Redemption redemption) {
        if (redemption.getStatus() == RedemptionStatus.FULFILLED) {
            redemptionService.commit(redemption.getId()); // fulfilled but never committed
        } else {
            orchestrator.fulfilReserved(redemption); // RESERVED: idempotent re-fulfil then commit
        }
    }
}
