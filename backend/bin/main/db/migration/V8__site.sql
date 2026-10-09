-- 기관 사이트 (FR-SITE-01~05, UC-10~14). site 모듈 소유의 테이블. organization 테이블은 건드리지 않는다.

-- 소개, 연락처, 색, 공개 주소. 기관당 한 행. 처음 저장할 때 만든다 (없으면 기본값으로 보여 준다).
CREATE TABLE site_profile (
    organization_id bigint      PRIMARY KEY REFERENCES organization (id),
    slug            text,                                -- 공개 주소 /s/{slug}. 공개하지 않으면 비어 있어도 된다
    published       boolean     NOT NULL DEFAULT false,
    accept_join     boolean     NOT NULL DEFAULT false,  -- 공개 페이지에서 가입 신청 받기. 가입 코드와 따로 켠다
    intro           text        NOT NULL DEFAULT '' CHECK (char_length(intro) <= 2000),
    address         text        NOT NULL DEFAULT '' CHECK (char_length(address) <= 200),
    phone           text        NOT NULL DEFAULT '' CHECK (char_length(phone) <= 30), -- 기관 대표 번호. 공개 정보라 암호화하지 않는다
    hours_text      text        NOT NULL DEFAULT '' CHECK (char_length(hours_text) <= 200), -- 운영 안내 문구. 예약 정책과 묶지 않는다
    color           text        NOT NULL DEFAULT 'INDIGO'
                                CHECK (color IN ('INDIGO', 'BLUE', 'SKY', 'VIOLET', 'PLUM', 'NAVY')),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CHECK (slug IS NULL OR slug ~ '^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$'),
    CHECK (NOT published OR slug IS NOT NULL)            -- 공개하려면 주소가 있어야 한다
);
CREATE UNIQUE INDEX uq_site_profile_slug ON site_profile (lower(slug)) WHERE slug IS NOT NULL;

-- 로고 (ADR 0015). 프로필을 읽을 때 바이트까지 끌려오지 않게 따로 둔다. 바꾸면 덮어쓴다.
CREATE TABLE site_logo (
    organization_id bigint      PRIMARY KEY REFERENCES organization (id),
    logo_key        uuid        NOT NULL UNIQUE,         -- 공개 URL. 바꿀 때마다 새로 만든다 (캐시 무효화 겸)
    content_type    text        NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp')),
    bytes           bytea       NOT NULL CHECK (octet_length(bytes) <= 204800),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

-- 공지. 관리자만 쓰는 한 방향 알림 (댓글·첨부·조회수 없음).
CREATE SEQUENCE notice_seq INCREMENT BY 50;
CREATE TABLE notice (
    id                   bigint      PRIMARY KEY DEFAULT nextval('notice_seq'),
    organization_id      bigint      NOT NULL REFERENCES organization (id),
    author_membership_id bigint      NOT NULL REFERENCES membership (id),
    title                text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 100),
    body                 text        NOT NULL CHECK (char_length(body) <= 5000),
    visibility           text        NOT NULL CHECK (visibility IN ('STAFF', 'MEMBERS', 'PUBLIC')),
    pinned               boolean     NOT NULL DEFAULT false,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL
);
CREATE INDEX ix_notice_list ON notice (organization_id, pinned DESC, created_at DESC);
