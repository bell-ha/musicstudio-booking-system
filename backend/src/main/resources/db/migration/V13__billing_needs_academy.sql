-- 청구·결제(BILLING)는 학원 관리(ACADEMY)가 있어야 켠다. 만들기·수정 어느 경로로도 어긋나지 않게 DB가 마지막으로 막는다
-- (서비스 검사는 Organization 한 곳에, 교차 리뷰 31 1-1)
ALTER TABLE organization ADD CONSTRAINT ck_organization_billing_needs_academy
    CHECK (NOT ('BILLING' = ANY (modules)) OR 'ACADEMY' = ANY (modules));
