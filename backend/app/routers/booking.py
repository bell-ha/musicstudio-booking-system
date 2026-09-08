from typing import List
from fastapi import APIRouter, Depends, HTTPException, status, Request
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy.orm import Session
from sqlalchemy.exc import OperationalError
from datetime import datetime, time as time_
from zoneinfo import ZoneInfo
from pathlib import Path

from app.database import get_db
from app.models.booking import Booking
from app.models.policy import get_policy, SEQUENTIAL, ONE_PER_DAY, NO_OVERLAP
from app.schemas.booking import BookingCreate, BookingRead
from app.schemas.user import UserRead
from app.schemas.room import RoomRead
from app.routers.auth import get_current_user
from app.services.locks import lock_room_date, lock_user_date, is_deadlock
from app.services.overlap import time_overlap_clause

# ===========================
# 📌 Jinja2 템플릿 경로: frontend 폴더
# ===========================
BASE_DIR = Path(__file__).resolve().parent.parent.parent.parent  # 저장소 루트
TEMPLATE_DIR = BASE_DIR / "frontend"
templates = Jinja2Templates(directory=str(TEMPLATE_DIR))

# ===========================
# 📌 Router 설정
# ===========================
router = APIRouter(prefix="/bookings", tags=["bookings"])


def _on_slot_grid(t: time_, slot_minutes: int) -> bool:
    """예약 시각이 정책의 슬롯 단위(slot_minutes) 배수인지 확인한다.

    정시(minute==0) 고정이 아니라 slot_minutes 배수로 검사하는 이유는
    관리자가 슬롯 단위를 30분/60분 등으로 바꿀 수 있기 때문이다(정책은
    DB에서 읽는다). 이 검사는 schemas/booking.py가 아니라 여기(라우터
    레벨)에 둔다 — 이유는 파일 하단 참고.
    """
    return t.second == 0 and t.microsecond == 0 and t.minute % slot_minutes == 0


# =========================================================
# ✅ HTML 페이지로 현재 로그인한 사용자의 예약 리스트 보기
# =========================================================
@router.get("/my-booking-list", response_class=HTMLResponse, summary="현재 사용자 예약 내역 HTML 페이지")
async def my_booking_list(
    request: Request,
    current_user=Depends(get_current_user),
    db: Session = Depends(get_db)
):
    my_bookings = db.query(Booking).filter(
        Booking.user_id == current_user.user_id
    ).order_by(Booking.start_date, Booking.start_time).all()

    return templates.TemplateResponse(
        "user_booking_list.html",
        {"request": request, "bookings": my_bookings}
    )

# =========================================================
# ✅ JSON API: 현재 로그인한 사용자의 예약 리스트 조회
# =========================================================
@router.get(
    "/me",
    response_model=List[BookingRead],
    summary="현재 로그인한 사용자의 예약 리스트 조회"
)
def get_my_bookings(
    db: Session = Depends(get_db),
    current_user=Depends(get_current_user)
):
    bookings = db.query(Booking).filter(
        Booking.user_id == current_user.user_id
    ).order_by(Booking.start_date, Booking.start_time).all()

    return [
        BookingRead(
            booking_id=b.booking_id,
            start_date=b.start_date,
            end_date=b.end_date,
            start_time=b.start_time,
            end_time=b.end_time,
            user=UserRead.from_orm(b.user),
            room=RoomRead.from_orm(b.room),
            created_at=b.created_at
        ) for b in bookings
    ]

# =========================================================
# ✅ JSON API: 전체 예약 리스트 (관리자 전용)
# =========================================================
@router.get(
    "/",
    response_model=List[BookingRead],
    summary="전체 예약 리스트 (관리자 전용)"
)
def get_all_bookings(
    db: Session = Depends(get_db),
    current_user=Depends(get_current_user)
):
    # 관리자 권한 체크
    if current_user.role != "admin":
        raise HTTPException(status_code=403, detail="관리자만 접근 가능합니다.")
    bookings = db.query(Booking).order_by(Booking.start_date, Booking.start_time).all()
    return [
        BookingRead(
            booking_id=b.booking_id,
            start_date=b.start_date,
            end_date=b.end_date,
            start_time=b.start_time,
            end_time=b.end_time,
            user=UserRead.from_orm(b.user),
            room=RoomRead.from_orm(b.room),
            created_at=b.created_at
        ) for b in bookings
    ]

# =========================================================
# ✅ 예약 생성
# =========================================================
@router.post(
    "/",
    response_model=BookingRead,
    status_code=status.HTTP_201_CREATED,
    summary="예약 생성"
)
def create_booking(
    booking_in: BookingCreate,
    db: Session = Depends(get_db),
    current_user=Depends(get_current_user)
):
    policy = get_policy(db)
    seoul_tz = ZoneInfo("Asia/Seoul")
    now = datetime.now(seoul_tz)

    dt_start = datetime.combine(booking_in.start_date, booking_in.start_time, tzinfo=seoul_tz)
    dt_end   = datetime.combine(booking_in.end_date, booking_in.end_time, tzinfo=seoul_tz)

    if dt_end <= dt_start:
        raise HTTPException(status_code=400, detail="종료 시간이 시작 시간보다 빨라요.")
    if (dt_end - dt_start).total_seconds() > policy.max_minutes * 60:
        raise HTTPException(status_code=400, detail=f"최대 {policy.max_minutes}분까지만 예약할 수 있습니다.")
    if not _on_slot_grid(booking_in.start_time, policy.slot_minutes) or not _on_slot_grid(booking_in.end_time, policy.slot_minutes):
        raise HTTPException(status_code=400, detail=f"예약 시각은 {policy.slot_minutes}분 단위여야 합니다.")

    # ── ②~④: room-lock → user-lock 획득 후 검사. 락 이후 실패로 끝나는
    # 모든 경로(HTTPException/DB 오류)는 반드시 rollback해서 advisory
    # lock을 즉시 반환한다 — 커밋 없이 세션만 닫히는 경로에 기대지 않는다.
    try:
        lock_room_date(db, booking_in.room_id, booking_in.start_date)
        lock_user_date(db, current_user.user_id, booking_in.start_date)

        if policy.same_day_mode != NO_OVERLAP:
            last = db.query(Booking).filter(
                Booking.user_id == current_user.user_id,
                Booking.start_date == booking_in.start_date
            ).order_by(Booking.end_time.desc()).first()

            if last:
                if policy.same_day_mode == ONE_PER_DAY:
                    raise HTTPException(
                        status_code=400,
                        detail="같은 날짜에 이미 예약이 있어 추가로 예약할 수 없습니다."
                    )
                # SEQUENTIAL: 새 예약의 "시작 시각"이 본인의 그날 마지막 예약
                # "종료 시각" 이후여야 한다. 예전에는 여기서 now(현재 벽시계
                # 시각)와 비교해서, 미래 날짜 예약이면 사실상 하루 1건으로
                # 막히고 ④(다른 방 동시 점유 검사)가 항상 도달 불가능했다.
                last_end = datetime.combine(last.end_date, last.end_time, tzinfo=seoul_tz)
                if dt_start < last_end:
                    raise HTTPException(
                        status_code=400,
                        detail="같은 날짜에 이미 예약이 있어 이전 예약의 종료 시각 이후에만 재예약할 수 있습니다."
                    )

        conflict = db.query(Booking).filter(
            Booking.room_id == booking_in.room_id,
            Booking.start_date == booking_in.start_date,
            time_overlap_clause(booking_in.start_time, booking_in.end_time)
        ).first()
        if conflict:
            raise HTTPException(status_code=400, detail="해당 시간에 이미 다른 사용자의 예약이 있습니다.")

        user_conflict = db.query(Booking).filter(
            Booking.user_id == current_user.user_id,
            Booking.start_date == booking_in.start_date,
            time_overlap_clause(booking_in.start_time, booking_in.end_time),
            Booking.room_id != booking_in.room_id
        ).first()
        if user_conflict:
            raise HTTPException(status_code=400, detail="동일한 시간대에 다른 연습실을 예약할 수 없습니다.")

        booking = Booking(
            user_id=current_user.user_id,
            room_id=booking_in.room_id,
            start_date=booking_in.start_date,
            end_date=booking_in.end_date,
            start_time=booking_in.start_time,
            end_time=booking_in.end_time
        )
        db.add(booking)
        db.commit()
    except HTTPException:
        db.rollback()
        raise
    except OperationalError as exc:
        db.rollback()
        if is_deadlock(exc):
            raise HTTPException(
                status_code=409,
                detail="다른 예약 요청과 충돌했습니다. 잠시 후 다시 시도해 주세요."
            ) from exc
        raise

    db.refresh(booking)
    db.refresh(booking, ["user", "room"])

    return BookingRead(
        booking_id=booking.booking_id,
        start_date=booking.start_date,
        end_date=booking.end_date,
        start_time=booking.start_time,
        end_time=booking.end_time,
        user=UserRead.from_orm(booking.user),
        room=RoomRead.from_orm(booking.room),
        created_at=booking.created_at
    )


@router.delete(
    "/{booking_id}",
    status_code=204,
    summary="예약 취소"
)
def delete_booking(
    booking_id: int,
    db: Session = Depends(get_db),
    current_user=Depends(get_current_user)
):
    policy = get_policy(db)
    booking = db.query(Booking).filter(Booking.booking_id == booking_id).first()
    if not booking:
        raise HTTPException(status_code=404, detail="예약을 찾을 수 없습니다.")
    if booking.user_id != current_user.user_id and current_user.role != "admin":
        raise HTTPException(status_code=403, detail="자신의 예약만 취소할 수 있습니다.")

    seoul_tz = ZoneInfo("Asia/Seoul")
    now = datetime.now(seoul_tz)
    start_dt = datetime.combine(booking.start_date, booking.start_time, tzinfo=seoul_tz)

    if current_user.role != "admin":  # 관리자는 무조건 취소 가능
        if now >= start_dt:
            raise HTTPException(status_code=400, detail="이미 시작된 예약은 취소할 수 없습니다.")
        if (start_dt - now).total_seconds() < policy.cancel_deadline_min * 60:
            raise HTTPException(status_code=400, detail=f"시작 {policy.cancel_deadline_min}분 전에는 취소할 수 없습니다.")

    db.delete(booking)
    db.commit()
    return


# ─────────────────────────────────────────────────────────────
# 왜 schemas/booking.py에 "슬롯 정렬" validator를 넣지 않았는가
# ─────────────────────────────────────────────────────────────
# 1) 검사 기준(slot_minutes)이 DB(BookingPolicy)에 있다. Pydantic
#    field_validator는 동기·순수 함수라 FastAPI가 요청 바디를 파싱하는
#    시점에 DB 세션에 접근할 방법이 없다(컨텍스트를 넘기려면 커스텀
#    Body 처리기가 필요한데, 이 정도 검사 하나 때문에 그런 배관을 새로
#    만들 이유가 없다고 판단했다).
# 2) BookingRead가 BookingBase를 상속한다. BookingBase에 validator를
#    두면 "생성 요청 검증"과 "이미 저장된 값 응답 직렬화"가 같은
#    규칙을 공유하게 되는데, slot_minutes가 나중에 바뀌면 정책 변경
#    이전에 만들어진 기존 예약을 조회만 해도 500이 날 수 있다.
# 그래서 이 파일(_on_slot_grid)에서 라우터 레벨로 검사한다 — policy를
# 이미 읽은 다음이라 자연스럽고, 응답 직렬화 경로에는 영향이 없다.
