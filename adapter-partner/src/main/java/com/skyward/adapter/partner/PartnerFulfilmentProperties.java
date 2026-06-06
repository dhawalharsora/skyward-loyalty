package com.skyward.adapter.partner;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config-driven behaviour of the simulated partner (bound from {@code skyward.partner.fulfilment.*}).
 * This is the "flaky-partner toggle": set {@code mode: FAIL} (or SLOW) to force the compensation path
 * for a demo, with no code change.
 */
@ConfigurationProperties("skyward.partner.fulfilment")
public record PartnerFulfilmentProperties(Mode mode, long latencyMs, int failuresBeforeSuccess) {

    public PartnerFulfilmentProperties {
        mode = mode == null ? Mode.SUCCEED : mode;
    }

    public enum Mode {
        /** Always succeed. */
        SUCCEED,
        /** Always reject (hard failure). */
        FAIL,
        /** Fail the first {@code failuresBeforeSuccess} calls, then succeed (transient). */
        FAIL_THEN_SUCCEED,
        /** Sleep {@code latencyMs} before succeeding (to trigger the time limiter). */
        SLOW
    }
}
