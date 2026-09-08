"""
정책(BookingPolicy) 값을 바꾸면 실제로 동작이 바뀌는지. 하드코딩된
상수를 정책 컬럼으로 옮긴 작업(max_minutes/cancel_deadline_min/
slot_minutes)이 실제로 그 컬럼을 읽는지 확인한다 — 기본값에서만 통과하고
값을 바꾸면 여전히 옛날 상수로 동작한다면(리팩터링 중 흔한 실수) 여기서
잡힌다.
"""
from datetime import timedelta
from datetime import datetime as dt

from app.models.booking import Booking
from tests.conftest import SEOUL, book, future_date


def test_max_minutes_enforced_from_policy(client, make_user, make_room, auth_headers, policy, db):
    policy.max_minutes = 30
    db.commit()

    user = make_user()
    room = make_room()
    headers = auth_headers(user)
    d = future_date()

    too_long = book(client, headers, room.room_id, d, "09:00:00", "10:00:00")  # 60분
    assert too_long.status_code == 400
    assert "30분" in too_long.json()["detail"]

    ok = book(client, headers, room.room_id, d, "09:00:00", "09:30:00")  # 30분
    assert ok.status_code == 201, ok.text


def test_cancel_deadline_enforced_from_policy(client, make_user, make_room, auth_headers, policy, db):
    policy.cancel_deadline_min = 60
    db.commit()

    user = make_user()
    room = make_room()

    start = dt.now(SEOUL) + timedelta(minutes=30)
    end = start + timedelta(minutes=60)
    too_close = Booking(
        user_id=user.user_id,
        room_id=room.room_id,
        start_date=start.date(),
        end_date=end.date(),
        start_time=start.time().replace(microsecond=0),
        end_time=end.time().replace(microsecond=0),
    )
    db.add(too_close)
    db.commit()
    db.refresh(too_close)

    res = client.delete(f"/api/bookings/{too_close.booking_id}", headers=auth_headers(user))
    assert res.status_code == 400
    assert "60분 전" in res.json()["detail"]


def test_slot_minutes_enforced_from_policy(client, make_user, make_room, auth_headers, policy, db):
    policy.slot_minutes = 60
    db.commit()

    user = make_user()
    room = make_room()
    headers = auth_headers(user)
    d = future_date()

    misaligned = book(client, headers, room.room_id, d, "09:30:00", "10:30:00")
    assert misaligned.status_code == 400
    assert "60분 단위" in misaligned.json()["detail"]

    aligned = book(client, headers, room.room_id, d, "09:00:00", "10:00:00")
    assert aligned.status_code == 201, aligned.text
