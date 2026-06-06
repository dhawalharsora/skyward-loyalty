package com.skyward.domain.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A loyalty program member. Mutable aggregate (tier can change over time), unlike the ledger.
 *
 * <p>Instances are created via the {@link #enrol} factory so an id and enrolment timestamp always
 * exist; the protected no-arg constructor exists only for JPA/Hibernate.
 */
@Entity
@Table(name = "member")
public class Member {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    private Tier tier;

    @Column(name = "enrolled_at", nullable = false)
    private OffsetDateTime enrolledAt;

    protected Member() {
        // for JPA only
    }

    private Member(UUID id, String fullName, Tier tier, OffsetDateTime enrolledAt) {
        this.id = id;
        this.fullName = fullName;
        this.tier = tier;
        this.enrolledAt = enrolledAt;
    }

    /** Enrols a new member at the given tier, assigning a fresh id and UTC enrolment time. */
    public static Member enrol(String fullName, Tier tier) {
        return new Member(UUID.randomUUID(), fullName, tier, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public Tier getTier() {
        return tier;
    }

    public OffsetDateTime getEnrolledAt() {
        return enrolledAt;
    }
}
