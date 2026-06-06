package com.skyward.domain.member;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Representation of an enrolled member returned by the enrolment endpoint. */
public record MemberResponse(UUID id, String fullName, Tier tier, OffsetDateTime enrolledAt) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(), member.getFullName(), member.getTier(), member.getEnrolledAt());
    }
}
