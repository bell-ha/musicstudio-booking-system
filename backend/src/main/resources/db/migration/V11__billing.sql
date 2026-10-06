-- 수납 관리 (FR-PAY-01~05, UC-60~64). 설계: 두 세션 합본(비공개 계획 문서 30).
-- 돈은 학원 밖(계좌, 카드 단말기)에서 오가고 앱은 청구와 입금을 기록한다. PG는 붙이지 않는다.

-- 청구·결제 모듈 플래그. 켜려면 학원 관리가 필요하다(서비스가 검사)
ALTER TABLE organization DROP CONSTRAINT organization_modules_check;
ALTER TABLE organization ADD CONSTRAINT organization_modules_check
    CHECK (modules <@ ARRAY['PRACTICE_ROOM', 'ACADEMY', 'BILLING']::text[]);

CREATE SEQUENCE invoice_seq INCREMENT BY 50;
CREATE TABLE invoice (
    id                       bigint      PRIMARY KEY DEFAULT nextval('invoice_seq'),
    organization_id          bigint      NOT NULL REFERENCES organization (id),
    student_id               bigint      NOT NULL REFERENCES student (id),
    enrollment_id            bigint      REFERENCES enrollment (id),          -- 교재비 등은 비어 있다
    auto                     boolean     NOT NULL DEFAULT false,              -- 수강 등록 때 자동으로 만든 것
    title                    text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 100),
    amount                   bigint      NOT NULL CHECK (amount > 0),         -- 원
    -- 납부 − 환불 (취소 안 된 장부의 합). 캐시지만 CHECK가 걸려 있어 초과 납부·음수는 저장될 수 없다
    paid_amount              bigint      NOT NULL DEFAULT 0,
    due_date                 date        NOT NULL,
    created_by_membership_id bigint      REFERENCES membership (id),          -- 자동 청구서는 비어 있다
    created_at               timestamptz NOT NULL,
    voided_at                timestamptz,
    void_reason              text,
    CONSTRAINT ck_invoice_paid CHECK (paid_amount >= 0 AND paid_amount <= amount),
    CHECK ((voided_at IS NULL) = (void_reason IS NULL)),
    CHECK (voided_at IS NULL OR paid_amount = 0)                              -- 납부가 남은 청구서는 무효로 못 한다
);
CREATE UNIQUE INDEX uq_invoice_auto ON invoice (enrollment_id) WHERE auto;   -- 자동 청구서는 수강당 하나
CREATE INDEX ix_invoice_student ON invoice (student_id, created_at DESC);
CREATE INDEX ix_invoice_unpaid ON invoice (organization_id, due_date) WHERE voided_at IS NULL AND paid_amount < amount;

-- 장부: 추가만 한다. 잘못 적은 것은 지우지 않고 취소(voided_at)한다
CREATE SEQUENCE payment_seq INCREMENT BY 50;
CREATE TABLE payment (
    id                        bigint      PRIMARY KEY DEFAULT nextval('payment_seq'),
    organization_id           bigint      NOT NULL REFERENCES organization (id),
    invoice_id                bigint      NOT NULL REFERENCES invoice (id),
    kind                      text        NOT NULL CHECK (kind IN ('PAYMENT', 'REFUND')),
    method                    text        NOT NULL CHECK (method IN ('CASH', 'TRANSFER', 'CARD', 'OTHER')),
    amount                    bigint      NOT NULL CHECK (amount > 0),        -- 환불도 양수, kind로 방향을 정한다
    paid_on                   date        NOT NULL,                           -- 실제로 받은(돌려준) 날, 기관 현지
    memo                      text        CHECK (char_length(memo) <= 200),
    request_id                uuid        NOT NULL,                           -- 멱등 키 (화면이 입금 시트를 열 때 만듦)
    recorded_by_membership_id bigint      NOT NULL REFERENCES membership (id),
    recorded_at               timestamptz NOT NULL,
    voided_at                 timestamptz,
    void_reason               text,
    voided_by_membership_id   bigint      REFERENCES membership (id),
    UNIQUE (organization_id, request_id),
    CHECK ((voided_at IS NULL) = (void_reason IS NULL)),
    CHECK ((voided_at IS NULL) = (voided_by_membership_id IS NULL))
);
CREATE INDEX ix_payment_invoice ON payment (invoice_id, recorded_at);
