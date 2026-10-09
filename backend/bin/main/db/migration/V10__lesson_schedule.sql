-- 레슨 일정·출결 (FR-AC-12~17, UC-49~53). 설계: 두 세션 합본(고정 일정 → 회차 미리 생성 → 출결).

-- 고정 주간 일정. 수강당 1~3행. 회차를 다시 만들 때의 원본이다. 현지 요일·시각으로 둔다(DST에도 같은 현지 시각).
CREATE SEQUENCE lesson_slot_seq INCREMENT BY 50;
CREATE TABLE lesson_slot (
    id            bigint   PRIMARY KEY DEFAULT nextval('lesson_slot_seq'),
    enrollment_id bigint   NOT NULL REFERENCES enrollment (id),
    day_of_week   smallint NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),   -- ISO, 1 = 월
    start_time    time     NOT NULL,
    UNIQUE (enrollment_id, day_of_week, start_time)
);

-- 회차. 수강이 끝날 때까지 미리 만든다(배치 없음).
CREATE SEQUENCE lesson_session_seq INCREMENT BY 50;
CREATE TABLE lesson_session (
    id                      bigint      PRIMARY KEY DEFAULT nextval('lesson_session_seq'),
    organization_id         bigint      NOT NULL REFERENCES organization (id),
    enrollment_id           bigint      NOT NULL REFERENCES enrollment (id),
    student_id              bigint      NOT NULL REFERENCES student (id),        -- 원생 겹침 제약용 (수강에서 복사)
    teacher_membership_id   bigint      NOT NULL REFERENCES membership (id),     -- 강사를 바꾸면 앞으로의 회차만 바뀐다
    kind                    text        NOT NULL CHECK (kind IN ('REGULAR', 'MAKEUP')),
    starts_at               timestamptz NOT NULL,
    ends_at                 timestamptz NOT NULL,
    during                  tstzrange   GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED,
    local_date              date        NOT NULL,                                -- 기관 현지 날짜
    status                  text        NOT NULL CHECK (status IN ('SCHEDULED', 'ATTENDED', 'ABSENT', 'EXCUSED', 'CANCELED')),
    note                    text        CHECK (char_length(note) <= 500),         -- 결석 메모, 휴강 사유
    marked_by_membership_id bigint      REFERENCES membership (id),
    marked_at               timestamptz,
    CHECK (ends_at > starts_at),
    CHECK ((status = 'SCHEDULED') = (marked_at IS NULL)),
    CHECK (status <> 'CANCELED' OR note IS NOT NULL),                             -- 휴강은 사유 필수
    -- 시간을 차지하는 회차만 겹침을 막는다. 사전 결석·휴강한 시간은 비어서 보강이 들어갈 수 있다.
    -- 마지막 방어선이다. 앞에서 LessonLocks(수강 → 강사 → 원생)로 줄을 세우고 앱이 먼저 겹침을 찾는다 (ADR 0010)
    CONSTRAINT ex_session_teacher EXCLUDE USING gist (teacher_membership_id WITH =, during WITH &&)
        WHERE (status IN ('SCHEDULED', 'ATTENDED', 'ABSENT')),
    CONSTRAINT ex_session_student EXCLUDE USING gist (student_id WITH =, during WITH &&)
        WHERE (status IN ('SCHEDULED', 'ATTENDED', 'ABSENT'))
);
CREATE INDEX ix_session_org_date ON lesson_session (organization_id, local_date);
CREATE INDEX ix_session_teacher_date ON lesson_session (teacher_membership_id, local_date);
CREATE INDEX ix_session_enrollment ON lesson_session (enrollment_id, starts_at);
