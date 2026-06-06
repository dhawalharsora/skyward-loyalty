package com.skyward.domain.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request to enrol a new member. {@code tier} is the starting tier; an unknown value fails JSON binding
 * (400) before it reaches the controller, and a blank name is rejected by {@code @NotBlank} (400) — so
 * invalid input never creates a half-formed member.
 */
public record EnrolMemberRequest(
        @NotBlank String fullName,
        @NotNull Tier tier) {}
