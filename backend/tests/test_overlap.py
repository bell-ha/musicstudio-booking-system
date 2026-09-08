"""
services/overlap.py의 time_overlap_clause() — booking.py(방충돌·사용자충돌),
admin_booking.py, room.py 슬롯 조회 네 곳이 전부 이걸 쓴다. 실제로 DB에
쿼리를 날려서 겹침 판정이 맞는지 확인한다(순수 파이썬 함수가 아니라
SQLAlchemy 조건절을 만드는 함수라, 값만 넣어 보는 단위 테스트로는 의미가
없다 — 실제 필터로 걸어서 결과 행이 나오는지를 봐야 한다).
"""
from datetime import time

import pytest

from app.models.booking import Booking
from app.services.overlap import time_overlap_clause
from tests.conftest import future_date


def _has_conflict(db, room_id, on_date, start, end):
    return (
        db.query(Booking)
        .filter(
            Booking.room_id == room_id,
            Booking.start_date == on_date,
            time_overlap_clause(start, end),
        )
        .first()
        is not None
    )


@pytest.fixture()
def existing_booking(db, make_user, make_room):
    """09:00-11:00 예약 하나를 깔아 둔다."""
    user = make_user()
    room = make_room()
    d = future_date()
    b = Booking(
        user_id=user.user_id,
        room_id=room.room_id,
        start_date=d,
        end_date=d,
        start_time=time(9, 0),
        end_time=time(11, 0),
    )
    db.add(b)
    db.commit()
    return room, d


@pytest.mark.parametrize(
    "candidate_start,candidate_end,expect_conflict,label",
    [
        (time(11, 0), time(12, 0), False, "뒤쪽 경계 맞닿음 — 비충돌"),
        (time(7, 0), time(9, 0), False, "앞쪽 경계 맞닿음 — 비충돌"),
        (time(8, 0), time(12, 0), True, "기존 예약을 감싸는 요청 — 충돌"),
        (time(9, 30), time(10, 30), True, "기존 예약에 포함되는 요청 — 충돌"),
        (time(8, 0), time(10, 0), True, "앞쪽 부분 겹침 — 충돌"),
        (time(10, 0), time(12, 0), True, "뒤쪽 부분 겹침 — 충돌"),
        (time(9, 0), time(11, 0), True, "완전히 동일한 구간 — 충돌"),
        (time(6, 0), time(7, 0), False, "완전히 동떨어짐(이전) — 비충돌"),
        (time(12, 0), time(13, 0), False, "완전히 동떨어짐(이후) — 비충돌"),
    ],
)
def test_time_overlap_boundary_cases(
    db, existing_booking, candidate_start, candidate_end, expect_conflict, label
):
    room, d = existing_booking
    result = _has_conflict(db, room.room_id, d, candidate_start, candidate_end)
    assert result == expect_conflict, label


def test_different_room_never_conflicts(db, existing_booking, make_room):
    """같은 시간이어도 room_id가 다르면 이 조건절만으로는 안 걸린다
    (방 필터는 호출하는 쪽 책임 — time_overlap_clause 자체의 범위가 아님을
    확인해 둔다)."""
    room, d = existing_booking
    other_room = make_room()
    assert not _has_conflict(db, other_room.room_id, d, time(9, 0), time(11, 0))
