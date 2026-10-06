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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.common.storage.FileStore;
import com.musicstudio.site.domain.SiteColors;
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
    private final FileStore files;
    private final Clock clock;

    SiteService(SiteProfileRepository profiles, SiteLogoRepository logos, FileStore files, Clock clock) {
        this.profiles = profiles;
        this.logos = logos;
        this.files = files;
        this.clock = clock;
    }

    /** 저장한 적이 없으면 기본값 (저장하지 않는다) */
    @Transactional(readOnly = true)
    public Site site(long orgId) {
        return new Site(profiles.findById(orgId).orElseGet(() -> new SiteProfile(orgId)), logos.findKey(orgId));
    }

    @Transactional
    public Site describe(long orgId, String intro, String address, String phone, String hoursText, String rawColor) {
        String color = SiteColors.normalize(rawColor)
                .orElseThrow(() -> ApiException.invalid("INVALID_COLOR", "기관 색은 목록의 색이나 #RRGGBB 형식으로 골라 주세요"));
        if (!SiteColors.readableWithWhite(color)) {
            throw ApiException.policyViolation("COLOR_TOO_LIGHT", "흰 글자가 잘 보이지 않는 밝은 색이에요. 조금 더 진한 색을 골라 주세요");
        }
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
        // 파일은 저장소에 먼저 올리고, DB에는 키만. 커밋되면 옛 파일을 지우고, 롤백되면 방금 올린 파일을 지운다
        String key = "orgs/" + orgId + "/logo/" + UUID.randomUUID();
        String[] old = new String[1];
        // 정리는 올리기 전에 등록한다: 올린 뒤 어디서 예외가 나도 롤백이면 새 파일이 지워진다 (리뷰 31 1-4)
        afterCompletion(committed -> files.delete(committed ? old[0] : key));
        files.put(key, bytes, type);
        SiteLogo logo = logos.findForUpdate(orgId).orElseGet(() -> new SiteLogo(orgId));
        old[0] = logo.store(type, key, clock.instant());
        try {
            return logos.saveAndFlush(logo).getLogoKey();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("TRY_AGAIN", "잠시 후 다시 시도해 주세요"); // 같은 기관이 동시에 처음 올렸다
        }
    }

    @Transactional
    public void deleteLogo(long orgId) {
        logos.findForUpdate(orgId).ifPresent(logo -> {
            String key = logo.getStorageKey();
            logos.delete(logo);
            afterCompletion(committed -> files.delete(committed ? key : null));
        });
    }

    /**
     * 공개 로고: 저장소에 있으면 저장소에서, 0015 시절 것은 DB에서.
     * 트랜잭션을 두지 않는다: 저장소를 읽는 동안 DB 커넥션을 쥐고 있지 않게 (리뷰 31 1-5)
     */
    public Optional<FileStore.StoredFile> logo(UUID key) {
        return logos.findByLogoKey(key).flatMap(l -> l.getStorageKey() == null
                ? Optional.of(new FileStore.StoredFile(l.getBytes(), l.getContentType()))
                : files.get(l.getStorageKey()).map(f -> new FileStore.StoredFile(f.bytes(), l.getContentType())));
    }

    /** 트랜잭션이 끝난 뒤 (커밋 여부를 받아) 저장소를 정리한다. 키가 없으면 할 일이 없다 */
    private static void afterCompletion(java.util.function.Consumer<Boolean> cleanup) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                cleanup.accept(status == STATUS_COMMITTED);
            }
        });
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
