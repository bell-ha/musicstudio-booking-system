package com.musicstudio.site.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

/** 기관 사이트 정보. 기관당 한 행이고 PK가 기관 ID다. 처음 저장할 때 만든다. */
@Entity
public class SiteProfile {

    @Id
    private Long organizationId;

    private String slug;

    private boolean published;

    private boolean acceptJoin;

    private String intro = "";

    private String address = "";

    private String phone = "";

    private String hoursText = "";

    @Enumerated(EnumType.STRING)
    private SiteColor color = SiteColor.INDIGO;

    private Instant updatedAt;

    protected SiteProfile() {
    }

    /** 아직 저장한 적 없는 기관의 기본값 */
    public SiteProfile(Long organizationId) {
        this.organizationId = organizationId;
    }

    public void describe(String intro, String address, String phone, String hoursText, SiteColor color, Instant now) {
        this.intro = intro;
        this.address = address;
        this.phone = phone;
        this.hoursText = hoursText;
        this.color = color;
        this.updatedAt = now;
    }

    public void publish(String slug, boolean published, boolean acceptJoin, Instant now) {
        this.slug = slug;
        this.published = published;
        this.acceptJoin = acceptJoin;
        this.updatedAt = now;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public String getSlug() {
        return slug;
    }

    public boolean isPublished() {
        return published;
    }

    public boolean isAcceptJoin() {
        return acceptJoin;
    }

    public String getIntro() {
        return intro;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getHoursText() {
        return hoursText;
    }

    public SiteColor getColor() {
        return color;
    }
}
