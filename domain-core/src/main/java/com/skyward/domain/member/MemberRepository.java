package com.skyward.domain.member;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Members are a mutable aggregate, so the full JpaRepository (incl. update/delete) is appropriate. */
public interface MemberRepository extends JpaRepository<Member, UUID> {

    /**
     * Loads a member with a pessimistic write lock (SELECT ... FOR UPDATE). Used by the redemption
     * reserve step to serialize concurrent redemptions for the same member, preventing double-spend:
     * a second reserve blocks until the first commits, so both see a consistent available balance.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findAndLockById(@Param("id") UUID id);
}
