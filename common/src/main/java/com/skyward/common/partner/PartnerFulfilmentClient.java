package com.skyward.common.partner;

/**
 * Outbound port for fulfilling a reward with a partner. Defined in {@code common} so the business saga
 * depends only on this interface, and the implementation (Resilience4j-wrapped, in adapter-partner now,
 * a REST client to a separate adapter service later) can be swapped without touching the orchestrator.
 */
public interface PartnerFulfilmentClient {

    /**
     * Fulfils the reward. Returns the partner's confirmation on success; throws
     * {@link FulfilmentException} on failure (rejection, timeout, or open circuit).
     */
    FulfilmentResult fulfil(FulfilmentRequest request);
}
