-- 연습실 모듈: 층(평면도), 방, 예약. docs/architecture/01-ERD.md, ADR 0009, ADR 0010.

CREATE SEQUENCE floor_seq INCREMENT BY 50;
CREATE TABLE floor (
    id              bigint  PRIMARY KEY DEFAULT nextval('floor_seq'),
    organization_id bigint  NOT NULL REFERENCES organization (id),
    name            text    NOT NULL,
    sort_order      integer NOT NULL DEFAULT 0,
    layout          jsonb   NOT NULL,          -- {"width":20,"height":12,"walls":[[x,y]],"corridors":[[x,y]]}
    version         bigint  NOT NULL DEFAULT 0 -- 평면도 저장의 If-Match (ADR 0009)
);
CREATE INDEX ix_floor_organization ON floor (organization_id);

CREATE SEQUENCE room_seq INCREMENT BY 50;
CREATE TABLE room (
    id              bigint   PRIMARY KEY DEFAULT nextval('room_seq'),
    organization_id bigint   NOT NULL REFERENCES organization (id),
    name            text     NOT NULL,
    capacity        smallint NOT NULL DEFAULT 1,
    equipment       text[]   NOT NULL DEFAULT '{}',
    bookable        boolean  NOT NULL DEFAULT true,  -- false = 점검 중
    -- 평면도 위 사각형. 평면도에서 빼면 다섯 컬럼이 모두 NULL
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
    usage_date                date        NOT NULL,  -- 기관 현지 날짜. 하루 한도(R4) 합계와 잠금 키
    canceled_at               timestamptz,
    canceled_by_membership_id bigint      REFERENCES membership (id),
    cancel_reason             text,
    created_at                timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at),
    CHECK ((canceled_at IS NULL) = (canceled_by_membership_id IS NULL)),
    -- R1 같은 방, R2 같은 사람의 유효한 예약끼리 겹치지 않는다. 앱에 버그가 있어도 DB가 막는다.
    CONSTRAINT ex_booking_room   EXCLUDE USING gist (room_id   WITH =, during WITH &&) WHERE (canceled_at IS NULL),
    CONSTRAINT ex_booking_member EXCLUDE USING gist (member_id WITH =, during WITH &&) WHERE (canceled_at IS NULL)
);
CREATE INDEX ix_booking_member_date ON practice_booking (member_id, usage_date) WHERE canceled_at IS NULL;
