package com.skyward.domain.member;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Member enrolment. Returns 201 Created with a {@code Location} of the new member's tier resource — the
 * read the strangler facade fronts — so a demo script can enrol a member and immediately route to it.
 * Balance and tier <em>reads</em> live in their own controllers; this owns the create.
 */
@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberEnrolmentService enrolment;

    public MemberController(MemberEnrolmentService enrolment) {
        this.enrolment = enrolment;
    }

    @PostMapping
    public ResponseEntity<MemberResponse> enrol(@Valid @RequestBody EnrolMemberRequest request) {
        Member member = enrolment.enrol(request.fullName(), request.tier());
        URI location = URI.create("/members/" + member.getId() + "/tier");
        return ResponseEntity.created(location).body(MemberResponse.from(member));
    }
}
