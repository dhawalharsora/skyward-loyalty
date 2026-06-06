package com.skyward.domain.outbox;

import com.skyward.common.event.Topics;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A row in the transactional outbox. It is written in the <b>same DB transaction</b> as the business
 * change (the ledger entry), so the event-to-publish is committed atomically with the data that caused
 * it — solving the dual-write problem. A separate relay (Day 2.2) later publishes unpublished rows to
 * Kafka and stamps {@code publishedAt}.
 *
 * <p>{@code aggregateId} is the natural Kafka partition key (here, the member id), which preserves
 * per-member ordering. {@code payload} is the serialized event JSON; domain-core stays free of any
 * JSON library — the producer (business layer) does the serialization.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected OutboxEvent() {
        // for JPA only
    }

    private OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType,
            String topic, String payload, OffsetDateTime createdAt, OffsetDateTime publishedAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
    }

    /** Creates an unpublished PointsAccrued outbox row keyed by the member (aggregate) id. */
    public static OutboxEvent pointsAccrued(UUID memberId, String payload) {
        return event(memberId, "PointsAccrued", Topics.POINTS_ACCRUED, payload);
    }

    /** Creates an unpublished PointsBurned outbox row keyed by the member (aggregate) id. */
    public static OutboxEvent pointsBurned(UUID memberId, String payload) {
        return event(memberId, "PointsBurned", Topics.POINTS_BURNED, payload);
    }

    private static OutboxEvent event(UUID memberId, String eventType, String topic, String payload) {
        return new OutboxEvent(UUID.randomUUID(), "member", memberId, eventType, topic, payload,
                OffsetDateTime.now(ZoneOffset.UTC), null);
    }

    /** Marks this row as published at the given time. Called by the relay after a successful publish. */
    public void markPublished(OffsetDateTime when) {
        this.publishedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }
}
