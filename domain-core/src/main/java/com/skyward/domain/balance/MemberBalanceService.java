package com.skyward.domain.balance;

import com.skyward.domain.member.MemberNotFoundException;
import com.skyward.domain.member.MemberRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a member's balance from the materialized {@code member_balance} read-model (O(1)), maintained
 * by the projection consumer. A member with no balance row yet reads as zero; an unknown member is a
 * 404. (Day 1 derived this on read by summing the ledger; Day 2 materializes it — the ledger remains
 * the source of truth and the read-model can be rebuilt from it.)
 */
@Service
public class MemberBalanceService {

    private final MemberRepository members;
    private final MemberBalanceRepository balances;

    public MemberBalanceService(MemberRepository members, MemberBalanceRepository balances) {
        this.members = members;
        this.balances = balances;
    }

    @Transactional(readOnly = true)
    public BalanceResponse balanceFor(UUID memberId) {
        if (!members.existsById(memberId)) {
            throw new MemberNotFoundException(memberId);
        }
        long balance = balances.findById(memberId)
                .map(MemberBalance::getBalance)
                .orElse(0L);
        return new BalanceResponse(memberId, balance, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
