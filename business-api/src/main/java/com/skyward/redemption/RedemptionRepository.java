package com.skyward.redemption;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persisted saga state. Mutable (status transitions), so the full JpaRepository is appropriate. */
public interface RedemptionRepository extends JpaRepository<Redemption, UUID> {

    Optional<Redemption> findByIdempotencyKey(String idempotencyKey);

    /** Sum of points currently held by a member's in-flight redemptions (the active holds). */
    @Query("""
            SELECT COALESCE(SUM(r.points), 0)
            FROM Redemption r
            WHERE r.memberId = :memberId AND r.status IN :statuses
            """)
    long sumPointsByMemberIdAndStatusIn(
            @Param("memberId") UUID memberId,
            @Param("statuses") Collection<RedemptionStatus> statuses);

    /** Used by the recovery scheduler to find sagas that are mid-flight. */
    List<Redemption> findByStatusIn(Collection<RedemptionStatus> statuses);

    /**
     * Recovery query: mid-flight sagas not touched since {@code cutoff}. The age filter keeps recovery
     * from racing the live request, which resolves a saga within milliseconds.
     */
    List<Redemption> findByStatusInAndUpdatedAtBefore(
            Collection<RedemptionStatus> statuses, OffsetDateTime cutoff);
}
