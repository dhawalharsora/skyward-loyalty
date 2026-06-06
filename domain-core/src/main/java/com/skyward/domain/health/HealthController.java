package com.skyward.domain.health;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trivial liveness endpoint required by the Day 0 plan.
 *
 * <p>Actuator already exposes {@code /actuator/health} (with liveness/readiness probes); this
 * plain {@code /health} is a deliberately simple, dependency-free signal that the web layer is up.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "domain-core",
                "timestamp", Instant.now().toString());
    }
}
