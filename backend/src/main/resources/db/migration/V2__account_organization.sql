-- 계정, 기관, 멤버십. docs/architecture/01-ERD.md 참고.
-- ID 시퀀스는 INCREMENT BY 50 (Hibernate 기본 allocationSize와 맞춘다).

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
    practice_policy   jsonb,                              -- 비어 있으면 기본 정책을 쓴다
    join_code         text        NOT NULL UNIQUE,        -- 원문 저장. 승인을 거치므로 해시하지 않는다
    join_code_enabled boolean     NOT NULL DEFAULT true,
    join_form         jsonb       NOT NULL DEFAULT '[]',  -- 가입 신청 때 받을 항목
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE SEQUENCE membership_seq INCREMENT BY 50;
CREATE TABLE membership (
    id              bigint      PRIMARY KEY DEFAULT nextval('membership_seq'),
    organization_id bigint      NOT NULL REFERENCES organization (id),
    user_id         bigint      NOT NULL REFERENCES user_account (id),
    role            text        NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'TEACHER', 'STUDENT')),
    status          text        NOT NULL CHECK (status IN ('PENDING', 'REJECTED', 'ACTIVE', 'INACTIVE')),
    profile         jsonb       NOT NULL DEFAULT '{}',
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id, user_id)
);
CREATE INDEX ix_membership_user ON membership (user_id);
