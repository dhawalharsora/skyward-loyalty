package com.skyward.redemption;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Member-facing redemption endpoint. Drives the saga and returns its outcome. */
@RestController
@RequestMapping("/redemptions")
public class RedemptionController {

    private final RedemptionOrchestrator orchestrator;

    public RedemptionController(RedemptionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public RedemptionResponse redeem(@Valid @RequestBody RedemptionRequest request) {
        Redemption redemption = orchestrator.redeem(
                request.memberId(), request.rewardCode(), request.points(), request.idempotencyKey());
        return RedemptionResponse.from(redemption);
    }
}
