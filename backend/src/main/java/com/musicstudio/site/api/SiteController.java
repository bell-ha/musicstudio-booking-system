package com.musicstudio.site.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.organization.api.CurrentMember;
import com.musicstudio.organization.api.OrgRole;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.site.application.NoticeService;
import com.musicstudio.site.application.SiteService;
import com.musicstudio.site.domain.Notice;
import com.musicstudio.site.domain.NoticeVisibility;
import com.musicstudio.site.domain.SiteColor;
import com.musicstudio.site.domain.SiteProfile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 기관 사이트 꾸미기와 공지 (API 37~45). 모듈 접두사가 없어서 모든 기관에서 쓴다. */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}")
class SiteController {

    private final SiteService sites;
    private final NoticeService notices;

    SiteController(SiteService sites, NoticeService notices) {
        this.sites = sites;
        this.notices = notices;
    }

    // ---------- 사이트 (37~41) ----------

    @GetMapping("/site")
    @OrgRole
    SiteResponse site(CurrentMember me) {
        return SiteResponse.of(sites.site(me.organizationId()), me.role());
    }

    @PutMapping("/site")
    @OrgRole(MembershipRole.MANAGER)
    SiteResponse describe(CurrentMember me, @Valid @RequestBody DescribeRequest req) {
        return SiteResponse.of(sites.describe(me.organizationId(), clean(req.intro()), clean(req.address()),
                clean(req.phone()), clean(req.hoursText()), req.color()), me.role());
    }

    /** 공개 주소, 공개 여부, 가입 받기는 바깥에 기관을 여는 일이라 소유자만 */
    @PutMapping("/site/publishing")
    @OrgRole(MembershipRole.OWNER)
    SiteResponse publish(CurrentMember me, @Valid @RequestBody PublishRequest req) {
        return SiteResponse.of(sites.publish(me.organizationId(), req.slug(), req.published(), req.acceptJoin()),
                me.role());
    }

    /**
     * 본문 = 이미지 바이트. {@code @RequestBody byte[]}는 크기와 상관없이 전부 메모리에 읽으므로(max-request-size는
     * multipart에만 적용된다) 길이를 먼저 보고, 200KB + 1바이트까지만 읽는다.
     */
    @PutMapping("/site/logo")
    @OrgRole(MembershipRole.MANAGER)
    LogoResponse replaceLogo(CurrentMember me, HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > SiteService.MAX_LOGO_BYTES) {
            throw SiteService.tooLarge();
        }
        byte[] bytes = request.getInputStream().readNBytes(SiteService.MAX_LOGO_BYTES + 1);
        UUID key = sites.replaceLogo(me.organizationId(), request.getContentType(), bytes);
        return new LogoResponse(logoUrl(key));
    }

    @DeleteMapping("/site/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @OrgRole(MembershipRole.MANAGER)
    void deleteLogo(CurrentMember me) {
        sites.deleteLogo(me.organizationId());
    }

    // ---------- 공지 (42~45) ----------

    @GetMapping("/notices")
    @OrgRole
    List<NoticeResponse> notices(CurrentMember me) {
        return notices.list(me.organizationId(), me.role()).stream().map(NoticeResponse::of).toList();
    }

    @PostMapping("/notices")
    @ResponseStatus(HttpStatus.CREATED)
    @OrgRole(MembershipRole.MANAGER)
    NoticeResponse createNotice(CurrentMember me, @Valid @RequestBody NoticeRequest req) {
        return NoticeResponse.of(notices.create(me.organizationId(), me.membershipId(), req.title(), req.body(),
                req.visibility(), req.pin()));
    }

    @PatchMapping("/notices/{noticeId}")
    @OrgRole(MembershipRole.MANAGER)
    NoticeResponse updateNotice(CurrentMember me, @PathVariable long noticeId, @Valid @RequestBody NoticeRequest req) {
        return NoticeResponse.of(notices.update(me.organizationId(), noticeId, req.title(), req.body(),
                req.visibility(), req.pin()));
    }

    @DeleteMapping("/notices/{noticeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @OrgRole(MembershipRole.MANAGER)
    void deleteNotice(CurrentMember me, @PathVariable long noticeId) {
        notices.delete(me.organizationId(), noticeId);
    }

    static String logoUrl(UUID key) {
        return "/api/v1/public/logos/" + key;
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }

    record DescribeRequest(@Size(max = 2000) String intro, @Size(max = 200) String address,
                           @Size(max = 30) String phone, @Size(max = 200) String hoursText,
                           @NotNull SiteColor color) {
    }

    record PublishRequest(String slug, @NotNull Boolean published, @NotNull Boolean acceptJoin) {
    }

    record LogoResponse(String logoUrl) {
    }

    /**
     * published·slug는 관리자 이상에게(읽기 전용, "공개 페이지에도 바로 보여요" 안내용), acceptJoin은 소유자에게만.
     * 그 밖의 역할에게는 null이다.
     */
    record SiteResponse(String logoUrl, String intro, String address, String phone, String hoursText,
                        SiteColor color, Boolean published, String slug, Boolean acceptJoin) {

        static SiteResponse of(SiteService.Site site, MembershipRole role) {
            SiteProfile p = site.profile();
            boolean manager = role == MembershipRole.OWNER || role == MembershipRole.MANAGER;
            boolean owner = role == MembershipRole.OWNER;
            return new SiteResponse(site.logoKey().map(SiteController::logoUrl).orElse(null), p.getIntro(),
                    p.getAddress(), p.getPhone(), p.getHoursText(), p.getColor(),
                    manager ? p.isPublished() : null, manager ? p.getSlug() : null, owner ? p.isAcceptJoin() : null);
        }
    }

    record NoticeRequest(@NotBlank @Size(max = 100) String title, @NotNull @Size(max = 5000) String body,
                         @NotNull NoticeVisibility visibility, Boolean pinned) {

        /** 빼면 고정하지 않는다 */
        boolean pin() {
            return Boolean.TRUE.equals(pinned);
        }
    }

    record NoticeResponse(Long id, String title, String body, NoticeVisibility visibility, boolean pinned,
                          Instant createdAt, Instant updatedAt) {

        static NoticeResponse of(Notice n) {
            return new NoticeResponse(n.getId(), n.getTitle(), n.getBody(), n.getVisibility(), n.isPinned(),
                    n.getCreatedAt(), n.getUpdatedAt());
        }
    }
}
