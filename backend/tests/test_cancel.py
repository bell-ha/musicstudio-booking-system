"""
취소 규칙 — 이미 시작된 예약 불가 / cancel_deadline_min 전 불가 /
본인 아니면 403 / 관리자는 우회. 시점을 정밀하게 통제해야 해서 예약
생성 API를 거치지 않고 Booking을 직접 DB에 심는다(생성 API 자체는
과거 날짜를 막지 않는다는 걸 확인했는데, 그건 이 작업 범위 밖이라
테스트로 고정하지 않고 참고용으로만 보고한다).
"""
from datetime import timedelta

import pytest

from app.models.booking import Booking
from tests.conftest import SEOUL
from datetime import datetime as dt


def _seed_booking(db, user, room, *, minutes_from_now: float, duration_minutes: int = 60):
    start = dt.now(SEOUL) + timedelta(minutes=minutes_from_now)
    end = start + timedelta(minutes=duration_minutes)
    b = Booking(
        user_id=user.user_id,
        room_id=room.room_id,
        start_date=start.date(),
        end_date=end.date(),
        start_time=start.time().replace(microsecond=0),
        end_time=end.time().replace(microsecond=0),
    )
    db.add(b)
    db.commit()
    db.refresh(b)
    return b


def test_cannot_cancel_already_started(client, make_user, make_room, auth_headers, db):
    user = make_user()
    room = make_room()
    booking = _seed_booking(db, user, room, minutes_from_now=-30, duration_minutes=90)

    res = client.delete(f"/api/bookings/{booking.booking_id}", headers=auth_headers(user))
    assert res.status_code == 400
    assert "이미 시작된" in res.json()["detail"]


def test_cannot_cancel_within_deadline(client, make_user, make_room, auth_headers, policy, db):
    """기본 cancel_deadline_min=10분. 5분 뒤 시작하는 예약은 취소 불가."""
    assert policy.cancel_deadline_min == 10
    user = make_user()
    room = make_room()
    booking = _seed_booking(db, user, room, minutes_from_now=5)

    res = client.delete(f"/api/bookings/{booking.booking_id}", headers=auth_headers(user))
    assert res.status_code == 400
    assert "10분 전" in res.json()["detail"]


def test_can_cancel_safely_in_future(client, make_user, make_room, auth_headers, db):
    user = make_user()
    room = make_room()
    booking = _seed_booking(db, user, room, minutes_from_now=60)

    res = client.delete(f"/api/bookings/{booking.booking_id}", headers=auth_headers(user))
    assert res.status_code == 204


def test_cannot_cancel_others_booking(client, make_user, make_room, auth_headers, db):
    owner = make_user()
    other = make_user()
    room = make_room()
    booking = _seed_booking(db, owner, room, minutes_from_now=60)

    res = client.delete(f"/api/bookings/{booking.booking_id}", headers=auth_headers(other))
    assert res.status_code == 403


def test_admin_can_cancel_anyone_even_started(client, make_user, make_room, auth_headers, db):
    owner = make_user()
    admin = make_user(role="admin")
    room = make_room()
    # 이미 시작됐고 취소 마감도 지난 예약 — 일반 사용자라면 무조건 막혀야 하는 케이스
    booking = _seed_booking(db, owner, room, minutes_from_now=-30, duration_minutes=90)

    res = client.delete(f"/api/bookings/{booking.booking_id}", headers=auth_headers(admin))
    assert res.status_code == 204


def test_cancel_nonexistent_booking_404(client, make_user, auth_headers):
    user = make_user()
    res = client.delete("/api/bookings/999999999", headers=auth_headers(user))
    assert res.status_code == 404
