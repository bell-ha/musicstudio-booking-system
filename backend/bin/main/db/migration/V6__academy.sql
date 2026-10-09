-- 학원 관리 모듈: 과목, 수업 상품, 원생, 수강, 레슨 기록. docs/architecture/01-ERD.md.

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
    session_count   smallint,  -- 횟수권: N회
    period_months   smallint,  -- 기간권: N개월
    lesson_minutes  smallint NOT NULL CHECK (lesson_minutes > 0),
    price           bigint   NOT NULL CHECK (price >= 0),
    -- OR 분기의 NULL 함정: 비교 결과가 NULL이면 CHECK가 통과하므로 IS NOT NULL을 명시한다
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
    phone_enc          bytea,                      -- AES-256-GCM 암호문 (NFR-04)
    guardian_name      text,
    guardian_phone_enc bytea,
    memo               text,
    active             boolean  NOT NULL DEFAULT true
);
CREATE INDEX ix_student_organization ON student (organization_id);

CREATE SEQUENCE enrollment_seq INCREMENT BY 50;
CREATE TABLE enrollment (
    id                    bigint      PRIMARY KEY DEFAULT nextval('enrollment_seq'),
    organization_id       bigint      NOT NULL REFERENCES organization (id),
    student_id            bigint      NOT NULL REFERENCES student (id),
    product_id            bigint      NOT NULL REFERENCES product (id),
    teacher_membership_id bigint      NOT NULL REFERENCES membership (id),
    status                text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED', 'REFUNDED')),
    price                 bigint      NOT NULL,    -- 등록 시점 상품 가격 복사 (UC-42)
    starts_on             date        NOT NULL,
    ends_on               date,                    -- 기간권. 재개할 때 정지 일수만큼 늘린다
    total_sessions        smallint,                -- 횟수권
    paused_at             date,                    -- PAUSED일 때 정지 시작일
    version               bigint      NOT NULL DEFAULT 0,  -- 관리자 둘의 동시 변경
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
