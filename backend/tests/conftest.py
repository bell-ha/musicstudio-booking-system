"""
공용 픽스처.

⚠️ 안전장치 — 왜 이 파일 맨 위에서 os.environ을 강제로 덮어쓰고
dotenv.find_dotenv/load_dotenv를 무력화하는가:

backend/.env가 실제로 존재하고 운영 Neon DATABASE_URL·JWT_SECRET을
담고 있다. app/database.py는 임포트되는 순간

    dotenv_path = find_dotenv()
    if dotenv_path:
        load_dotenv(dotenv_path, override=True)

를 실행한다. override=True라서, 여기서 미리 os.environ에 테스트용
DATABASE_URL을 심어 놔도 app.database가 임포트되는 순간 backend/.env의
운영 값으로 덮어써진다 — pytest를 backend/ 밑 어디서 실행하든
find_dotenv()가 backend/.env를 찾아내기 때문이다. 그 상태로
TestClient가 뜨면 "테스트"가 실제로는 운영 Neon에 붙는다.

그래서 app.* 를 그 무엇도 임포트하기 전에:
  1) os.environ에 테스트 값을 강제로 심고(사전에 뭐가 있었든 덮어씀 —
     "운영 DB 절대 금지" 제약이라 setdefault가 아니라 확실하게 덮는다),
  2) dotenv.find_dotenv/load_dotenv 자체를 아무것도 안 하는 함수로
     바꿔서 app.database가 .env를 읽어도 아무 효과가 없게 만든다.
이 순서를 지키지 않으면(예: app.database를 먼저 import한 뒤 이 파일을
고치면) 안전장치가 무의미해진다.
"""
import os

_TEST_DATABASE_URL = "postgresql+psycopg2://postgres:scratch@localhost:5433/booking_test"
_TEST_JWT_SECRET = "test-secret-do-not-use-in-prod"

os.environ["DATABASE_URL"] = _TEST_DATABASE_URL
os.environ["JWT_SECRET"] = _TEST_JWT_SECRET

import dotenv  # noqa: E402  (환경변수 강제 설정 다음에 와야 함)
dotenv.find_dotenv = lambda *a, **k: ""
dotenv.load_dotenv = lambda *a, **k: False

# 이 지점 이후에야 app.* 를 임포트한다.
import uuid  # noqa: E402
from datetime import date, datetime, time, timedelta  # noqa: E402
from zoneinfo import ZoneInfo  # noqa: E402

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.database import DATABASE_URL, SessionLocal  # noqa: E402
from app.main import app as fastapi_app  # noqa: E402
from app.models.booking import Booking  # noqa: E402
from app.models.policy import BookingPolicy, POLICY_ID  # noqa: E402
from app.models.room import Room  # noqa: E402
from app.models.user import MAJORS, User  # noqa: E402
from app.security import get_password_hash  # noqa: E402

SEOUL = ZoneInfo("Asia/Seoul")


def _assert_pointed_at_scratch_db() -> None:
    """혹시라도 위 무력화가 실패해서 운영 DB로 붙었으면 테스트를 아예
    시작하지 않는다 — 조용히 넘어가는 것보다 즉시 실패하는 게 낫다."""
    assert DATABASE_URL == _TEST_DATABASE_URL, (
        f"테스트가 스크래치 DB가 아닌 곳을 보고 있습니다: {DATABASE_URL!r}. "
        "운영 Neon일 수 있습니다 — 즉시 중단합니다."
    )
    assert "neon.tech" not in DATABASE_URL


_assert_pointed_at_scratch_db()


@pytest.fixture(scope="session")
def client():
    _assert_pointed_at_scratch_db()
    with TestClient(fastapi_app) as c:
        yield c


@pytest.fixture()
def db():
    _assert_pointed_at_scratch_db()
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()


def _wipe_tables() -> None:
    session = SessionLocal()
    try:
        session.query(Booking).delete()
        session.query(User).delete()
        session.query(Room).delete()
        session.query(BookingPolicy).delete()
        session.commit()
    finally:
        session.close()


@pytest.fixture(scope="session", autouse=True)
def _wipe_before_session():
    """이전 실행이 중간에 죽어서 정리가 안 됐을 경우를 대비해 세션
    시작 시 한 번 더 지운다."""
    _assert_pointed_at_scratch_db()
    _wipe_tables()


@pytest.fixture(autouse=True)
def _clean_db():
    """매 테스트 뒤 상태를 지운다. 정책 행은 지워서 다음 테스트의 첫
    get_policy() 호출이 기본값으로 새로 만들게 한다 — 기본값을 여기
    직접 하드코딩해서 어긋날 일이 없다."""
    _assert_pointed_at_scratch_db()
    yield
    _wipe_tables()


def _unique(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex[:8]}"


@pytest.fixture()
def make_user(db):
    """기본은 role='user'. student_id/login_id는 매번 유니크하게 만든다."""
    created = []

    def _make(role: str = "user", password: str = "TestPass123!", **overrides):
        login_id = overrides.pop("login_id", _unique("user"))
        student_id = overrides.pop("student_id", _unique("stu")[:20])
        user = User(
            login_id=login_id,
            password=get_password_hash(password),
            username=overrides.pop("username", "테스트유저"),
            student_id=student_id,
            major=overrides.pop("major", MAJORS[0]),
            phone=overrides.pop("phone", None),
            role=role,
            **overrides,
        )
        db.add(user)
        db.commit()
        db.refresh(user)
        user.plain_password = password  # ORM이 안 쓰는 이름이라 그냥 얹어도 안전
        created.append(user)
        return user

    yield _make


@pytest.fixture()
def make_room(db):
    def _make(**overrides):
        room = Room(
            room_name=overrides.pop("room_name", _unique("room")),
            floor=overrides.pop("floor", 1),
            pos_x=overrides.pop("pos_x", 0),
            pos_y=overrides.pop("pos_y", 0),
            state=overrides.pop("state", True),
            **overrides,
        )
        db.add(room)
        db.commit()
        db.refresh(room)
        return room

    yield _make


@pytest.fixture()
def auth_headers(client):
    def _headers(user) -> dict:
        res = client.post(
            "/auth/login",
            data={"username": user.login_id, "password": user.plain_password},
        )
        assert res.status_code == 200, res.text
        token = res.json()["access_token"]
        return {"Authorization": f"Bearer {token}"}

    return _headers


@pytest.fixture()
def policy(db):
    """정책 행을 가져오거나(없으면 기본값으로) 만든다. 테스트가 필드를
    바꾸고 싶으면 이 fixture가 반환한 객체를 직접 수정하고 commit하면
    된다."""
    p = db.get(BookingPolicy, POLICY_ID)
    if p is None:
        p = BookingPolicy(id=POLICY_ID)
        db.add(p)
        db.commit()
        db.refresh(p)
    return p


def future_date(days: int = 1) -> date:
    return (datetime.now(SEOUL) + timedelta(days=days)).date()


def combine(d: date, t: time):
    return datetime.combine(d, t, tzinfo=SEOUL)


def book(client, headers, room_id, d, start, end):
    """POST /api/bookings/ 헬퍼. start/end는 'HH:MM:SS' 문자열."""
    return client.post(
        "/api/bookings/",
        headers=headers,
        json={
            "room_id": room_id,
            "start_date": d.isoformat(),
            "end_date": d.isoformat(),
            "start_time": start,
            "end_time": end,
        },
    )
