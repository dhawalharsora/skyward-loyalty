package com.skyward.legacy;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * The legacy system of record for tiers — an in-memory map standing in for the old SOA's database.
 *
 * <p>It is intentionally <em>independent</em> of the new domain service's Postgres: the two can hold
 * different tiers for the same member. That divergence is the whole point of the strangler's shadow-
 * compare step — it surfaces exactly the data drift a real migration must reconcile before cutover.
 */
@Repository
public class LegacyTierRepository {

    private final Map<String, String> tiersByMemberId = new ConcurrentHashMap<>();

    /** Returns the legacy tier for a member id, or empty if this system has never heard of them. */
    public Optional<String> findTier(String memberId) {
        return Optional.ofNullable(tiersByMemberId.get(memberId));
    }

    /** Seeds/overwrites the legacy tier for a member id (used to populate the old system's data). */
    public void put(String memberId, String tier) {
        tiersByMemberId.put(memberId, tier);
    }
}
