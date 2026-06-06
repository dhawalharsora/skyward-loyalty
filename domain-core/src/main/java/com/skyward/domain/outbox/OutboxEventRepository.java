package com.skyward.domain.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Repository for the transactional outbox. */
public interface OutboxEventRepository extends Repository<OutboxEvent, UUID> {

    OutboxEvent save(OutboxEvent event);

    List<OutboxEvent> findByAggregateId(UUID aggregateId);

    /**
     * The relay's poll query. Selects the oldest unpublished rows and locks them with
     * {@code FOR UPDATE SKIP LOCKED}: each concurrent relay instance grabs a disjoint set of rows
     * (rows already locked by another instance are skipped), so no event is published twice across
     * instances. Must be called within a transaction; the locks are held until commit.
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublishedBatch(@Param("batchSize") int batchSize);
}
