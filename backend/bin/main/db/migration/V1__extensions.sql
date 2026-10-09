-- 예약 겹침 배타 제약(ADR 0010)에서 bigint 같은 일반 타입을 gist 인덱스로 = 비교하려면 필요하다.
CREATE EXTENSION IF NOT EXISTS btree_gist;
