"""
방/사용자·날짜 단위 배타 잠금 — Postgres(Neon) advisory lock.

SELECT(충돌 검사) → INSERT(생성) 사이에 아무 잠금도 없어서 두 요청이
동시에 같은 시간대를 통과할 수 있는 문제(README가 스스로 인정했던
동시성 이슈)를 막기 위한 것이다. booking.py와 admin_booking.py가
반드시 "이 헬퍼"를 같이 import해서 써야 한다 — 두 파일이 각자 부호나
해시 규칙을 다르게 정하면 같은 room_id에 대해 서로 다른 키가 나와서
상호 배제가 안 된다.

── 설계 근거 ──
- 세션이 아니라 트랜잭션 레벨(`pg_advisory_xact_lock`)만 쓴다. Neon의
  연결 문자열이 PgBouncer transaction-mode pooler 엔드포인트(호스트에
  `-pooler`)라 물리 커넥션이 트랜잭션마다 바뀔 수 있다. session-level
  `pg_advisory_lock`은 "잠근 커넥션 = 나중에 푸는 커넥션"이 보장돼야
  하는데 transaction 모드에서는 그 보장이 깨진다. xact-level은
  COMMIT/ROLLBACK 시 자동 해제되므로 이 문제가 아예 없다.
- 키는 해시가 아니라 정확한 두 int32 쌍이다. `pg_advisory_xact_lock(int,
  int)`은 그 두 값을 그대로 키로 쓰지 해시하지 않는다. `date.toordinal()`은
  0001-01-01~9999-12-31 범위에서 1~3,652,059로, int32 안에 안전하게
  들어가고 날짜 간 충돌이 원천적으로 없다(전단사). room_id/user_id도
  autoincrement PK라 int32 안에 들어간다 — 그래서 해시 충돌을 고민할
  필요가 아예 없다.
- room_id는 항상 음수, user_id는 항상 양수로 걸어서 두 키스페이스가
  절대 겹치지 않게 했다(둘 다 자연수 PK라 0이 나오지 않는다).
- 락 획득 순서는 항상 room → user 로 고정한다(코드 전체에서 이 순서를
  지켜야 advisory lock 간 데드락 사이클이 생기지 않는다). 그래도 발생하면
  Postgres 데드락 감지기가 한쪽을 40P01로 중단시키는데, 그건 라우터에서
  잡아서 409로 변환한다(`is_deadlock`).
"""
from datetime import date

from sqlalchemy import text
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import Session

DEADLOCK_SQLSTATE = "40P01"


def lock_room_date(db: Session, room_id: int, on_date: date) -> None:
    """방+날짜 단위 배타 잠금. 현재 트랜잭션이 끝나면 자동 해제된다."""
    db.execute(
        text("SELECT pg_advisory_xact_lock(:k1, :k2)"),
        {"k1": -room_id, "k2": on_date.toordinal()},
    )


def lock_user_date(db: Session, user_id: int, on_date: date) -> None:
    """사용자+날짜 단위 배타 잠금. 현재 트랜잭션이 끝나면 자동 해제된다."""
    db.execute(
        text("SELECT pg_advisory_xact_lock(:k1, :k2)"),
        {"k1": user_id, "k2": on_date.toordinal()},
    )


def is_deadlock(exc: OperationalError) -> bool:
    """Postgres 데드락 감지(40P01)로 중단된 트랜잭션인지 확인한다."""
    orig = getattr(exc, "orig", None)
    return getattr(orig, "pgcode", None) == DEADLOCK_SQLSTATE
