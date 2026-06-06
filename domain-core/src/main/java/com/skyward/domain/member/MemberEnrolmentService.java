package com.skyward.domain.member;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enrols new members. Thin by design — enrolment is a single aggregate write via the {@code Member.enrol}
 * factory (which assigns the id and UTC enrolment time), so the invariant "a member always has an id, a
 * name, and a tier" lives in the aggregate, not here.
 */
@Service
public class MemberEnrolmentService {

    private final MemberRepository members;

    public MemberEnrolmentService(MemberRepository members) {
        this.members = members;
    }

    @Transactional
    public Member enrol(String fullName, Tier tier) {
        return members.save(Member.enrol(fullName, tier));
    }
}
