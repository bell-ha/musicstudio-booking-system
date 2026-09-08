from datetime import date, time, datetime
from typing import Optional
from pydantic import BaseModel, ConfigDict
from app.schemas.user import UserRead
from app.schemas.room import RoomRead

class TimeSlot(BaseModel):
    start: datetime
    end: datetime
    available: bool

# "슬롯 단위(policy.slot_minutes) 배수인지" 검증은 여기 두지 않았다.
# 기준값이 DB(BookingPolicy)에 있어 동기 field_validator로는 못 읽고,
# BookingRead가 이 클래스를 상속해서 응답 직렬화에도 같은 규칙이 타면
# 정책 변경 전에 만들어진 기존 예약 조회가 실패할 수 있다. 검증은
# routers/booking.py의 create_booking(_on_slot_grid)에서 한다.
class BookingBase(BaseModel):
    start_date: date
    end_date:   date
    start_time: time
    end_time:   time

class BookingCreate(BookingBase):
    room_id: int

class BookingRead(BookingBase):
    booking_id: int
    user:       UserRead
    room:       RoomRead
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)

Booking = BookingRead  # alias for admin router
