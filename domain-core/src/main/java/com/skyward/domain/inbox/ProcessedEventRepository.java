package com.skyward.domain.inbox;

import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Inbox repository: just record an event id and check whether one was already processed. */
public interface ProcessedEventRepository extends Repository<ProcessedEvent, UUID> {

    boolean existsById(UUID eventId);

    ProcessedEvent save(ProcessedEvent event);
}
