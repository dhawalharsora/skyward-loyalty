package com.skyward.domain.tier;

import com.skyward.domain.member.Member;
import com.skyward.domain.member.MemberNotFoundException;
import com.skyward.domain.member.MemberRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a member's tier from the {@link Member} aggregate (the source of truth for tier). Unlike balance,
 * tier is a strongly-consistent field on the member row, so this is a direct lookup — no projection.
 * An unknown member is a 404 ({@link MemberNotFoundException}), matching the balance endpoint's contract.
 */
@Service
public class MemberTierService {

    private final MemberRepository members;

    public MemberTierService(MemberRepository members) {
        this.members = members;
    }

    @Transactional(readOnly = true)
    public TierResponse tierFor(UUID memberId) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        return new TierResponse(
                member.getId(), member.getTier(), "domain", OffsetDateTime.now(ZoneOffset.UTC));
    }
}
