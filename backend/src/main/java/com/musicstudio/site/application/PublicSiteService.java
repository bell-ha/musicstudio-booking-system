package com.musicstudio.site.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.organization.application.JoinService;
import com.musicstudio.organization.domain.JoinField;
import com.musicstudio.organization.domain.Organization;
import com.musicstudio.organization.domain.OrganizationRepository;
import com.musicstudio.site.domain.Notice;
import com.musicstudio.site.domain.SiteLogoRepository;
import com.musicstudio.site.domain.SiteProfile;
import com.musicstudio.site.domain.SiteProfileRepository;

/**
 * 로그인 없이 보는 공개 소개 페이지 (UC-13)와 그 페이지에서의 가입 신청 (UC-14).
 * 없는 주소와 공개하지 않은 사이트는 같은 404다. 가입 코드는 내보내지 않는다.
 */
@Service
public class PublicSiteService {

    private final SiteProfileRepository profiles;
    private final SiteLogoRepository logos;
    private final OrganizationRepository organizations;
    private final NoticeService notices;
    private final JoinService joins;

    PublicSiteService(SiteProfileRepository profiles, SiteLogoRepository logos, OrganizationRepository organizations,
                      NoticeService notices, JoinService joins) {
        this.profiles = profiles;
        this.logos = logos;
        this.organizations = organizations;
        this.notices = notices;
        this.joins = joins;
    }

    @Transactional(readOnly = true)
    public PublicSite site(String slug) {
        SiteProfile profile = published(slug);
        Organization org = organizations.findById(profile.getOrganizationId()).orElseThrow();
        boolean joinOpen = joinOpen(profile, org);
        return new PublicSite(org, profile, logos.findKey(org.getId()), notices.publicNotices(org.getId()),
                joinOpen, joinOpen ? org.getJoinForm() : List.of());
    }

    @Transactional
    public JoinService.Applied apply(long userId, String slug, Map<String, String> answers) {
        SiteProfile profile = published(slug);
        Organization org = organizations.findById(profile.getOrganizationId()).orElseThrow();
        if (!joinOpen(profile, org)) {
            throw notFound();
        }
        return joins.applyToOrganization(userId, org.getId(), answers);
    }

    /** 코드 가입(코드를 받은 사람)과 공개 가입(누구나)은 입구 크기가 달라서 따로 켠다. 코드 가입이 꺼지면 둘 다 닫힌다 */
    private static boolean joinOpen(SiteProfile profile, Organization org) {
        return profile.isAcceptJoin() && org.isJoinCodeEnabled();
    }

    private SiteProfile published(String slug) {
        return profiles.findPublished(slug == null ? "" : slug).orElseThrow(PublicSiteService::notFound);
    }

    private static ApiException notFound() {
        return ApiException.notFound("찾을 수 없는 페이지입니다");
    }

    public record PublicSite(Organization organization, SiteProfile profile, Optional<UUID> logoKey,
                             List<Notice> notices, boolean joinOpen, List<JoinField> joinForm) {
    }
}
