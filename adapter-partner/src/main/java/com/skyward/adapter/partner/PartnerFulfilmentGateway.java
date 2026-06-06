package com.skyward.adapter.partner;

import com.skyward.common.partner.FulfilmentRequest;
import com.skyward.common.partner.FulfilmentResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

/**
 * Applies the resilience policies around the raw partner call. Kept as a separate bean on purpose: the
 * Resilience4j annotations are woven by a Spring AOP proxy, which only intercepts calls that arrive
 * <em>through</em> the proxy (from another bean) — a method calling its own annotated method would
 * silently bypass all of this.
 *
 * <p>Policy stacking (default Resilience4j order, outer to inner): {@code Retry → CircuitBreaker →
 * TimeLimiter}. So each retry attempt passes through the breaker and is bounded by the time limiter; a
 * timeout counts as a failure toward the breaker. {@code @TimeLimiter} requires an async return, hence
 * the {@link CompletableFuture}.
 */
@Component
public class PartnerFulfilmentGateway {

    public static final String INSTANCE = "partnerFulfilment";

    private final FlakyPartnerStub partner;

    // Dedicated pool so the partner call never competes for the shared ForkJoinPool.commonPool. The
    // TimeLimiter cancels the future on timeout but cannot interrupt a blocking call, so a slow call
    // keeps its thread until it returns; an isolated, bounded pool keeps that from starving other work.
    private final ExecutorService executor = Executors.newFixedThreadPool(16, runnable -> {
        Thread thread = new Thread(runnable, "partner-fulfilment");
        thread.setDaemon(true);
        return thread;
    });

    public PartnerFulfilmentGateway(FlakyPartnerStub partner) {
        this.partner = partner;
    }

    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    @TimeLimiter(name = INSTANCE)
    public CompletableFuture<FulfilmentResult> fulfil(FulfilmentRequest request) {
        return CompletableFuture.supplyAsync(() -> partner.invoke(request), executor);
    }
}
