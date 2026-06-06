package com.skyward.domain.outbox.relay;

import com.skyward.domain.outbox.OutboxEvent;
import com.skyward.domain.outbox.OutboxEventRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polling publisher for the transactional outbox. This is the <b>infrastructure</b> corner of the
 * domain module (it talks to Kafka), deliberately fenced into the {@code outbox.relay} package.
 *
 * <h2>Crash-safety / at-least-once</h2>
 * On each tick, within one transaction, it locks a batch of unpublished rows with
 * {@code FOR UPDATE SKIP LOCKED}, publishes each to Kafka <em>synchronously</em> (waiting for the
 * broker ack), then stamps {@code published_at} and commits. The publish happens <b>before</b> the row
 * is marked published: if the process dies after publishing but before commit, the row stays
 * unpublished and is re-published on the next run. That makes delivery <b>at-least-once</b> — so
 * consumers must be idempotent. (Marking first then publishing would risk losing events on a crash —
 * worse for a points system.)
 *
 * <h2>Production note</h2>
 * Polling is the simple, dependency-free relay. The production-grade alternative is log-based CDC
 * (Debezium) tailing the Postgres WAL — captured in ADR-0001.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;

    public OutboxRelay(OutboxEventRepository outbox, KafkaTemplate<String, String> kafkaTemplate,
            @Value("${skyward.outbox.relay.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${skyward.outbox.relay.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outbox.lockUnpublishedBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent event : batch) {
            publish(event);
            // Managed entity: this UPDATE flushes at commit, after a confirmed publish.
            event.markPublished(OffsetDateTime.now(ZoneOffset.UTC));
        }
        log.debug("Outbox relay published {} event(s)", batch.size());
    }

    private void publish(OutboxEvent event) {
        try {
            // Synchronous: only proceed to mark published once the broker has acked the record.
            // The destination topic is carried on the row, so the relay handles any event type.
            kafkaTemplate
                    .send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing outbox event " + event.getId(), e);
        } catch (ExecutionException | TimeoutException e) {
            // Roll back: the row stays unpublished and is retried on the next poll.
            throw new IllegalStateException("Failed to publish outbox event " + event.getId(), e);
        }
    }
}
