from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.booking import Booking
from app.schemas.booking import Booking as BookingSchema, BookingCreate
from app.routers.auth import get_current_admin
from app.services.locks import lock_room_date, is_deadlock
from app.services.overlap import time_overlap_clause

router = APIRouter(
    prefix="/admin/bookings",
    tags=["admin_bookings"],
    dependencies=[Depends(get_current_admin)]
)


def _dates_in_range(start_date, end_date):
    d = start_date
    while d <= end_date:
        yield d
        d += timedelta(days=1)


@router.get("/", response_model=list[BookingSchema])
def list_blocks(skip: int = 0, limit: int = 100, db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    blocks = (
        db.query(Booking)
        .offset(skip)
        .limit(limit)
        .all()
    )
    return blocks

@router.post("/", response_model=BookingSchema, status_code=status.HTTP_201_CREATED)
def create_block(block_in: BookingCreate, db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    if block_in.end_date < block_in.start_date or (block_in.end_date == block_in.start_date and block_in.end_time <= block_in.start_time):
        raise HTTPException(400, "끝날짜/끝시간이 시작보다 빨라요.")

    # booking.py의 create_booking과 같은 헬퍼(app.services.locks)를 써서
    # 같은 방(room_id)에 대해서는 학생 예약과 관리자 블록 생성이 서로
    # 상호 배제되게 한다 — 부호/키 규칙이 갈라지면 이 상호 배제가 깨진다.
    # 블록은 여러 날짜에 걸칠 수 있어(start_date != end_date) room-lock을
    # 그 범위의 모든 날짜에 대해, 항상 날짜 오름차순으로 건다(고정 순서
    # 없이 걸면 겹치는 범위를 가진 두 블록 생성 요청이 서로 다른 순서로
    # 잠가서 데드락을 만들 수 있다).
    try:
        for d in _dates_in_range(block_in.start_date, block_in.end_date):
            lock_room_date(db, block_in.room_id, d)

        conflict = db.query(Booking).filter(
            Booking.room_id    == block_in.room_id,
            Booking.start_date <= block_in.end_date,
            Booking.end_date   >= block_in.start_date,
            time_overlap_clause(block_in.start_time, block_in.end_time)
        ).first()
        if conflict:
            raise HTTPException(409, "겹치는 블록(예약)이 존재합니다.")
        new_block = Booking(
            user_id    = admin.user_id,
            room_id    = block_in.room_id,
            start_date = block_in.start_date,
            end_date   = block_in.end_date,
            start_time = block_in.start_time,
            end_time   = block_in.end_time
        )
        db.add(new_block)
        db.commit()
    except HTTPException:
        db.rollback()
        raise
    except OperationalError as exc:
        db.rollback()
        if is_deadlock(exc):
            raise HTTPException(409, "다른 요청과 충돌했습니다. 잠시 후 다시 시도해 주세요.") from exc
        raise

    db.refresh(new_block)
    return new_block

@router.delete("/{booking_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_block(booking_id: int, db: Session = Depends(get_db), admin=Depends(get_current_admin)):
    block = db.query(Booking).filter(Booking.booking_id == booking_id, Booking.user_id == admin.user_id).first()
    if not block:
        raise HTTPException(404, "블록(예약)을 찾을 수 없습니다.")
    db.delete(block)
    db.commit()
