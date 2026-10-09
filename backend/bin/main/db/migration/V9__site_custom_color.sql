-- 기관 색: 미리 정한 6색만 받던 것을 원장이 고른 아무 색(#RRGGBB)도 받게 넓힌다.
-- 흰 글자 대비(4.5:1)는 SQL로 계산하기 번거로워서 애플리케이션(SiteColors)이 확인한다. DB는 형식만 지킨다.
ALTER TABLE site_profile DROP CONSTRAINT site_profile_color_check;
ALTER TABLE site_profile ADD CONSTRAINT site_profile_color_check
    CHECK (color IN ('INDIGO', 'BLUE', 'SKY', 'VIOLET', 'PLUM', 'NAVY') OR color ~ '^#[0-9A-F]{6}$');
