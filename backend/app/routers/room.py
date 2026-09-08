from typing import List
from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.orm import Session
from datetime import datetime, date, timedelta
from zoneinfo import ZoneInfo

from app.database import get_db
from app.models.room import Room
from app.models.booking import Booking
from app.models.policy import get_policy
from app.schemas.room import RoomCreate, RoomRead, RoomUpdate
from app.schemas.booking import TimeSlot
from app.routers.auth import get_current_user
from app.routers.user import get_current_admin_user
from app.services.overlap import time_overlap_clause

# /api/rooms 로 등록됨 (main.py에서 prefix="/api"를 주기 때문)
router = APIRouter(prefix="/rooms", tags=["rooms"])

# ─────────────────────────────────────────────
# 관리자 전용 의존성
# ─────────────────────────────────────────────
def admin_only(current_user=Depends(get_current_admin_user)):
    return current_user

# ─────────────────────────────────────────────
# 1) 방 생성 (관리자 전용)
# POST /api/rooms
# ─────────────────────────────────────────────
@router.post(
    "/",
    response_model=RoomRead,
    status_code=status.HTTP_201_CREATED,
    summary="방 생성",
)
def create_room(
    room_in: RoomCreate,
    db: Session = Depends(get_db),
    _: object = Depends(admin_only),
):
    # 같은 이름의 방이 이미 있는지 확인
    if db.query(Room).filter(Room.room_name == room_in.room_name).first():
        raise HTTPException(status_code=400, detail="이미 존재하는 방 이름입니다.")
    room = Room(
        room_name=room_in.room_name,
        floor=room_in.floor,
        pos_x=room_in.pos_x,
        pos_y=room_in.pos_y,
        state=room_in.state,
        equipment=room_in.equipment,
    )
    db.add(room)
    db.commit()
    db.refresh(room)
    return room

# ─────────────────────────────────────────────
# 2) 방 목록 조회 (로그인 필요)
# GET /api/rooms
# ─────────────────────────────────────────────
@router.get(
    "/",
    response_model=List[RoomRead],
    summary="전체 방 조회",
)
def list_rooms(
    db: Session = Depends(get_db),
    _: object = Depends(get_current_user),
):
    return db.query(Room).all()

# ─────────────────────────────────────────────
# 3) 특정 방 조회
# GET /api/rooms/{room_id}
# ─────────────────────────────────────────────
@router.get(
    "/{room_id}",
    response_model=RoomRead,
    summary="방 상세 조회",
)
def get_room(
    room_id: int,
    db: Session = Depends(get_db),
    _: object = Depends(get_current_user),
):
    room = db.get(Room, room_id)
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    return room

# ─────────────────────────────────────────────
# 4) 방 정보 수정 (관리자)
# PATCH /api/rooms/{room_id}
# ─────────────────────────────────────────────
@router.patch(
    "/{room_id}",
    response_model=RoomRead,
    summary="방 정보 수정",
)
def update_room(
    room_id: int,
    room_in: RoomUpdate,
    db: Session = Depends(get_db),
    _: object = Depends(admin_only),
):
    room = db.get(Room, room_id)
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    for field, val in room_in.dict(exclude_unset=True).items():
        setattr(room, field, val)
    db.commit()
    db.refresh(room)
    return room

# ─────────────────────────────────────────────
# 5) 방 삭제 (관리자)
# DELETE /api/rooms/{room_id}
# ─────────────────────────────────────────────
@router.delete(
    "/{room_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="방 삭제",
)
def delete_room(
    room_id: int,
    db: Session = Depends(get_db),
    _: object = Depends(admin_only),
):
    room = db.get(Room, room_id)
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    db.delete(room)
    db.commit()

# ─────────────────────────────────────────────
# 6) 방 슬롯(예약 가능 시간) 조회
# GET /api/rooms/{room_id}/slots?booking_date=YYYY-MM-DD
# ─────────────────────────────────────────────
@router.get(
    "/{room_id}/slots",
    response_model=List[TimeSlot],
    summary="방 시간표 조회 (주간 윈도우 자동 적용)",
)
def get_room_slots(
    room_id: int,
    booking_date: date = Query(..., description="예약 날짜 (YYYY-MM-DD)"),
    db: Session = Depends(get_db),
    _: object = Depends(get_current_user),
):
    policy = get_policy(db)
    seoul = ZoneInfo("Asia/Seoul")
    now = datetime.now(seoul)

    # 주간 오픈 시점 계산 — 요일(week_open_weekday)·시각(week_open_time)을
    # 정책에서 읽는다. 기존 코드는 "금요일 09:00"을 하드코딩해서
    # `friday_date + timedelta(days=3)`(금→다음 주 월) /
    # `friday_date - timedelta(days=4)`(금→이번 주 월) 처럼 숫자가
    # 금요일(weekday=4)에 종속돼 있었다. 요일이 W일 때:
    #   - 오픈 시점을 지났으면 "그 날짜 + (0-W)%7" = 다음 주 월요일
    #   - 아직이면 "그 날짜 - W" = 이번 주 월요일
    # 이 두 식은 W=4(금)를 넣으면 원래 값(+3 / -4)과 정확히 같다 —
    # 기본값에서 기존 동작(금요일 09:00 오픈, 다음 주 월~일 창)이
    # 그대로 재현되는지는 W=4 대입으로 확인했다.
    W = policy.week_open_weekday
    today_wd = now.weekday()  # Mon=0 … Sun=6
    days_to_open = (W - today_wd) % 7
    open_date = (now + timedelta(days=days_to_open)).date()
    open_dt = datetime.combine(open_date, policy.week_open_time, tzinfo=seoul)

    # 예약 오픈 윈도우
    if now >= open_dt:
        window_start = open_date + timedelta(days=(-W) % 7)  # 다음 주 월
    else:
        window_start = open_date - timedelta(days=W)          # 이번 주 월
    window_end = window_start + timedelta(days=6)

    if not (window_start <= booking_date <= window_end):
        return []

    room = db.get(Room, room_id)
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")

    # 운영 시간 · 슬롯 단위도 정책에서 읽는다 (기본값 09:00~23:00, 30분)
    start_dt = datetime.combine(booking_date, policy.open_time, tzinfo=seoul)
    end_dt = datetime.combine(booking_date, policy.close_time, tzinfo=seoul)

    slots: List[TimeSlot] = []
    current = start_dt
    while current < end_dt:
        next_dt = current + timedelta(minutes=policy.slot_minutes)
        conflict = db.query(Booking).filter(
            Booking.room_id == room_id,
            Booking.start_date <= booking_date,
            Booking.end_date >= booking_date,
            time_overlap_clause(current.time(), next_dt.time()),
        ).first() is not None

        past = (booking_date == now.date() and current < now)
        slots.append(TimeSlot(start=current, end=next_dt, available=not conflict and not past))
        current = next_dt

    return slots
