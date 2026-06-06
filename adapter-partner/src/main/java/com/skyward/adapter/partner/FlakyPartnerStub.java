package com.skyward.adapter.partner;

import com.skyward.common.partner.FulfilmentRequest;
import com.skyward.common.partner.FulfilmentResult;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * A simulated, unreliable partner. Behaviour is seeded from config (the demo toggle) and changeable at
 * runtime by tests.
 *
 * <p>Crucially it is <b>idempotent</b>: it remembers which redemptions it has fulfilled (keyed by
 * redemption id) and returns the same result on a repeat call without issuing twice. That is what makes
 * retry and recovery safe, and what resolves the "timed out but actually fulfilled" edge case.
 */
@Component
public class FlakyPartnerStub {

    public enum Mode {
        SUCCEED,
        /** Definite rejection — the reward is not issued. */
        FAIL,
        /** Fail the first {@code failuresBeforeSuccess} calls, then succeed (transient/indeterminate). */
        FAIL_THEN_SUCCEED,
        /** Sleep before succeeding — a tight time limit turns this into a timeout (indeterminate). */
        SLOW,
        /** Issue the reward, THEN stall — so the call times out even though fulfilment happened. */
        TIMEOUT_BUT_FULFILS
    }

    private volatile Mode mode;
    private volatile long latencyMs;
    private volatile int failuresBeforeSuccess;

    private final AtomicInteger invocations = new AtomicInteger();
    private final AtomicInteger transientFailures = new AtomicInteger();
    private final Map<UUID, FulfilmentResult> fulfilled = new ConcurrentHashMap<>();

    public FlakyPartnerStub(PartnerFulfilmentProperties properties) {
        this.mode = Mode.valueOf(properties.mode().name());
        this.latencyMs = properties.latencyMs();
        this.failuresBeforeSuccess = properties.failuresBeforeSuccess();
    }

    public FulfilmentResult invoke(FulfilmentRequest request) {
        invocations.incrementAndGet();

        // Idempotency: if this redemption was already fulfilled, return the same result — never reissue.
        FulfilmentResult existing = fulfilled.get(request.redemptionId());
        if (existing != null) {
            return existing;
        }

        return switch (mode) {
            case SUCCEED -> issue(request);
            case FAIL -> throw new PartnerRejectedException("partner rejected fulfilment");
            case SLOW -> {
                sleep(latencyMs);
                yield issue(request);
            }
            case TIMEOUT_BUT_FULFILS -> {
                FulfilmentResult result = issue(request); // record fulfilment BEFORE stalling
                sleep(latencyMs);
                yield result;
            }
            case FAIL_THEN_SUCCEED -> {
                if (transientFailures.incrementAndGet() <= failuresBeforeSuccess) {
                    throw new IllegalStateException("partner transient failure");
                }
                yield issue(request);
            }
        };
    }

    private FulfilmentResult issue(FulfilmentRequest request) {
        FulfilmentResult result = new FulfilmentResult("PARTNER-" + request.redemptionId());
        fulfilled.put(request.redemptionId(), result);
        return result;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    // --- runtime controls (config seeds defaults; tests override) ---

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public void setFailuresBeforeSuccess(int failuresBeforeSuccess) {
        this.failuresBeforeSuccess = failuresBeforeSuccess;
    }

    public int invocations() {
        return invocations.get();
    }

    /** Number of distinct redemptions actually fulfilled (proves no double-issue). */
    public int distinctIssues() {
        return fulfilled.size();
    }

    public void reset() {
        invocations.set(0);
        transientFailures.set(0);
        fulfilled.clear();
    }
}
