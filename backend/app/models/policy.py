# backend/app/models/policy.py
"""
예약 정책 — 관리자가 화면에서 바꿀 수 있는 운영 규칙.

코드에 박혀 있던 값(최대 2시간, 취소 10분 전, 09:00~23:00, 금요일 오픈)을
DB로 옮겼다. 규칙이 바뀔 때마다 재배포하지 않아도 되게 하기 위함이다.
평면도 좌표를 cells 테이블에 둔 것과 같은 이유다.

단일 행(id=1)으로 운영한다.
"""
from datetime import datetime, time

from sqlalchemy import Column, Integer, String, Time, DateTime, CheckConstraint
from app.database import Base

# same_day_mode 허용값
# 자기 예약끼리 겹치는 것은 어느 모드에서든 막힌다(방 충돌 + 사용자 충돌 검사).
# 아래는 "겹침 외에 추가로 무엇을 더 제한할 것인가"를 정한다.
NO_OVERLAP  = "NO_OVERLAP"   # 겹치지만 않으면 순서 무관 (기본)
SEQUENTIAL  = "SEQUENTIAL"   # 이전 예약 종료 이후 시간대만. 더 이른 시간은 못 잡는다
ONE_PER_DAY = "ONE_PER_DAY"  # 하루 1건
SAME_DAY_MODES = (NO_OVERLAP, SEQUENTIAL, ONE_PER_DAY)

POLICY_ID = 1


class BookingPolicy(Base):
    __tablename__ = "booking_policies"

    id = Column(Integer, primary_key=True)

    # 같은 날 본인 재예약 규칙
    same_day_mode = Column(String(20), nullable=False, default=NO_OVERLAP)

    # 예약 1건의 최대 길이(분)
    max_minutes = Column(Integer, nullable=False, default=120)

    # 시작 몇 분 전까지 취소 가능한가
    cancel_deadline_min = Column(Integer, nullable=False, default=10)

    # 운영 시간
    open_time  = Column(Time, nullable=False, default=time(9, 0))
    close_time = Column(Time, nullable=False, default=time(23, 0))

    # 슬롯 단위(분)
    slot_minutes = Column(Integer, nullable=False, default=30)

    # 주간 예약 오픈 시점 (0=월 … 4=금 … 6=일)
    week_open_weekday = Column(Integer, nullable=False, default=4)
    week_open_time    = Column(Time,    nullable=False, default=time(9, 0))

    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    __table_args__ = (
        # 정책은 단 하나여야 한다. id=1만 허용해 "어느 행이 진짜인가"를
        # 애플리케이션이 아니라 DB가 보장하게 한다.
        CheckConstraint("id = 1", name="ck_policy_singleton"),
        CheckConstraint("close_time > open_time", name="ck_policy_open_before_close"),
        CheckConstraint("max_minutes > 0", name="ck_policy_max_minutes"),
        CheckConstraint("slot_minutes > 0", name="ck_policy_slot_minutes"),
        CheckConstraint("cancel_deadline_min >= 0", name="ck_policy_cancel_deadline"),
        CheckConstraint(
            "week_open_weekday BETWEEN 0 AND 6", name="ck_policy_weekday_range"
        ),
        CheckConstraint(
            "same_day_mode IN ('NO_OVERLAP', 'SEQUENTIAL', 'ONE_PER_DAY')",
            name="ck_policy_same_day_mode",
        ),
    )


def get_policy(db) -> BookingPolicy:
    """단일 정책 행을 반환한다. 없으면 기본값으로 만들어서 반환한다."""
    policy = db.get(BookingPolicy, POLICY_ID)
    if policy is None:
        policy = BookingPolicy(id=POLICY_ID)
        db.add(policy)
        db.commit()
        db.refresh(policy)
    return policy
