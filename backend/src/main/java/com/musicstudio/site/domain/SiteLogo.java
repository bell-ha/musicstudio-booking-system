package com.musicstudio.site.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * 기관 로고. 기관당 한 장. 바꾸면 공개 키도 새로 만들어서 옛 URL의 캐시가 섞이지 않는다.
 * 파일은 저장소(ADR 0017)의 storageKey에 있다. bytes는 0015 시절 DB에 넣은 것을 읽기 위해서만 남는다.
 */
@Entity
public class SiteLogo {

    @Id
    private Long organizationId;

    private UUID logoKey;

    private String contentType;

    private byte[] bytes;

    private String storageKey;

    private Instant updatedAt;

    protected SiteLogo() {
    }

    public SiteLogo(Long organizationId) {
        this.organizationId = organizationId;
    }

    /** 새 파일을 저장소에 올린 뒤 부른다. 옛 저장소 키를 돌려준다(커밋 뒤 지우려고) */
    public String store(String contentType, String storageKey, Instant now) {
        String old = this.storageKey;
        this.logoKey = UUID.randomUUID();
        this.contentType = contentType;
        this.bytes = null;
        this.storageKey = storageKey;
        this.updatedAt = now;
        return old;
    }

    public String getStorageKey() {
        return storageKey;
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
