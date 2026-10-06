-- v2 MVP 스키마 초안 (PostgreSQL 17). 설명은 01-ERD.md. 테이블 12개.
-- ID는 bigint + 시퀀스 INCREMENT BY 50 (Hibernate pooled-lo, allocationSize = 50).
-- CHECK의 OR 분기에서 NULL일 수 있는 컬럼은 IS NOT NULL을 명시한다. CHECK 결과가 NULL이면 통과하기 때문이다.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ===================== 기본 =====================

CREATE SEQUENCE user_account_seq INCREMENT BY 50;
CREATE TABLE user_account (
    id            bigint      PRIMARY KEY DEFAULT nextval('user_account_seq'),
    email         text        NOT NULL,
    password_hash text        NOT NULL,
    name          text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_user_account_email ON user_account (lower(email));

CREATE SEQUENCE organization_seq INCREMENT BY 50;
CREATE TABLE organization (
    id                bigint      PRIMARY KEY DEFAULT nextval('organization_seq'),
    name              text        NOT NULL,
    type              text        NOT NULL CHECK (type IN ('ACADEMY', 'SCHOOL', 'OTHER')),
    timezone          text        NOT NULL,
    modules           text[]      NOT NULL CHECK (modules <@ ARRAY['PRACTICE_ROOM', 'ACADEMY']::text[]),
    practice_policy   jsonb,                       -- 연습실 모듈을 처음 켤 때 기본값으로 채운다
    join_code         text        NOT NULL UNIQUE, -- 원문 저장. 승인을 거치므로 해시하지 않는다
    join_code_enabled boolean     NOT NULL DEFAULT true,
    join_form         jsonb       NOT NULL DEFAULT '[]',  -- 가입 신청 때 받을 항목 (학번, 전공 등)
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE SEQUENCE membership_seq INCREMENT BY 50;
CREATE TABLE membership (
    id              bigint      PRIMARY KEY DEFAULT nextval('membership_seq'),
    organization_id bigint      NOT NULL REFERENCES organization (id),
    user_id         bigint      NOT NULL REFERENCES user_account (id),
    role            text        NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'TEACHER', 'STUDENT')),
    status          text        NOT NULL CHECK (status IN ('PENDING', 'REJECTED', 'ACTIVE', 'INACTIVE')),
    profile         jsonb       NOT NULL DEFAULT '{}',  -- 가입 신청 때 입력한 값
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, user_id)          -- UC-06 2b. 거절된 뒤 다시 신청하면 이 행을 PENDING으로 되돌린다
);
CREATE INDEX ix_membership_user ON membership (user_id);

CREATE SEQUENCE invitation_seq INCREMENT BY 50;
CREATE TABLE invitation (
    id              bigint      PRIMARY KEY DEFAULT nextval('invitation_seq'),
    organization_id bigint      NOT NULL REFERENCES organization (id),
    role            text        NOT NULL CHECK (role IN ('MANAGER', 'TEACHER', 'STUDENT')),
    token_hash      text        NOT NULL UNIQUE,   -- SHA-256. 원문은 만들 때 한 번만 응답
    expires_at      timestamptz NOT NULL,
    used_at         timestamptz
);

-- ===================== 연습실 =====================

CREATE SEQUENCE floor_seq INCREMENT BY 50;
CREATE TABLE floor (
    id              bigint  PRIMARY KEY DEFAULT nextval('floor_seq'),
    organization_id bigint  NOT NULL REFERENCES organization (id),
    name            text    NOT NULL,
    sort_order      integer NOT NULL DEFAULT 0,
    layout          jsonb   NOT NULL,              -- {"width":20,"height":12,"walls":[[x,y]],"corridors":[[x,y]]}
    version         bigint  NOT NULL DEFAULT 0     -- If-Match (ADR 0009)
);

CREATE SEQUENCE room_seq INCREMENT BY 50;
CREATE TABLE room (
    id              bigint   PRIMARY KEY DEFAULT nextval('room_seq'),
    organization_id bigint   NOT NULL REFERENCES organization (id),
    name            text     NOT NULL,
    capacity        smallint NOT NULL DEFAULT 1,
    equipment       text[]   NOT NULL DEFAULT '{}',
    bookable        boolean  NOT NULL DEFAULT true,  -- false = 점검 중
    -- 평면도 위 사각형. 평면도에서 빼면 다섯 컬럼 모두 NULL
    floor_id        bigint   REFERENCES floor (id),
    x smallint, y smallint, w smallint, h smallint,
    UNIQUE (organization_id, name),
    CHECK (num_nulls(floor_id, x, y, w, h) = 5
        OR (num_nulls(floor_id, x, y, w, h) = 0 AND x >= 0 AND y >= 0 AND w >= 1 AND h >= 1))
);
CREATE INDEX ix_room_floor ON room (floor_id);

CREATE SEQUENCE practice_booking_seq INCREMENT BY 50;
CREATE TABLE practice_booking (
    id                        bigint      PRIMARY KEY DEFAULT nextval('practice_booking_seq'),
    organization_id           bigint      NOT NULL REFERENCES organization (id),
    room_id                   bigint      NOT NULL REFERENCES room (id),
    member_id                 bigint      NOT NULL REFERENCES membership (id),
    starts_at                 timestamptz NOT NULL,
    ends_at                   timestamptz NOT NULL,
    during                    tstzrange   GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED,
    usage_date                date        NOT NULL,  -- 기관 현지 날짜. R4 합계와 잠금 키
    canceled_at               timestamptz,
    canceled_by_membership_id bigint      REFERENCES membership (id),
    cancel_reason             text,                  -- 관리자 강제 취소 사유
    created_at                timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at),
    CHECK ((canceled_at IS NULL) = (canceled_by_membership_id IS NULL)),
    -- R1 같은 방, R2 같은 사람. 취소된 예약은 빠진다
    CONSTRAINT ex_booking_room   EXCLUDE USING gist (room_id   WITH =, during WITH &&) WHERE (canceled_at IS NULL),
    CONSTRAINT ex_booking_member EXCLUDE USING gist (member_id WITH =, during WITH &&) WHERE (canceled_at IS NULL)
);
CREATE INDEX ix_booking_member_date ON practice_booking (member_id, usage_date) WHERE canceled_at IS NULL;
CREATE INDEX ix_booking_org_date    ON practice_booking (organization_id, usage_date) WHERE canceled_at IS NULL;

-- ===================== 학원 관리 =====================

CREATE SEQUENCE subject_seq INCREMENT BY 50;
CREATE TABLE subject (
    id              bigint PRIMARY KEY DEFAULT nextval('subject_seq'),
    organization_id bigint NOT NULL REFERENCES organization (id),
    name            text   NOT NULL,
    UNIQUE (organization_id, name)
);

CREATE SEQUENCE product_seq INCREMENT BY 50;
CREATE TABLE product (
    id              bigint   PRIMARY KEY DEFAULT nextval('product_seq'),
    organization_id bigint   NOT NULL REFERENCES organization (id),
    subject_id      bigint   NOT NULL REFERENCES subject (id),
    name            text     NOT NULL,
    kind            text     NOT NULL CHECK (kind IN ('COUNT', 'PERIOD')),
    session_count   smallint,  -- COUNT: N회
    period_months   smallint,  -- PERIOD: N개월
    lesson_minutes  smallint NOT NULL CHECK (lesson_minutes > 0),
    price           bigint   NOT NULL CHECK (price >= 0),
    CHECK ((kind = 'COUNT'  AND session_count IS NOT NULL AND session_count > 0 AND period_months IS NULL)
        OR (kind = 'PERIOD' AND period_months IS NOT NULL AND period_months > 0 AND session_count IS NULL))
);

CREATE SEQUENCE student_seq INCREMENT BY 50;
CREATE TABLE student (
    id                 bigint   PRIMARY KEY DEFAULT nextval('student_seq'),
    organization_id    bigint   NOT NULL REFERENCES organization (id),
    membership_id      bigint   UNIQUE REFERENCES membership (id),  -- 학생 계정이 있으면 연결
    name               text     NOT NULL,
    birth_year         smallint,
    phone_enc          bytea,                      -- AES-GCM 암호문 (NFR-04)
    guardian_name      text,
    guardian_phone_enc bytea,
    memo               text,
    active             boolean  NOT NULL DEFAULT true
);

CREATE SEQUENCE enrollment_seq INCREMENT BY 50;
CREATE TABLE enrollment (
    id                    bigint      PRIMARY KEY DEFAULT nextval('enrollment_seq'),
    organization_id       bigint      NOT NULL REFERENCES organization (id),
    student_id            bigint      NOT NULL REFERENCES student (id),
    product_id            bigint      NOT NULL REFERENCES product (id),
    teacher_membership_id bigint      NOT NULL REFERENCES membership (id),
    status                text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED', 'REFUNDED')),
    price                 bigint      NOT NULL,    -- 등록 시점 상품 가격 복사 (UC-42 규칙)
    starts_on             date        NOT NULL,
    ends_on               date,                    -- 기간권. 재개할 때 정지 일수만큼 늘린다
    total_sessions        smallint,                -- 횟수권
    paused_at             date,                    -- PAUSED일 때 정지 시작일
    created_at            timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(ends_on, total_sessions) = 1),
    CHECK ((status = 'PAUSED') = (paused_at IS NOT NULL))
);
CREATE INDEX ix_enrollment_student ON enrollment (student_id);
CREATE INDEX ix_enrollment_teacher ON enrollment (teacher_membership_id) WHERE status IN ('ACTIVE', 'PAUSED');

CREATE SEQUENCE lesson_record_seq INCREMENT BY 50;
CREATE TABLE lesson_record (
    id                   bigint      PRIMARY KEY DEFAULT nextval('lesson_record_seq'),
    organization_id      bigint      NOT NULL REFERENCES organization (id),
    enrollment_id        bigint      NOT NULL REFERENCES enrollment (id),
    author_membership_id bigint      NOT NULL REFERENCES membership (id),
    lesson_date          date        NOT NULL,
    progress             text,
    homework             text,
    memo                 text,
    visible_to_student   boolean     NOT NULL DEFAULT false,
    created_at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_lesson_record_enrollment ON lesson_record (enrollment_id, lesson_date);
