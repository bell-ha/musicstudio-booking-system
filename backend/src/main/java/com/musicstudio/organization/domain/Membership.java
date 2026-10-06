package com.musicstudio.organization.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * 계정이 어느 기관에서 어떤 역할인지. 기관과 계정은 ID로만 가리킨다.
 * 가입 신청은 PENDING 상태의 멤버십이다.
 *
 * 상태 전이: PENDING → ACTIVE | REJECTED, ACTIVE → INACTIVE, INACTIVE → ACTIVE.
 * 거절된 사람이 다시 신청하면 같은 행이 REJECTED → PENDING이 된다.
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

    /** 가입 신청 때 입력한 값 (학번, 전공 등). */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> profile = new HashMap<>();

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

    public static Membership invited(Long organizationId, Long userId, MembershipRole role) {
        return new Membership(organizationId, userId, role, MembershipStatus.ACTIVE);
    }

    public static Membership applied(Long organizationId, Long userId, Map<String, String> profile) {
        Membership m = new Membership(organizationId, userId, MembershipRole.STUDENT, MembershipStatus.PENDING);
        m.profile = new HashMap<>(profile);
        return m;
    }

    /** 초대는 관리자의 의사이므로 대기·거절 상태였어도 바로 활성이 된다. */
    public void acceptInvitation(MembershipRole role) {
        this.role = role;
        this.status = MembershipStatus.ACTIVE;
    }

    public void reapply(Map<String, String> profile) {
        this.status = MembershipStatus.PENDING;
        this.profile = new HashMap<>(profile);
    }

    /** 허용되지 않은 전이면 false. */
    public boolean changeStatus(MembershipStatus next) {
        boolean allowed = switch (status) {
            case PENDING -> next == MembershipStatus.ACTIVE || next == MembershipStatus.REJECTED;
            case ACTIVE -> next == MembershipStatus.INACTIVE;
            case INACTIVE -> next == MembershipStatus.ACTIVE;
            case REJECTED -> false;
        };
        if (allowed) {
            this.status = next;
        }
        return allowed;
    }

    /** 활성 멤버만 역할을 바꿀 수 있다. */
    public boolean changeRole(MembershipRole next) {
        if (status != MembershipStatus.ACTIVE) {
            return false;
        }
        this.role = next;
        return true;
    }

    public boolean isActiveOwner() {
        return role == MembershipRole.OWNER && status == MembershipStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getUserId() {
        return userId;
    }

    public MembershipRole getRole() {
        return role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public Map<String, String> getProfile() {
        return profile;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
