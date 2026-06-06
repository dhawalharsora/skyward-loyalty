package com.skyward.domain.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Inbox record: an event id that has already been handled by a consumer. Written in the same
 * transaction as the effect it guards (the balance update), so a redelivered event is recognised and
 * skipped — the mechanism that makes the consumer idempotent against at-least-once delivery.
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected ProcessedEvent() {
        // for JPA only
    }

    private ProcessedEvent(UUID eventId, OffsetDateTime processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public static ProcessedEvent of(UUID eventId) {
        return new ProcessedEvent(eventId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public UUID getEventId() {
        return eventId;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }
}
