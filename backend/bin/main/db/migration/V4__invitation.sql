-- 초대 링크 (UC-04, UC-05). 토큰 원문은 만들 때 한 번만 응답하고 SHA-256만 저장한다.
CREATE SEQUENCE invitation_seq INCREMENT BY 50;
CREATE TABLE invitation (
    id              bigint      PRIMARY KEY DEFAULT nextval('invitation_seq'),
    organization_id bigint      NOT NULL REFERENCES organization (id),
    role            text        NOT NULL CHECK (role IN ('MANAGER', 'TEACHER', 'STUDENT')),
    token_hash      text        NOT NULL UNIQUE,
    expires_at      timestamptz NOT NULL,
    used_at         timestamptz
);
