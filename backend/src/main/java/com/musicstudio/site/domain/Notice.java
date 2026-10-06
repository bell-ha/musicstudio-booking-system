package com.musicstudio.site.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** 공지. 관리자만 쓰는 한 방향 알림이다. 본문은 평문이고 화면이 이스케이프한다. */
@Entity
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long organizationId;

    private Long authorMembershipId;

    private String title;

    private String body;

    @Enumerated(EnumType.STRING)
    private NoticeVisibility visibility;

    private boolean pinned;

    private Instant createdAt;

    private Instant updatedAt;

    protected Notice() {
    }

    public Notice(Long organizationId, Long authorMembershipId, Instant now) {
        this.organizationId = organizationId;
        this.authorMembershipId = authorMembershipId;
        this.createdAt = now;
    }

    public void write(String title, String body, NoticeVisibility visibility, boolean pinned, Instant now) {
        this.title = title;
        this.body = body;
        this.visibility = visibility;
        this.pinned = pinned;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public NoticeVisibility getVisibility() {
        return visibility;
    }

    public boolean isPinned() {
        return pinned;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
