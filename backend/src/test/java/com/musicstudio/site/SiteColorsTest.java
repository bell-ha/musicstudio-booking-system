package com.musicstudio.site;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.musicstudio.site.domain.SiteColors;

/** 기관 색 규칙: 키나 #RRGGBB, 흰 글자 대비 4.5:1 이상 */
class SiteColorsTest {

    @Test
    void 키와_색을_대문자로_맞추고_형식이_틀리면_비어_있다() {
        assertThat(SiteColors.normalize("blue")).contains("BLUE");
        assertThat(SiteColors.normalize(" #1d4ed8 ")).contains("#1D4ED8");
        assertThat(SiteColors.normalize("GREEN")).isEmpty();
        assertThat(SiteColors.normalize("#12345")).isEmpty();
        assertThat(SiteColors.normalize("red; background:url(x)")).isEmpty();
    }

    @Test
    void 흰_글자가_안_보이는_밝은_색은_거절한다() {
        assertThat(SiteColors.readableWithWhite("#1D4ED8")).isTrue();   // 6.70
        assertThat(SiteColors.readableWithWhite("#767676")).isTrue();   // 4.54, 경계 바로 위
        assertThat(SiteColors.readableWithWhite("#777777")).isFalse();  // 4.48
        assertThat(SiteColors.readableWithWhite("#FFD400")).isFalse();  // 노랑
        assertThat(SiteColors.readableWithWhite("NAVY")).isTrue();
    }
}
