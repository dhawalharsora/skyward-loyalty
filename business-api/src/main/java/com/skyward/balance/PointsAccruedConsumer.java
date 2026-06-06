package com.skyward.balance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyward.common.event.PointsAccrued;
import com.skyward.common.event.Topics;
import com.skyward.domain.balance.BalanceProjectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inbound Kafka adapter: consumes {@code PointsAccrued} and updates the materialized balance via the
 * domain projection service. Kept in the business/host layer (not the domain) so the domain stays free
 * of Kafka.
 *
 * <p>A message that cannot be parsed throws, which the configured error handler retries and then routes
 * to the dead-letter topic — no silent loss, no poison message blocking the partition forever. The
 * apply itself is idempotent, so the relay's at-least-once redelivery is safe.
 */
@Component
public class PointsAccruedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PointsAccruedConsumer.class);

    private final BalanceProjectionService projection;
    private final ObjectMapper objectMapper;

    public PointsAccruedConsumer(BalanceProjectionService projection, ObjectMapper objectMapper) {
        this.projection = projection;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.POINTS_ACCRUED)
    public void onPointsAccrued(String payload) {
        PointsAccrued event = parse(payload);
        projection.applyAccrued(event.eventId(), event.memberId(), event.points());
        log.debug("Applied PointsAccrued event {} for member {}", event.eventId(), event.memberId());
    }

    private PointsAccrued parse(String payload) {
        try {
            return objectMapper.readValue(payload, PointsAccrued.class);
        } catch (JsonProcessingException e) {
            // Poison message: let the error handler retry, then dead-letter it.
            throw new IllegalArgumentException("Unparseable PointsAccrued payload: " + payload, e);
        }
    }
}
