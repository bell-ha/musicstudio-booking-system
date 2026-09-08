from datetime import datetime
from sqlalchemy import (
    Column, Integer, ForeignKey, Date, Time, DateTime,
    CheckConstraint, Index, func,
)
from sqlalchemy.orm import relationship
from app.database import Base

class Booking(Base):
    __tablename__ = "bookings"

    booking_id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    user_id    = Column(Integer, ForeignKey("users.user_id"), nullable=False)
    room_id    = Column(Integer, ForeignKey("rooms.room_id"), nullable=False)

    # 단일 date → 기간: start_date, end_date
    start_date = Column(Date, nullable=False)
    end_date   = Column(Date, nullable=False)

    start_time = Column(Time, nullable=False)
    end_time   = Column(Time, nullable=False)
    # server_default가 있어야 ORM을 거치지 않는 INSERT에서도 값이 채워진다.
    # 기존 default=datetime.utcnow는 SQLAlchemy가 채우는 값이라 DB에는 DEFAULT가 없었고,
    # 원시 SQL INSERT가 NOT NULL 위반으로 실패했다.
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    user = relationship("User", back_populates="bookings")
    room = relationship("Room", back_populates="bookings")

    __table_args__ = (
        # 업무 규칙을 코드에만 두면 애플리케이션을 거치지 않는 경로(psql, 배치 스크립트,
        # 검증을 빠뜨린 새 API)로 모순된 행이 들어온다. 제약은 데이터에 붙여 둔다.
        CheckConstraint("end_time > start_time", name="ck_bookings_time_order"),
        CheckConstraint("end_date >= start_date", name="ck_bookings_date_order"),
        # 실제 조회 패턴에 맞춘 복합 인덱스. Postgres는 FK에 인덱스를 자동 생성하지 않는다.
        Index("ix_bookings_room_date", "room_id", "start_date"),
        Index("ix_bookings_user_date", "user_id", "start_date"),
    )
