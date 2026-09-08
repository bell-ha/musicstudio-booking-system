"""
데모 데이터 — 화면을 실제로 보기 위한 학생·예약·공지.

페르소나를 나눠 넣는다. 전부 똑같이 예약을 채우면 관리자 화면의
'승인 대기'나 '예약 없음' 같은 상태를 볼 수 없다.

삭제 범위를 login_id 접두어 'demo_'로 한정한다.
실제 학생 계정과 연습실·평면도는 건드리지 않는다.
"""
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from app.database import SessionLocal
from app.models.user import User
from app.models.room import Room
from app.models.booking import Booking
from app.models.notice import Notice
from app.security import get_password_hash

PREFIX = "demo_"
PASSWORD = "demo1234!"
KST = ZoneInfo("Asia/Seoul")

# (아이디, 이름, 학번, 전공, 역할, 어떤 학생인가)
PEOPLE = [
    ("demo_admin",   "데모관리자", "30000001", "뮤직테크놀러지&컴퓨터음악작곡", "admin",   "관리자 계정"),
    ("demo_student", "데모학생",   "30000002", "싱어송라이터전공",              "user",    "둘러보기용 기본 계정"),
    ("demo_pending", "데모대기",   "30000003", "재즈퍼포먼스전공",              "pending", "가입 후 승인을 기다리는 상태"),
    ("demo_daily",   "정민서",     "30240101", "재즈퍼포먼스전공",              "user",    "매일 저녁 같은 시간에 연습한다"),
    ("demo_exam",    "한지우",     "30240102", "싱어송라이터전공",              "user",    "시험 주간에만 몰아서 쓴다"),
    ("demo_band1",   "오세현",     "30240103", "재즈퍼포먼스전공",              "user",    "합주 준비 중 — 같은 시간대 인접 방"),
    ("demo_band2",   "윤가람",     "30240104", "재즈퍼포먼스전공",              "user",    "합주 준비 중 — 같은 시간대 인접 방"),
    ("demo_rare",    "배수린",     "30240105", "뮤직테크놀러지&컴퓨터음악작곡", "user",    "거의 안 쓴다"),
    ("demo_new1",    "김도윤",     "30250101", "싱어송라이터전공",              "pending", "신입생 승인 대기"),
    ("demo_new2",    "이서진",     "30250102", "뮤직테크놀러지&컴퓨터음악작곡", "pending", "신입생 승인 대기"),
]

NOTICES = [
    ("연습실 예약 시스템 이용 안내",
     "예약은 매주 금요일 오전 9시에 다음 주 분이 한 번에 열립니다.\n"
     "한 번에 최대 2시간까지 예약할 수 있고, 시작 10분 전까지 취소할 수 있습니다.\n"
     "예약해 두고 오지 않는 경우가 반복되면 이용이 제한될 수 있습니다."),
    ("3층 325호 피아노 조율 안내",
     "9월 15일(월) 오전 중 325호 피아노 조율이 예정되어 있습니다.\n"
     "해당 시간에는 예약이 막혀 있습니다. 다른 방을 이용해 주세요."),
    ("야간 이용 시 유의사항",
     "22시 이후에는 건물 출입이 제한됩니다. 학생증을 꼭 지참해 주세요.\n"
     "마지막 이용자는 조명과 냉난방을 끄고 문을 닫아 주시기 바랍니다."),
    ("합주실 사용 규칙 변경",
     "합주 목적으로 2층 221호를 사용할 경우, 대표 1명만 예약하고\n"
     "나머지 인원은 별도로 예약하지 않도록 합니다. 중복 예약이 확인되면 취소됩니다."),
    ("분실물 보관 안내",
     "연습실에서 발견된 분실물은 학과 사무실에서 2주간 보관합니다.\n"
     "케이블, 이어폰, 악보 등이 다수 보관되어 있으니 확인해 주세요."),
]


def _wipe(db):
    users = db.query(User).filter(User.login_id.like(f"{PREFIX}%")).all()
    ids = [u.user_id for u in users]
    n_b = db.query(Booking).filter(Booking.user_id.in_(ids)).delete(synchronize_session=False) if ids else 0
    n_u = db.query(User).filter(User.user_id.in_(ids)).delete(synchronize_session=False) if ids else 0
    n_n = db.query(Notice).delete()
    db.commit()
    return n_b, n_u, n_n


def seed():
    db = SessionLocal()
    try:
        n_b, n_u, n_n = _wipe(db)
        print(f"  기존 데모 정리 — 예약 {n_b} · 계정 {n_u} · 공지 {n_n}")

        pw = get_password_hash(PASSWORD)
        users = {}
        for lid, name, sid, major, role, _ in PEOPLE:
            u = User(login_id=lid, username=name, student_id=sid, major=major,
                     password=pw, role=role, phone="01000000000")
            db.add(u); users[lid] = u
        db.commit()

        for title, content in NOTICES:
            db.add(Notice(title=title, content=content, created_at=datetime.now(KST).replace(tzinfo=None)))
        db.commit()

        rooms = db.query(Room).order_by(Room.room_name).all()
        by_floor = {}
        for r in rooms:
            by_floor.setdefault(r.floor, []).append(r)

        today = datetime.now(KST).date()
        monday = today - timedelta(days=today.weekday())

        def book(login_id, room, d, start_h, hours=1):
            db.add(Booking(
                user_id=users[login_id].user_id, room_id=room.room_id,
                start_date=d, end_date=d,
                start_time=f"{start_h:02d}:00", end_time=f"{start_h + hours:02d}:00",
                created_at=datetime.now(KST).replace(tzinfo=None),
            ))

        f2 = by_floor.get(2, rooms)
        made = 0

        # 매일 저녁 같은 방, 같은 시간 — 지난주부터 이번 주까지
        for off in range(-7, 5):
            d = monday + timedelta(days=off)
            if d.weekday() >= 5:
                continue
            book("demo_daily", f2[0], d, 19, 2); made += 1

        # 시험 주간에만 몰아서
        for off in range(0, 5):
            d = monday + timedelta(days=off)
            for h in (14, 16):
                book("demo_exam", f2[1 + off % 3], d, h, 2); made += 1

        # 합주 준비 — 같은 시간대에 인접한 두 방
        for off in (1, 3):
            d = monday + timedelta(days=off)
            book("demo_band1", f2[4], d, 20, 2); made += 1
            book("demo_band2", f2[5], d, 20, 2); made += 1

        # 거의 안 쓰는 학생
        book("demo_rare", f2[6], monday + timedelta(days=2), 11); made += 1

        # 저녁 시간대가 인기라는 게 드러나도록 여러 학생이 겹쳐 예약
        for i, lid in enumerate(("demo_student", "demo_exam", "demo_rare")):
            book(lid, f2[7 + i], monday + timedelta(days=4), 19 + i % 2, 1); made += 1

        db.commit()
        print(f"  계정 {len(PEOPLE)} · 공지 {len(NOTICES)} · 예약 {made}")
        print(f"  비밀번호 전원: {PASSWORD}")
        for lid, name, _, _, role, why in PEOPLE:
            print(f"   {lid:14} {name:6} {role:8} {why}")
    finally:
        db.close()


if __name__ == "__main__":
    seed()
