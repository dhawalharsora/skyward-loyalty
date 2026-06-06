package com.skyward.domain.balance;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The materialized balance is a mutable read-model, so the full JpaRepository is appropriate. */
public interface MemberBalanceRepository extends JpaRepository<MemberBalance, UUID> {
}
