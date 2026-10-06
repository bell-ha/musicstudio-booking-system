package com.musicstudio.site.application;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.site.domain.SiteColor;
import com.musicstudio.site.domain.SiteLogo;
import com.musicstudio.site.domain.SiteLogoRepository;
import com.musicstudio.site.domain.SiteProfile;
import com.musicstudio.site.domain.SiteProfileRepository;

/** 기관 사이트 꾸미기와 공개 설정 (UC-10). 로고는 ADR 0015. */
@Service
public class SiteService {

    public static final int MAX_LOGO_BYTES = 200 * 1024;

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$");

    /** 화면 경로·서비스 이름과 헷갈리는 주소 */
    private static final Set<String> RESERVED = Set.of("admin", "api", "s", "login", "signup", "join", "invite",
            "orgs", "organizations", "auth", "public", "assets", "static", "www", "help", "support", "settings",
            "me", "new", "musicstudio");

    private final SiteProfileRepository profiles;
    private final SiteLogoRepository logos;
    private final Clock clock;

    SiteService(SiteProfileRepository profiles, SiteLogoRepository logos, Clock clock) {
        this.profiles = profiles;
        this.logos = logos;
        this.clock = clock;
    }

    /** 저장한 적이 없으면 기본값 (저장하지 않는다) */
    @Transactional(readOnly = true)
    public Site site(long orgId) {
        return new Site(profiles.findById(orgId).orElseGet(() -> new SiteProfile(orgId)), logos.findKey(orgId));
    }

    @Transactional
    public Site describe(long orgId, String intro, String address, String phone, String hoursText, SiteColor color) {
        SiteProfile profile = profiles.findById(orgId).orElseGet(() -> new SiteProfile(orgId));
        profile.describe(intro, address, phone, hoursText, color, clock.instant());
        return new Site(saveProfile(profile), logos.findKey(orgId));
    }

    @Transactional
    public Site publish(long orgId, String rawSlug, boolean published, boolean acceptJoin) {
        String slug = rawSlug == null || rawSlug.isBlank() ? null : rawSlug.trim().toLowerCase();
        if (slug != null && !SLUG.matcher(slug).matches()) {
            throw ApiException.invalid("INVALID_SLUG", "주소는 영문 소문자·숫자·하이픈 3~30자로, 하이픈으로 시작하거나 끝나지 않게 써 주세요");
        }
        if (slug != null && RESERVED.contains(slug)) {
            throw ApiException.invalid("SLUG_RESERVED", "쓸 수 없는 주소입니다");
        }
        if (published && slug == null) {
            throw ApiException.policyViolation("SLUG_REQUIRED", "공개하려면 주소를 정해 주세요");
        }
        SiteProfile profile = profiles.findById(orgId).orElseGet(() -> new SiteProfile(orgId));
        profile.publish(slug, published, acceptJoin, clock.instant());
        return new Site(saveProfile(profile), logos.findKey(orgId));
    }

    /**
     * 형식은 Content-Type이 아니라 파일 앞부분(매직 바이트)으로 정한다. 선언은 "이미지인가"만 본다:
     * 확장자만 .jpg로 바뀐 PNG(메신저·캡처 도구에서 흔하다)를 브라우저는 image/jpeg로 보내지만 내용은 정상이다.
     * 저장·응답하는 형식은 내용에서 알아낸 것이라 nosniff와도 맞다.
     * SVG는 스크립트를 담을 수 있어서 받지 않는다 (매직 바이트 목록에 없다).
     */
    @Transactional
    public UUID replaceLogo(long orgId, String declaredType, byte[] bytes) {
        String declared = declaredType == null ? "" : declaredType.split(";")[0].trim().toLowerCase();
        if (!declared.startsWith("image/")) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "UNSUPPORTED_IMAGE",
                    "PNG, JPEG, WebP 이미지만 올릴 수 있습니다");
        }
        if (bytes.length > MAX_LOGO_BYTES) {
            throw tooLarge();
        }
        String type = sniff(bytes);
        if (type == null) {
            throw ApiException.invalid("INVALID_IMAGE", "PNG, JPEG, WebP 이미지만 올릴 수 있습니다");
        }
        SiteLogo logo = logos.findById(orgId).orElseGet(() -> new SiteLogo(orgId));
        logo.replace(type, bytes, clock.instant());
        try {
            return logos.saveAndFlush(logo).getLogoKey();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("TRY_AGAIN", "잠시 후 다시 시도해 주세요"); // 같은 기관이 동시에 처음 올렸다
        }
    }

    @Transactional
    public void deleteLogo(long orgId) {
        logos.deleteById(orgId);
    }

    @Transactional(readOnly = true)
    public Optional<SiteLogo> logo(UUID key) {
        return logos.findByLogoKey(key);
    }

    public static ApiException tooLarge() {
        return new ApiException(HttpStatus.CONTENT_TOO_LARGE, "payload-too-large", "IMAGE_TOO_LARGE",
                "200KB 이하 이미지만 올릴 수 있습니다");
    }

    /** PNG 89 50 4E 47 0D 0A 1A 0A, JPEG FF D8 FF, WebP "RIFF" ???? "WEBP" */
    static String sniff(byte[] b) {
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] b, int offset, int... expected) {
        if (b.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((b[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private SiteProfile saveProfile(SiteProfile profile) {
        try {
            return profiles.saveAndFlush(profile);
        } catch (DataIntegrityViolationException e) {
            // 제약 이름으로 나눈다. 주소 중복이 아니면 같은 기관의 첫 저장이 동시에 일어나 PK가 겹친 것이다
            throw String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_site_profile_slug")
                    ? ApiException.conflict("SLUG_TAKEN", "이미 쓰고 있는 주소입니다")
                    : ApiException.conflict("TRY_AGAIN", "잠시 후 다시 시도해 주세요");
        }
    }

    public record Site(SiteProfile profile, Optional<UUID> logoKey) {
    }
}
