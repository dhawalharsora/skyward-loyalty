package com.skyward.balance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyward.common.event.PointsBurned;
import com.skyward.common.event.Topics;
import com.skyward.domain.balance.BalanceProjectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inbound Kafka adapter for burns: consumes {@code PointsBurned} and debits the materialized balance
 * via the idempotent projection service. Mirror of {@link PointsAccruedConsumer}; poison messages are
 * retried and dead-lettered by the same error handler.
 */
@Component
public class PointsBurnedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PointsBurnedConsumer.class);

    private final BalanceProjectionService projection;
    private final ObjectMapper objectMapper;

    public PointsBurnedConsumer(BalanceProjectionService projection, ObjectMapper objectMapper) {
        this.projection = projection;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.POINTS_BURNED)
    public void onPointsBurned(String payload) {
        PointsBurned event = parse(payload);
        projection.applyBurned(event.eventId(), event.memberId(), event.points());
        log.debug("Applied PointsBurned event {} for member {}", event.eventId(), event.memberId());
    }

    private PointsBurned parse(String payload) {
        try {
            return objectMapper.readValue(payload, PointsBurned.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unparseable PointsBurned payload: " + payload, e);
        }
    }
}
