package com.musicstudio.site.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.common.security.AccessTokens;
import com.musicstudio.organization.application.JoinService;
import com.musicstudio.organization.domain.JoinField;
import com.musicstudio.organization.domain.MembershipStatus;
import com.musicstudio.organization.domain.OrganizationType;
import com.musicstudio.site.application.PublicSiteService;
import com.musicstudio.site.application.SiteService;
import com.musicstudio.site.domain.Notice;
import com.musicstudio.site.domain.SiteLogo;
import com.musicstudio.site.domain.SiteProfile;

/**
 * 공개 소개 페이지 (API 46~48). 46·47은 로그인 없이 부른다(SecurityConfig의 /api/v1/public/**).
 * 응답은 정해 둔 항목만 담은 DTO다. 엔티티를 그대로 내보내지 않는다.
 */
@RestController
class PublicSiteController {

    private final PublicSiteService publicSites;
    private final SiteService sites;

    PublicSiteController(PublicSiteService publicSites, SiteService sites) {
        this.publicSites = publicSites;
        this.sites = sites;
    }

    @GetMapping("/api/v1/public/sites/{slug}")
    PublicSiteResponse site(@PathVariable String slug) {
        return PublicSiteResponse.of(publicSites.site(slug));
    }

    /** 키가 바뀌면 URL도 바뀌므로 오래 캐시해도 된다. nosniff: 선언한 형식 밖으로 해석하지 않게 */
    @GetMapping("/api/v1/public/logos/{logoKey}")
    ResponseEntity<byte[]> logo(@PathVariable String logoKey) {
        UUID key;
        try {
            key = UUID.fromString(logoKey);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("찾을 수 없습니다");
        }
        SiteLogo logo = sites.logo(key).orElseThrow(() -> ApiException.notFound("찾을 수 없습니다"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(logo.getBytes());
    }

    /** 가입 코드 없이 주소로 신청한다 (UC-14). 응답은 가입 코드 신청(API 10)과 같다 */
    @PostMapping("/api/v1/sites/{slug}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    JoinResponse apply(@AuthenticationPrincipal Jwt jwt, @PathVariable String slug,
                       @RequestBody(required = false) JoinRequest request) {
        JoinService.Applied applied = publicSites.apply(AccessTokens.userId(jwt), slug,
                request == null ? Map.of() : request.answers());
        return new JoinResponse(applied.membership().getOrganizationId(), applied.organizationName(),
                applied.membership().getStatus());
    }

    record JoinRequest(Map<String, String> answers) {
    }

    record JoinResponse(Long organizationId, String organizationName, MembershipStatus status) {
    }

    record PublicSiteResponse(String name, OrganizationType type, String logoUrl, String intro, String address,
                              String phone, String hoursText, String color, List<PublicNotice> notices,
                              Join join) {

        static PublicSiteResponse of(PublicSiteService.PublicSite s) {
            SiteProfile p = s.profile();
            return new PublicSiteResponse(s.organization().getName(), s.organization().getType(),
                    s.logoKey().map(SiteController::logoUrl).orElse(null), p.getIntro(), p.getAddress(),
                    p.getPhone(), p.getHoursText(), p.getColor(),
                    s.notices().stream().map(PublicNotice::of).toList(), new Join(s.joinOpen(), s.joinForm()));
        }
    }

    record PublicNotice(Long id, String title, String body, boolean pinned, Instant createdAt) {

        static PublicNotice of(Notice n) {
            return new PublicNotice(n.getId(), n.getTitle(), n.getBody(), n.isPinned(), n.getCreatedAt());
        }
    }

    record Join(boolean open, List<JoinField> form) {
    }
}
