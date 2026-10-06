package com.musicstudio.organization.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * 계정이 어느 기관에서 어떤 역할인지. 기관과 계정은 ID로만 가리킨다.
 * 가입 신청은 PENDING 상태의 멤버십이다.
 */
@Entity
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long organizationId;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    private MembershipStatus status;

    private Instant createdAt;

    protected Membership() {
    }

    private Membership(Long organizationId, Long userId, MembershipRole role, MembershipStatus status) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public static Membership owner(Long organizationId, Long userId) {
        return new Membership(organizationId, userId, MembershipRole.OWNER, MembershipStatus.ACTIVE);
    }

    public Long getId() {
        return id;
    }

    public MembershipRole getRole() {
        return role;
    }

    public MembershipStatus getStatus() {
        return status;
    }
}
