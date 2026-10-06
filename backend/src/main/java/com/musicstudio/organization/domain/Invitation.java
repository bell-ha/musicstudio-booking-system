package com.musicstudio.organization.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long organizationId;

    @Enumerated(EnumType.STRING)
    private MembershipRole role;

    private String tokenHash;

    private Instant expiresAt;

    private Instant usedAt;

    protected Invitation() {
    }

    public Invitation(Long organizationId, MembershipRole role, String tokenHash, Instant expiresAt) {
        this.organizationId = organizationId;
        this.role = role;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public MembershipRole getRole() {
        return role;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
