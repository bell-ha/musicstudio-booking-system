package com.musicstudio.site.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 기관 로고 (ADR 0015). 기관당 한 장. 바꾸면 키도 새로 만들어서 옛 URL의 캐시가 섞이지 않는다. */
@Entity
public class SiteLogo {

    @Id
    private Long organizationId;

    private UUID logoKey;

    private String contentType;

    private byte[] bytes;

    private Instant updatedAt;

    protected SiteLogo() {
    }

    public SiteLogo(Long organizationId) {
        this.organizationId = organizationId;
    }

    public void replace(String contentType, byte[] bytes, Instant now) {
        this.logoKey = UUID.randomUUID();
        this.contentType = contentType;
        this.bytes = bytes;
        this.updatedAt = now;
    }

    public UUID getLogoKey() {
        return logoKey;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBytes() {
        return bytes;
    }
}
