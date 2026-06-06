package com.skyward.partner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.skyward.adapter.partner.FlakyPartnerStub;
import com.skyward.common.partner.FulfilmentException;
import com.skyward.common.partner.FulfilmentRequest;
import com.skyward.common.partner.FulfilmentResult;
import com.skyward.common.partner.PartnerFulfilmentClient;
import com.skyward.support.AbstractIntegrationTest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Resilience4j behaviour around the partner fulfilment call: success, retry, time-limit, hard failure,
 * and circuit breaking. The flaky stub is driven at runtime so each behaviour is deterministic.
 */
@SpringBootTest
class PartnerFulfilmentIT extends AbstractIntegrationTest {

    @Autowired
    private PartnerFulfilmentClient client;

    @Autowired
    private FlakyPartnerStub partner;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        partner.reset();
        circuitBreakerRegistry.circuitBreaker("partnerFulfilment").reset();
    }

    private static FulfilmentRequest request() {
        return new FulfilmentRequest(UUID.randomUUID(), UUID.randomUUID(), "FLIGHT_UPGRADE", 1_000);
    }

    @Test
    void successReturnsPartnerReference() {
        partner.setMode(FlakyPartnerStub.Mode.SUCCEED);

        FulfilmentResult result = client.fulfil(request());

        assertThat(result.partnerReference()).isNotBlank();
        assertThat(partner.invocations()).isEqualTo(1);
    }

    @Test
    void retriesTransientFailuresThenSucceeds() {
        partner.setMode(FlakyPartnerStub.Mode.FAIL_THEN_SUCCEED);
        partner.setFailuresBeforeSuccess(2);

        FulfilmentResult result = client.fulfil(request());

        assertThat(result.partnerReference()).isNotBlank();
        assertThat(partner.invocations()).isEqualTo(3); // 2 failures + 1 success (max-attempts = 3)
    }

    @Test
    void hardFailureThrowsAfterExhaustingRetries() {
        partner.setMode(FlakyPartnerStub.Mode.FAIL);

        assertThatThrownBy(() -> client.fulfil(request())).isInstanceOf(FulfilmentException.class);
        assertThat(partner.invocations()).isEqualTo(3); // retried up to max-attempts
    }

    @Test
    void slowPartnerTimesOut() {
        partner.setMode(FlakyPartnerStub.Mode.SLOW);
        partner.setLatencyMs(2_000); // exceeds the 500ms time limit

        assertThatThrownBy(() -> client.fulfil(request())).isInstanceOf(FulfilmentException.class);
    }

    @Test
    void circuitBreakerOpensAndFailsFastWithoutCallingPartner() {
        partner.setMode(FlakyPartnerStub.Mode.FAIL);

        // Drive enough failures to trip the breaker.
        for (int i = 0; i < 10; i++) {
            try {
                client.fulfil(request());
            } catch (RuntimeException ignored) {
                // expected
            }
        }

        int invocationsBeforeOpenCall = partner.invocations();
        assertThatThrownBy(() -> client.fulfil(request())).isInstanceOf(FulfilmentException.class);

        // Breaker is open: the call fails fast and the partner is NOT invoked again.
        assertThat(partner.invocations()).isEqualTo(invocationsBeforeOpenCall);
    }
}
