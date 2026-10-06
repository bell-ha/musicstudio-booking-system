package com.musicstudio.site.domain;

import java.time.Instant;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

/**
 * 기관 사이트 정보. 기관당 한 행이고 PK가 기관 ID다. 처음 저장할 때 만든다.
 * 꾸미기(관리자)와 공개 설정(소유자)은 같은 행의 서로 다른 칸을 고친다. 바뀐 칸만 UPDATE해야(@DynamicUpdate)
 * 동시에 저장했을 때 나중 커밋이 먼저 읽은 낡은 값으로 상대 칸을 되돌리지 않는다. 같은 칸을 둘이 고치는 일은 없어서 @Version은 두지 않는다.
 */
@Entity
@DynamicUpdate
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
