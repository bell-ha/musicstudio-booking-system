"""
같은 사용자·같은 날 재예약 규칙 3종(same_day_mode) — 실제로 사용자가
승인한 대로 동작하는지. github-de가 8병렬 curl로 확인한 것과 별개로,
회귀가 생기면 바로 잡아내려고 여기 고정해 둔다.

가정: create_booking은 "이번 주 오픈 윈도우" 같은 건 검사하지 않는다
(그건 room.py의 슬롯 조회 전용 로직이다) — 그래서 그냥 내일 날짜를 쓴다.
"""
from app.models.policy import NO_OVERLAP, ONE_PER_DAY, SEQUENTIAL
from tests.conftest import book as _book
from tests.conftest import future_date


def test_no_overlap_blocks_only_actual_overlap(client, make_user, make_room, auth_headers, policy, db):
    """기본값 NO_OVERLAP: 겹치지만 않으면 순서 무관하게 여러 방 예약 가능,
    겹치면 ④(사용자충돌)로 막힌다."""
    assert policy.same_day_mode == NO_OVERLAP  # 기본값 확인

    user = make_user()
    room1, room2 = make_room(), make_room()
    headers = auth_headers(user)
    d = future_date()

    r1 = _book(client, headers, room1.room_id, d, "09:00:00", "10:00:00")
    assert r1.status_code == 201, r1.text

    # 겹침(다른 방, 09:30-10:30) → ④로 막혀야 한다
    r2 = _book(client, headers, room2.room_id, d, "09:30:00", "10:30:00")
    assert r2.status_code == 400
    assert "다른 연습실" in r2.json()["detail"]

    # 안 겹치고 순서도 이전(08:00-09:00, room1의 시작 이전) → NO_OVERLAP이면 통과해야 한다
    r3 = _book(client, headers, room2.room_id, d, "07:00:00", "08:00:00")
    assert r3.status_code == 201, r3.text


def test_sequential_requires_chronological_order(client, make_user, make_room, auth_headers, policy, db):
    """SEQUENTIAL: 새 예약 시작이 '그날 마지막(종료가 가장 늦은) 예약'의
    종료 이후여야 한다 — 겹치지 않아도 순서가 앞서면 막힌다."""
    policy.same_day_mode = SEQUENTIAL
    db.commit()

    user = make_user()
    room1, room2, room3 = make_room(), make_room(), make_room()
    headers = auth_headers(user)
    d = future_date()

    r1 = _book(client, headers, room1.room_id, d, "09:00:00", "10:00:00")
    assert r1.status_code == 201, r1.text

    # 겹침 → ②(순차 규칙)에서 막힌다. 메시지로 ②인지 확인(④가 아님).
    r2 = _book(client, headers, room2.room_id, d, "09:30:00", "10:30:00")
    assert r2.status_code == 400
    assert "이전 예약의 종료 시각 이후" in r2.json()["detail"]

    # 정확히 이전 예약 종료 시각부터 시작 → 통과해야 한다(순차 허용)
    r3 = _book(client, headers, room2.room_id, d, "10:00:00", "11:00:00")
    assert r3.status_code == 201, r3.text

    # 안 겹치지만 "마지막 예약(room2, 10-11시)"보다 이른 시간 → SEQUENTIAL에서는
    # 그래도 막힌다(마지막 예약 기준 순서 위반) — 이게 ④가 SEQUENTIAL에서
    # 도달 불가능한 이유이기도 하다.
    r4 = _book(client, headers, room3.room_id, d, "08:00:00", "09:00:00")
    assert r4.status_code == 400
    assert "이전 예약의 종료 시각 이후" in r4.json()["detail"]


def test_one_per_day_blocks_regardless_of_overlap(client, make_user, make_room, auth_headers, policy, db):
    """ONE_PER_DAY: 겹치지 않아도, 시간이 완전히 동떨어져 있어도 그날 두
    번째 예약은 무조건 막힌다."""
    policy.same_day_mode = ONE_PER_DAY
    db.commit()

    user = make_user()
    room1, room2 = make_room(), make_room()
    headers = auth_headers(user)
    d = future_date()

    r1 = _book(client, headers, room1.room_id, d, "09:00:00", "10:00:00")
    assert r1.status_code == 201, r1.text

    # 완전히 안 겹치는 오후 시간대인데도 막혀야 한다
    r2 = _book(client, headers, room2.room_id, d, "14:00:00", "15:00:00")
    assert r2.status_code == 400
    assert "추가로 예약할 수 없습니다" in r2.json()["detail"]

    # 다른 날짜라면 문제 없어야 한다(하루 1건이지, 전체 1건이 아님)
    d2 = future_date(days=2)
    r3 = _book(client, headers, room2.room_id, d2, "09:00:00", "10:00:00")
    assert r3.status_code == 201, r3.text
