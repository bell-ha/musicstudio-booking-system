"""
시연용 페르소나 시드 데이터

관리자 화면(사용자 관리 / 예약 내역 / 공지 / 평면도)이 빈 테이블로만 보여서
디자인 검토가 불가능하기 때문에, 뉴뮤직학부 학생들이 실제로 쓸 법한 모양의
데이터를 넣습니다. 난수가 아니라 페르소나 기반입니다.

    실행 (기본은 미리보기 — 아무것도 쓰지 않습니다)
        backend/.venv_pg/bin/python -m app.scripts.seed_demo

    실제로 반영
        backend/.venv_pg/bin/python -m app.scripts.seed_demo --apply

    데모 데이터만 지우고 끝내기 (실서비스 오픈 전 정리용)
        backend/.venv_pg/bin/python -m app.scripts.seed_demo --purge --apply

데모 계정 비밀번호는 전부 아래 DEMO_PASSWORD 입니다.

────────────────────────────────────────────────────────────────────────
안전장치 — 이 DB는 실제 운영(Neon)입니다
────────────────────────────────────────────────────────────────────────
1. 삭제 범위는 login_id 가 'demo_' 로 시작하는 계정뿐입니다.
   bellha 를 비롯한 실제 계정은 어떤 경로로도 지우지 않습니다.
   (예약은 users → bookings 의 cascade 로 함께 지워집니다)
2. 공지는 아래 DEMO_NOTICES 에 적힌 제목과 정확히 일치하는 행만 지웁니다.
3. rooms 는 절대 지우지 않습니다. pos_x/pos_y 가 비어 있는 방에만 좌표를
   채우고, 이미 좌표가 있는 방은 건드리지 않습니다.
4. cells 는 테이블이 비어 있을 때만 생성합니다. 관리자가 이미 평면도를
   그려 두었다면 그대로 둡니다. (--force-cells 로만 다시 깔 수 있습니다)
5. 기본 동작은 미리보기입니다. --apply 를 붙여야 실제로 씁니다.

한계 하나: 이 스키마에는 '취소 이력'이 없습니다. 예약 취소는 행을 삭제하므로
흔적이 남지 않아, "취소가 잦은 학생"은 데이터로 표현할 방법이 없습니다.
대신 그 페르소나는 '짧은 예약을 자주 잡는' 패턴으로 넣었습니다.
"""

import argparse
import sys
from collections import defaultdict
from datetime import date, datetime, time, timedelta

from sqlalchemy import text

from app.database import SessionLocal
from app.models.user import User          # noqa: F401  (메타데이터 등록)
from app.models.room import Room          # noqa: F401
from app.models.booking import Booking    # noqa: F401
from app.models.notice import Notice      # noqa: F401
from app.models.cell import Cell          # noqa: F401
from app.security import get_password_hash

DEMO_PREFIX = "demo_"
DEMO_PASSWORD = "demo1234!"

MAJOR_MT = "뮤직테크놀러지&컴퓨터음악작곡"
MAJOR_SS = "싱어송라이터전공"
MAJOR_JZ = "재즈퍼포먼스전공"


# ──────────────────────────────────────────────────────────────────────
# 페르소나
#   kind 는 예약 패턴을 결정합니다.
#     regular   매일 저녁 같은 시간, 같은 방을 선호. 이력이 길다
#     crammer   시험 주에만 몰아서
#     ensemble  합주팀. 같은 시간대에 인접한 방을 함께 잡는다
#     light     거의 안 쓴다 (0~1건)
#     churner   짧은 예약을 자주 잡는다 (취소가 잦은 학생의 대체 표현)
#     pending   승인 대기 — 예약 없음
# ──────────────────────────────────────────────────────────────────────
PERSONAS = [
    # 성실형 — 예약 내역 화면에서 가장 긴 이력을 만든다
    dict(sid="20201045", name="정하윤", major=MAJOR_JZ, kind="regular",
         hour=19, note="매일 저녁 7시, 같은 방"),
    dict(sid="20211132", name="문지후", major=MAJOR_MT, kind="regular",
         hour=20, note="매일 저녁 8시, 같은 방"),

    # 시험 직전 몰아쓰기 — 특정 한 주에만 집중된다
    dict(sid="20223301", name="배시윤", major=MAJOR_SS, kind="crammer", hour=18),
    dict(sid="20223318", name="한서진", major=MAJOR_SS, kind="crammer", hour=21),
    dict(sid="20214407", name="오채원", major=MAJOR_JZ, kind="crammer", hour=20),

    # 합주팀 — 같은 시간대에 인접한 방 4개를 함께 잡는다
    dict(sid="20205512", name="류시온", major=MAJOR_JZ, kind="ensemble", team=0),
    dict(sid="20205513", name="강도현", major=MAJOR_JZ, kind="ensemble", team=1),
    dict(sid="20215520", name="신아린", major=MAJOR_SS, kind="ensemble", team=2),
    dict(sid="20215521", name="임하람", major=MAJOR_MT, kind="ensemble", team=3),

    # 거의 안 쓰는 학생
    dict(sid="20236601", name="곽민재", major=MAJOR_MT, kind="light"),
    dict(sid="20236612", name="서다온", major=MAJOR_SS, kind="light"),
    dict(sid="20226623", name="윤겨울", major=MAJOR_JZ, kind="light"),
    dict(sid="20226634", name="주하진", major=MAJOR_MT, kind="light"),

    # 짧은 예약을 자주 잡는 학생
    dict(sid="20207701", name="남기훈", major=MAJOR_MT, kind="churner"),
    dict(sid="20217712", name="편수아", major=MAJOR_SS, kind="churner"),

    # 승인 대기 신입생 — 관리자 '승인 대기' 표를 채운다
    dict(sid="20258801", name="고은별", major=MAJOR_SS, kind="pending"),
    dict(sid="20258802", name="차우진", major=MAJOR_MT, kind="pending"),
    dict(sid="20258803", name="백서율", major=MAJOR_JZ, kind="pending"),
    dict(sid="20258804", name="탁현우", major=MAJOR_SS, kind="pending"),

    # 방금 승인된 신입생 — 이제 막 첫 예약을 잡았다
    dict(sid="20258810", name="구예람", major=MAJOR_JZ, kind="light"),
]

DEMO_NOTICES = [
    ("2학기 연습실 운영 시간 안내",
     "학기 중 연습실은 09:00부터 23:00까지 운영합니다.\n"
     "23시 이후에는 건물 출입이 제한되니 정리 시간을 포함해 예약해 주세요."),
    ("주간 예약 오픈 시각 변경",
     "예약 오픈이 금요일 09:00으로 바뀌었습니다.\n"
     "오픈 직후에는 접속이 몰릴 수 있으니 시간을 두고 접속해 주세요."),
    ("222호 피아노 조율 작업 (사용 제한)",
     "조율 작업으로 222호 계열 연습실을 하루 사용할 수 없습니다.\n"
     "해당 시간대 예약은 자동으로 막아 두었습니다."),
    ("연습실 사용 후 정리 부탁드립니다",
     "보면대와 의자를 원위치해 주세요.\n"
     "음식물 반입은 금지되어 있습니다."),
    ("노쇼가 반복되면 이용이 제한될 수 있습니다",
     "예약해 두고 오지 않는 사례가 늘고 있습니다.\n"
     "사용하지 않게 되면 시작 10분 전까지 취소해 주세요. 다음 사람이 쓸 수 있습니다."),
]


# ──────────────────────────────────────────────────────────────────────
# 좌표 / 격자
# ──────────────────────────────────────────────────────────────────────
GRID = 30  # user.js / admin_making_room.js 가 쓰는 30×30 좌표계


def plan_room_positions(rooms_by_floor):
    """
    좌표가 없는 방에만 격자 위 위치를 배정한다.
    방을 층마다 격자에 고르게 흩어 놓아 평면도에서 .sticker 가 겹치지 않게 한다.
    """
    plan = {}          # room_id -> (x, y)
    cells = set()      # (floor, x, y)

    for floor, rooms in sorted(rooms_by_floor.items()):
        cols = 5
        for i, r in enumerate(rooms):
            col, row = i % cols, i // cols
            x = 3 + col * 5
            y = 3 + row * 4
            if x >= GRID - 1 or y >= GRID - 1:
                continue                      # 격자를 넘어가면 좌표를 주지 않는다
            # 방 하나가 차지하는 3×2 칸을 바닥에 깔아 둔다
            for dx in range(3):
                for dy in range(2):
                    cells.add((floor, x + dx, y + dy))
            if r["pos_x"] is None or r["pos_y"] is None:
                plan[r["room_id"]] = (x, y)
    return plan, cells


# ──────────────────────────────────────────────────────────────────────
# 예약 패턴
# ──────────────────────────────────────────────────────────────────────
def build_bookings(persona, rooms_by_floor, taken, today):
    """
    페르소나별 예약 목록을 만든다.
    taken 은 (room_id, date, start_time) 집합으로, 같은 방 같은 시간이
    겹치지 않도록 호출자와 공유한다.
    """
    kind = persona["kind"]
    if kind == "pending":
        return []

    # 층을 섞어 쓰되 페르소나마다 선호 층이 생기게 한다
    floors = sorted(rooms_by_floor)
    fav_floor = floors[hash(persona["sid"]) % len(floors)]
    pool = rooms_by_floor[fav_floor]
    if not pool:
        return []

    out = []

    def add(room, d, hour, minutes=120):
        start = time(hour, 0)
        end_h = hour + minutes // 60
        if end_h > 23:
            return
        key = (room["room_id"], d, start)
        if key in taken:
            return
        taken.add(key)
        out.append(dict(
            room_id=room["room_id"], start_date=d, end_date=d,
            start_time=start, end_time=time(end_h, 0),
            # 예약을 잡은 시각 — 대개 며칠 전에 미리 잡는다
            created_at=datetime.combine(d - timedelta(days=3), time(9, 12)),
        ))

    if kind == "regular":
        # 3주 전부터 다음 주까지, 평일 저녁 같은 시간·같은 방
        room = pool[hash(persona["sid"]) % len(pool)]
        for offset in range(-21, 8):
            d = today + timedelta(days=offset)
            if d.weekday() >= 5:               # 주말은 쉰다
                continue
            add(room, d, persona["hour"])

    elif kind == "crammer":
        # 지난주 한 주에만 몰아서. 하루에 두 번 잡기도 한다
        monday = today - timedelta(days=today.weekday() + 7)
        for i in range(5):
            d = monday + timedelta(days=i)
            room = pool[(i * 3) % len(pool)]
            add(room, d, persona["hour"])
            if i % 2 == 0:
                add(pool[(i * 3 + 1) % len(pool)], d, persona["hour"] - 3)

    elif kind == "ensemble":
        # 합주팀 4명이 같은 시간대에 인접한 방을 나눠 잡는다.
        # 평면도에서 이웃한 .sticker 들이 한꺼번에 차는 모습이 보인다.
        base = hash("ensemble") % max(1, len(pool) - 4)
        room = pool[(base + persona["team"]) % len(pool)]
        for offset in (-14, -12, -7, -5, -2, 1, 3):
            d = today + timedelta(days=offset)
            if d.weekday() >= 5:
                continue
            add(room, d, 19)

    elif kind == "light":
        room = pool[hash(persona["name"]) % len(pool)]
        add(room, today + timedelta(days=2), 14)

    elif kind == "churner":
        # 1시간짜리 짧은 예약을 자주. 앞으로 잡아둔 것도 많다
        for i, offset in enumerate((-10, -8, -6, -3, -1, 2, 4, 6, 9)):
            d = today + timedelta(days=offset)
            room = pool[(i * 2) % len(pool)]
            add(room, d, 13 + (i % 6), minutes=60)

    return out


# ──────────────────────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser(description="시연용 페르소나 시드 데이터")
    ap.add_argument("--apply", action="store_true",
                    help="실제로 DB에 반영한다 (없으면 미리보기만)")
    ap.add_argument("--purge", action="store_true",
                    help="데모 데이터를 지우기만 하고 끝낸다")
    ap.add_argument("--force-cells", action="store_true",
                    help="cells 가 이미 있어도 다시 깐다 (평면도를 덮어쓴다)")
    args = ap.parse_args()

    db = SessionLocal()
    try:
        # ── 현황 ──────────────────────────────────────────────────────
        counts = {
            t: db.execute(text(f"SELECT count(*) FROM {t}")).scalar()
            for t in ("users", "rooms", "bookings", "notices", "cells")
        }
        demo_users = db.execute(text(
            "SELECT count(*) FROM users WHERE login_id LIKE :p"
        ), {"p": DEMO_PREFIX + "%"}).scalar()
        real_users = counts["users"] - demo_users

        print("현재 DB")
        for t, n in counts.items():
            print(f"  {t:10} {n}")
        print(f"  (그중 데모 계정 {demo_users}, 실제 계정 {real_users})")
        print()

        # ── 삭제 대상 ────────────────────────────────────────────────
        print(f"지울 것 — login_id LIKE '{DEMO_PREFIX}%' 인 계정과 그 예약,")
        print(f"          그리고 아래 제목과 정확히 일치하는 공지 {len(DEMO_NOTICES)}건")
        print("          rooms 와 bellha 계정은 건드리지 않습니다.")
        print()

        if args.apply:
            db.execute(text(
                "DELETE FROM bookings WHERE user_id IN "
                "(SELECT user_id FROM users WHERE login_id LIKE :p)"
            ), {"p": DEMO_PREFIX + "%"})
            db.execute(text("DELETE FROM users WHERE login_id LIKE :p"),
                       {"p": DEMO_PREFIX + "%"})
            db.execute(text("DELETE FROM notices WHERE title = ANY(:titles)"),
                       {"titles": [t for t, _ in DEMO_NOTICES]})
            db.commit()
            print("  삭제 완료")

        if args.purge:
            if not args.apply:
                print("  (미리보기 — --apply 를 붙이면 실제로 지웁니다)")
            print("\n--purge 이므로 여기서 끝냅니다.")
            return

        # ── 방 / 좌표 ────────────────────────────────────────────────
        rooms = db.execute(text(
            "SELECT room_id, room_name, floor, pos_x, pos_y FROM rooms ORDER BY room_id"
        )).mappings().all()
        if not rooms:
            print("rooms 가 비어 있습니다. seed_rooms 를 먼저 실행하세요.")
            return

        by_floor = defaultdict(list)
        for r in rooms:
            by_floor[r["floor"]].append(dict(r))

        pos_plan, cell_plan = plan_room_positions(by_floor)
        print(f"방 {len(rooms)}개, 층 {sorted(by_floor)}")
        print(f"  좌표를 채울 방 {len(pos_plan)}개 "
              f"(이미 좌표가 있는 방 {len(rooms) - len(pos_plan)}개는 그대로 둠)")

        seed_cells = counts["cells"] == 0 or args.force_cells
        print(f"  격자 칸 {len(cell_plan)}개 "
              f"{'생성' if seed_cells else '건너뜀 (cells 에 이미 데이터가 있음)'}")
        print()

        if args.apply:
            for room_id, (x, y) in pos_plan.items():
                db.execute(text(
                    "UPDATE rooms SET pos_x = :x, pos_y = :y "
                    "WHERE room_id = :id AND (pos_x IS NULL OR pos_y IS NULL)"
                ), {"x": x, "y": y, "id": room_id})
            if seed_cells:
                if args.force_cells:
                    db.execute(text("DELETE FROM cells"))
                for floor, x, y in sorted(cell_plan):
                    db.execute(text(
                        "INSERT INTO cells (floor, x, y) VALUES (:f, :x, :y) "
                        "ON CONFLICT (floor, x, y) DO NOTHING"
                    ), {"f": floor, "x": x, "y": y})
            db.commit()

        # ── 사용자 ───────────────────────────────────────────────────
        pw_hash = get_password_hash(DEMO_PASSWORD)
        n_pending = sum(1 for p in PERSONAS if p["kind"] == "pending")
        print(f"사용자 {len(PERSONAS)}명 (승인 대기 {n_pending}명, "
              f"승인됨 {len(PERSONAS) - n_pending}명)")

        ids = {}
        for p in PERSONAS:
            login_id = DEMO_PREFIX + p["sid"]
            role = "pending" if p["kind"] == "pending" else "user"
            phone = f"010-{p['sid'][4:8]}-{p['sid'][2:4]}{p['sid'][-2:]}"
            if args.apply:
                ids[p["sid"]] = db.execute(text(
                    "INSERT INTO users "
                    "(login_id, password, username, student_id, major, phone, role) "
                    "VALUES (:l, :pw, :u, :s, :m, :ph, :r) RETURNING user_id"
                ), {"l": login_id, "pw": pw_hash, "u": p["name"], "s": p["sid"],
                    "m": p["major"], "ph": phone, "r": role}).scalar()
        if args.apply:
            db.commit()

        # ── 예약 ─────────────────────────────────────────────────────
        today = date.today()
        taken = set()
        total = 0
        by_kind = defaultdict(int)
        for p in PERSONAS:
            rows = build_bookings(p, by_floor, taken, today)
            by_kind[p["kind"]] += len(rows)
            total += len(rows)
            if args.apply and rows:
                for b in rows:
                    db.execute(text(
                        "INSERT INTO bookings "
                        "(user_id, room_id, start_date, end_date, "
                        " start_time, end_time, created_at) "
                        "VALUES (:u, :r, :sd, :ed, :st, :et, :ca)"
                    ), {"u": ids[p["sid"]], "r": b["room_id"],
                        "sd": b["start_date"], "ed": b["end_date"],
                        "st": b["start_time"], "et": b["end_time"],
                        "ca": b["created_at"]})
        if args.apply:
            db.commit()

        past = sum(1 for (_, d, _) in taken if d < today)
        future = sum(1 for (_, d, _) in taken if d > today)
        print(f"예약 {total}건 (지난 {past} · 오늘 {total - past - future} · 앞으로 {future})")
        for k in ("regular", "crammer", "ensemble", "churner", "light"):
            print(f"  {k:9} {by_kind[k]}건")
        print()

        # ── 공지 ─────────────────────────────────────────────────────
        print(f"공지 {len(DEMO_NOTICES)}건")
        if args.apply:
            for i, (title, content) in enumerate(DEMO_NOTICES):
                db.execute(text(
                    "INSERT INTO notices (title, content, created_at) "
                    "VALUES (:t, :c, :ca)"
                ), {"t": title, "c": content,
                    "ca": datetime.combine(today - timedelta(days=i * 4 + 1),
                                           time(10, 30))})
            db.commit()

        print()
        if args.apply:
            print("반영했습니다.")
            print(f"데모 계정 로그인:  {DEMO_PREFIX}20201045 / {DEMO_PASSWORD}")
            print("오픈 전에 정리하려면:  python -m app.scripts.seed_demo --purge --apply")
        else:
            print("미리보기였습니다. 실제로 넣으려면 --apply 를 붙이세요.")

    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


if __name__ == "__main__":
    sys.exit(main())
