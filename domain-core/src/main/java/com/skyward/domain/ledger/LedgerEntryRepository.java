package com.skyward.domain.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Repository for the append-only ledger.
 *
 * <p>It extends the bare {@link Repository} marker (not {@code JpaRepository} or {@code CrudRepository})
 * and exposes only {@code save} and read queries. There is deliberately no {@code delete*} or update
 * method: the append-only invariant is enforced by the interface itself, not by convention.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID> {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> findByMemberIdOrderByCreatedAtAsc(UUID memberId);

    /** Fast-path idempotency lookup: has this accrual already been recorded? */
    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

    /**
     * The balance projection: sum of EARNs minus BURNs for a member. COALESCE yields 0 when the member
     * has no entries. Native SQL keeps the CASE expression explicit and readable.
     */
    @Query(
            value = """
                    SELECT COALESCE(SUM(CASE WHEN entry_type = 'EARN' THEN amount ELSE -amount END), 0)
                    FROM ledger_entry
                    WHERE member_id = :memberId
                    """,
            nativeQuery = true)
    long balanceOf(@Param("memberId") UUID memberId);
}
