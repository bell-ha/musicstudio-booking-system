#!/usr/bin/env python3
"""데모 데이터를 API로 넣는다. 실행 중인 서비스(기본 http://localhost:3000)에 붙는다.

    python3 scripts/seed-demo.py [기본 주소]

원장·강사·학생 계정, 학원 하나, 평면도와 방, 과목·상품, 원생·수강·레슨 기록, 내일 연습실 예약 하나를 만든다.
여러 번 돌리면 계정 이메일이 겹치므로 실행마다 새 접미사를 붙인다.
"""
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:3000").rstrip("/") + "/api/v1"
SUFFIX = time.strftime("%H%M%S")
PASSWORD = "password123"
SEOUL = timezone(timedelta(hours=9))


def call(method, path, body=None, token=None, headers=None):
    req = urllib.request.Request(BASE + path, method=method,
                                 data=None if body is None else json.dumps(body).encode())
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req) as res:
            raw = res.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path} → {e.code} {e.read().decode()}")


def user(name, role):
    email = f"demo-{role}-{SUFFIX}@example.com"
    call("POST", "/auth/signup", {"email": email, "password": PASSWORD, "name": name})
    token = call("POST", "/auth/login", {"email": email, "password": PASSWORD})["accessToken"]
    return email, token


owner_email, owner = user("김원장", "owner")
org = call("POST", "/organizations", {"name": f"하모니 음악학원 {SUFFIX}", "type": "ACADEMY", "timezone": "Asia/Seoul"}, owner)
O = f"/organizations/{org['id']}"


def join(name, role):
    token = call("POST", O + "/invitations", {"role": role}, owner)["token"]
    email, t = user(name, role.lower())
    call("POST", "/invitations/accept", {"token": token}, t)
    me = [o for o in call("GET", "/me/organizations", token=t) if o["organizationId"] == org["id"]][0]
    return email, t, me["membershipId"]


teacher_email, teacher, teacher_id = join("박강사", "TEACHER")
student_email, student, student_member_id = join("이학생", "STUDENT")

# ---------- 연습실: v1처럼 벽과 복도로 건물을 그리고 그 안에 방 9개 ----------
# 30×16 격자. 바깥벽, 가운데 복도(2줄), 위쪽 방 5개와 아래쪽 방 4개, 방 사이 벽, 방마다 복도 쪽 문.
W, H = 30, 16
TOP = (1, 5)       # 위쪽 방: y 1~5
BOTTOM = (10, 14)  # 아래쪽 방: y 10~14
cells = {}
for x in range(W):
    cells[(x, 0)] = cells[(x, H - 1)] = "wall"
for y in range(H):
    cells[(0, y)] = cells[(W - 1, y)] = "wall"
for x in range(1, W - 1):
    cells[(x, 6)] = cells[(x, 9)] = "wall"          # 방과 복도 사이 벽
    cells[(x, 7)] = cells[(x, 8)] = "corridor"      # 복도
top_rooms = [(1 + i * 5, 4) for i in range(5)]       # (x, 너비)
bottom_rooms = [(1 + i * 6, 5) for i in range(4)]
for x, w in top_rooms:
    if x + w < W - 1:
        for y in range(TOP[0], TOP[1] + 1):
            cells[(x + w, y)] = "wall"               # 옆 방과의 벽
    cells[(x + w // 2, 6)] = "corridor"              # 문
for x, w in bottom_rooms:
    if x + w < W - 1:
        for y in range(BOTTOM[0], BOTTOM[1] + 1):
            cells[(x + w, y)] = "wall"
    cells[(x + w // 2, 9)] = "corridor"
# 오른쪽 아래 계단실 (v1 평면도처럼 대각선)
for i in range(4):
    cells[(25 + i, 10 + i)] = "wall"

names = [("A101", ["업라이트 피아노"]), ("A102", ["업라이트 피아노"]), ("A103", ["그랜드 피아노"]),
         ("A104", ["드럼"]), ("A105", ["앰프", "마이크"]), ("B101", ["업라이트 피아노"]),
         ("B102", ["업라이트 피아노"]), ("B103", ["전자 피아노"]), ("B104", ["기타 앰프"])]
rooms = [call("POST", O + "/practice/rooms", {"name": n, "equipment": eq}, owner) for n, eq in names]
spots = [{"x": x, "y": TOP[0], "w": w, "h": TOP[1] - TOP[0] + 1} for x, w in top_rooms] \
    + [{"x": x, "y": BOTTOM[0], "w": w, "h": BOTTOM[1] - BOTTOM[0] + 1} for x, w in bottom_rooms]
placements = [{"id": r["id"], **s} for r, s in zip(rooms, spots)]
floor = call("POST", O + "/practice/floors", {"name": "1층", "width": W, "height": H}, owner)
call("PUT", O + f"/practice/floors/{floor['id']}",
     {"name": "1층", "sortOrder": 0,
      "layout": {"width": W, "height": H,
                 "walls": [[x, y] for (x, y), t in cells.items() if t == "wall"],
                 "corridors": [[x, y] for (x, y), t in cells.items() if t == "corridor"]},
      "rooms": placements}, owner, {"If-Match": '"0"'})

# 내일 14:00~15:30 A101 예약 (학생)
tomorrow = (datetime.now(SEOUL) + timedelta(days=1)).date().isoformat()
call("POST", O + "/practice/bookings", {"roomId": rooms[0]["id"],
                                        "startsAt": f"{tomorrow}T14:00:00+09:00",
                                        "endsAt": f"{tomorrow}T15:30:00+09:00"}, student)

# ---------- 학원 관리: 과목, 상품, 원생, 수강, 레슨 기록 ----------
piano = call("POST", O + "/academy/subjects", {"name": "피아노"}, owner)
call("POST", O + "/academy/subjects", {"name": "보컬"}, owner)
product = call("POST", O + "/academy/products", {"subjectId": piano["id"], "name": "피아노 3개월", "kind": "PERIOD",
                                                  "periodMonths": 3, "lessonMinutes": 50, "price": 450000}, owner)
call("POST", O + "/academy/products", {"subjectId": piano["id"], "name": "피아노 10회", "kind": "COUNT",
                                       "sessionCount": 10, "lessonMinutes": 50, "price": 300000}, owner)
st = call("POST", O + "/academy/students", {"name": "이학생", "birthYear": 2011, "phone": "010-1234-5678",
                                            "guardianName": "이보호", "guardianPhone": "010-9876-5432"}, owner)
call("PUT", O + f"/academy/students/{st['id']}/account", {"membershipId": student_member_id}, owner)
for name, year in [("최민준", 2013), ("정서연", 2009)]:
    call("POST", O + "/academy/students", {"name": name, "birthYear": year}, owner)
starts = (datetime.now(SEOUL) - timedelta(days=80)).date().isoformat()
enrollment = call("POST", O + "/academy/enrollments", {"studentId": st["id"], "productId": product["id"],
                                                        "teacherMembershipId": teacher_id, "startsOn": starts}, owner)
today = datetime.now(SEOUL).date().isoformat()
call("POST", O + "/academy/lesson-records", {"enrollmentId": enrollment["id"], "lessonDate": today,
                                             "progress": "체르니 30번 12번", "homework": "C장조 스케일 하루 10분",
                                             "memo": "손목 힘 빼기", "visibleToStudent": True}, teacher)

print(f"""
데모 데이터를 넣었습니다. 브라우저: {BASE.removesuffix('/api/v1')}
비밀번호는 모두 {PASSWORD}

  원장  {owner_email}
  강사  {teacher_email}
  학생  {student_email}

학원: 하모니 음악학원 {SUFFIX} (가입 코드: {call('GET', O + '/join-code', token=owner)['joinCode']})
내일 A101 14:00~15:30에 학생 예약이 있고, 이학생 수강은 곧 끝납니다(만료 임박).
""")
