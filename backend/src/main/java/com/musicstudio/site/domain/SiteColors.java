package com.musicstudio.site.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 기관 색 (FR-SITE-02). 미리 정한 6색의 키나 원장이 고른 아무 색(#RRGGBB)을 받는다.
 * 그 색 위에 흰 글자(주 버튼, 기관 머리)를 쓰므로 흰 글자 대비가 WCAG AA(4.5:1) 이상이어야 한다.
 * 화면이 너무 밝은 색을 자동으로 진하게 맞추지만, 화면을 거치지 않은 요청도 있으니 서버가 다시 확인한다.
 */
public final class SiteColors {

    public static final String DEFAULT = "INDIGO";
    public static final double MIN_CONTRAST = 4.5;

    private static final Set<String> PRESETS = Set.of("INDIGO", "BLUE", "SKY", "VIOLET", "PLUM", "NAVY");
    private static final Pattern HEX = Pattern.compile("^#[0-9A-F]{6}$");

    private SiteColors() {
    }

    /** 키는 대문자, 색은 #RRGGBB 대문자로 맞춘다. 형식이 틀리면 비어 있다 */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.strip().toUpperCase(Locale.ROOT);
        return PRESETS.contains(value) || HEX.matcher(value).matches() ? Optional.of(value) : Optional.empty();
    }

    /** 프리셋은 미리 확인했다 (DESIGN.md 표, 5.93~10.36) */
    public static boolean readableWithWhite(String normalized) {
        return PRESETS.contains(normalized) || contrastWithWhite(normalized) >= MIN_CONTRAST;
    }

    /** WCAG 2 대비: (흰색 휘도 + 0.05) / (색 휘도 + 0.05) */
    static double contrastWithWhite(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double l = 0.2126 * channel(rgb >> 16) + 0.7152 * channel(rgb >> 8) + 0.0722 * channel(rgb);
        return 1.05 / (l + 0.05);
    }

    private static double channel(int value) {
        double c = (value & 0xFF) / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
