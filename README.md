<div align="center">

# 뉴뮤직 연습실 예약 시스템

**단국대학교 뉴뮤직 학부 연습실 예약·관리 웹 서비스 — 학부 학생들이 실제로 사용 중**

[![FastAPI](https://img.shields.io/badge/FastAPI-Backend-009688?logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com/)
[![MySQL](https://img.shields.io/badge/MySQL-Database-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![JWT](https://img.shields.io/badge/Auth-JWT_HS256-000000?logo=jsonwebtokens&logoColor=white)](https://jwt.io/)
[![Docker](https://img.shields.io/badge/Docker-Deployed-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Status](https://img.shields.io/badge/운영-실사용_중-success)]()

2인 팀 · 2025.03 ~ 2026.07 · 실운영 서비스

</div>

---

## 한눈에 보기

| | |
|---|---|
| **문제** | 연습실 예약을 카톡·구두로 하다 보니 중복 예약과 분쟁이 반복됐다 |
| **핵심 난이도** | **예약 시스템의 본질은 UI가 아니라 "겹치면 안 되는 규칙"이다.** 같은 방·같은 사람·같은 날에 걸리는 조건이 서로 다르다 |
| **설계 포인트** | ① 4중 예약 검증 ② 격자 좌표 기반 호실 배치 ③ 학부 인증 승인 흐름 |
| **사용자** | 뉴뮤직 학부 학생 · 관리자(교수/학생회) |

<div align="center">
<img src="booking_1.png" width="270"/> <img src="booking_2.png" width="270"/> <img src="admin_3.png" width="270"/>
<br><sub>예약 현황 · 시간 선택 · 관리자 호실 배치 설정</sub>
</div>

---

## 목차

1. [담당 역할](#1-담당-역할)
2. [예약 충돌 처리 — 이 프로젝트의 핵심](#2-예약-충돌-처리--이-프로젝트의-핵심)
3. [격자 좌표 기반 호실 배치](#3-격자-좌표-기반-호실-배치)
4. [인증과 학부 승인 흐름](#4-인증과-학부-승인-흐름)
5. [기능](#5-기능)
6. [기술 스택 · 구조](#6-기술-스택--구조)
7. [실행 방법](#7-실행-방법)
8. [알려진 한계와 다음 단계](#8-알려진-한계와-다음-단계)

---

## 1. 담당 역할

2인 팀으로 진행했으며 전체 57 커밋 중 34 커밋을 담당했다.

| | 담당 |
|---|---|
| **이종하** | **백엔드 전반** — 예약 검증 로직, DB 스키마, JWT 인증·권한, 관리자 API, Docker 배포 |
| 팀원 | 프론트엔드 화면 구현 |
| 공통 | 예약 정책 설계, 운영 대응 |

---

## 2. 예약 충돌 처리 — 이 프로젝트의 핵심

예약 시스템에서 실제로 어려운 건 화면이 아니라 **"어떤 예약을 거절해야 하는가"** 다. 조건이 하나가 아니다.

`POST /bookings` 는 통과하기 전에 **4단계 검증**을 거친다.

```mermaid
flowchart TB
    IN["예약 요청"] --> V1
    V1["① 시간 유효성<br/>end &gt; start · 최대 2시간"] -->|OK| V2
    V2["② 같은 날 본인 재예약<br/>이전 예약 종료 이후에만"] -->|OK| V3
    V3["③ 방 충돌<br/>같은 방 · 시간 겹침"] -->|OK| V4
    V4["④ 사용자 충돌<br/>같은 사람 · 다른 방 · 시간 겹침"] -->|OK| OK["예약 생성"]

    V1 -.->|400| X["거절"]
    V2 -.->|400| X
    V3 -.->|400| X
    V4 -.->|400| X

    style V3 fill:#1f6feb,color:#fff
    style V4 fill:#1f6feb,color:#fff
```

### ③ 방 충돌 — 구간 겹침 판정

시간대가 "겹친다"를 정확히 판정하는 조건이다.

```python
Booking.room_id    == room_id,
Booking.start_date == start_date,
Booking.start_time <  end_time,      # 기존 시작 < 신규 종료
Booking.end_time   >  start_time     # 기존 종료 > 신규 시작
```

두 구간이 겹치는 필요충분조건이다. 시작 시각만 비교하면 **기존 예약을 감싸는 요청**(09:00–11:00 위에 08:00–12:00)을 놓치고, 포함 관계만 보면 **부분 겹침**을 놓친다. 양쪽 부등식을 모두 걸어야 걸러진다.

### ④ 사용자 충돌 — 놓치기 쉬운 조건

③만으로는 **한 사람이 같은 시간에 여러 연습실을 잡는 것**을 막지 못한다. 방이 다르므로 ③에는 걸리지 않는다.

```python
Booking.user_id  == current_user.user_id,
Booking.room_id  != room_id,          # 다른 방인데
Booking.start_time < end_time,        # 시간은 겹친다
Booking.end_time   > start_time
```

연습실이 한정된 상황에서 한 명이 여러 방을 선점하면 다른 학생이 쓸 수 없다. **운영상 실제로 발생했던 문제**라 별도 조건으로 분리했다.

### 타임존을 명시적으로 고정

```python
seoul_tz = ZoneInfo("Asia/Seoul")
now      = datetime.now(seoul_tz)
dt_start = datetime.combine(start_date, start_time, tzinfo=seoul_tz)
```

배포 서버의 시스템 시간대는 UTC인 경우가 많다. 날짜와 시각을 따로 저장한 뒤 비교할 때 타임존을 붙이지 않으면 **9시간이 어긋나 자정 근처 예약이 전날로 처리**된다. 모든 시각 비교에서 `Asia/Seoul`을 명시했다.

### 취소 규칙

```
이미 시작된 예약        → 취소 불가
시작 10분 전 이내       → 취소 불가
본인 예약이 아닌 경우    → 403
```

취소 마감을 둔 이유는, 직전 취소가 반복되면 그 시간을 아무도 쓸 수 없기 때문이다.

---

## 3. 격자 좌표 기반 호실 배치

연습실 평면도를 코드에 하드코딩하지 않았다. `cells` 테이블에 **층과 좌표**로 저장한다.

```
cells
  id · floor · x · y
```

관리자가 화면에서 칸을 배치하면 그 좌표가 DB에 저장되고, 사용자 화면은 이 좌표를 읽어 평면도를 그린다.

**이렇게 한 이유**: 연습실은 공사·용도 변경으로 배치가 바뀐다. 하드코딩하면 그때마다 코드를 고치고 재배포해야 한다. 좌표를 데이터로 두면 **관리자가 직접 바꿀 수 있다.** 층 개념도 `floor` 컬럼 하나로 확장된다.

<div align="center">
<img src="admin_3.png" width="420"/>
<br><sub>관리자 호실 배치 설정 화면</sub>
</div>

---

## 4. 인증과 학부 승인 흐름

이 서비스는 **뉴뮤직 학부 학생만** 쓸 수 있어야 한다. 학교 계정 연동이 없으므로 승인 절차를 두었다.

```
회원가입  →  role = "pending"  →  로그인 차단
                    ↓
           관리자가 학번·이름·전공 확인
                    ↓
              role = "user"  →  로그인 허용
```

`users.role`을 `pending` / `user` / `admin` **enum**으로 두고 기본값을 `pending`으로 설정했다. 별도 승인 테이블 없이 역할 하나로 가입 심사와 권한 관리를 함께 처리한다.

**토큰 정책**
- JWT (HS256), payload에 `user_id` · `role` 포함
- Refresh Token 1일 — 학생들이 매번 로그인하지 않도록
- 매일 오전 6시 전체 자동 로그아웃 — 공용 PC에서 로그인이 남는 것을 방지

---

## 5. 기능

### 학생

| | |
|---|---|
| 예약 | 주 단위 오픈(매주 금요일 09:00) · 1시간 단위 · **최대 2시간 연속** |
| 예약 시간 | 09:00 ~ 23:00 |
| 조회·취소 | 본인 예약 내역, 시작 10분 전까지 취소 |
| 정보 | 연습실별 장비 목록, 공지사항 |

### 관리자

| | |
|---|---|
| 사용자 | 학부 인증 승인(`pending` → `user`), 상세 조회, 삭제 |
| 호실 | 예약 가능 여부(공사 등), 이름·장비 관리, **격자 배치 편집** |
| 예약 | 전체 예약 내역 조회·관리 |
| 공지 | 게시물 작성·수정 |

---

## 6. 기술 스택 · 구조

| | |
|---|---|
| Backend | **FastAPI** · SQLAlchemy · MySQL |
| Auth | JWT (HS256) · Refresh Token |
| Frontend | HTML / CSS / Vanilla JS (프레임워크 없음) |
| 배포 | Docker · Cloudtype |

```
├── backend/app/
│   ├── models/       user · room · cell · booking · notice
│   ├── routers/      auth · user · room · cell · booking · admin_booking · notice · health
│   ├── schemas/      Pydantic 요청·응답
│   ├── database.py · main.py
│   └── .env.example
├── frontend/
│   ├── index.html                    로그인 · 예약
│   ├── admin_booking.html            예약 관리
│   ├── admin_booking_list.html       예약 내역
│   ├── admin_making_room.html        호실 배치 편집
│   ├── admin_userlist.html           사용자 승인·관리
│   ├── js/   main · user · admin · register · consent-modal
│   └── css/  common · user · admin · consent-modal
└── Dockerfile
```

프론트엔드는 프레임워크 없이 순수 JS로 구성했다. 페이지 수가 적고 상태가 단순해 빌드 파이프라인을 두는 이득이 크지 않다고 판단했다.

---

## 7. 실행 방법

```bash
cd backend
cp .env.example .env          # DB 접속 정보, SECRET_KEY 설정
./.venv/bin/python -m uvicorn app.main:app --reload
```

API 문서: `http://localhost:8000/docs`

**Docker**
```bash
docker build -t musicstudio . && docker run -p 8000:8000 --env-file backend/.env musicstudio
```

---

## 8. 알려진 한계와 다음 단계

**동시성 — 현재 방어되지 않음**

예약 생성은 `SELECT`(충돌 검사) → `INSERT`(생성) 순서로 동작한다. 두 요청이 **정확히 같은 순간**에 같은 시간대를 요청하면, 둘 다 충돌 검사를 통과한 뒤 둘 다 삽입될 수 있다.

주 단위 오픈(매주 금요일 09:00)에 요청이 몰리는 구조라 실제로 발생 가능한 시나리오다. 해결 방향은 두 가지다.

- `(room_id, start_date, start_time)`에 **DB 유니크 제약**을 걸어 두 번째 삽입을 실패시킨다
- 충돌 검사 시 `SELECT ... FOR UPDATE`로 행을 잠근다

전자가 단순하고 확실하다. 현재는 예약 단위가 1시간 고정이므로 유니크 제약으로 대부분 커버된다.

**그 외 예정**
- 공지사항 첨부파일
- DB 백업 체계
- 관리자용 예약 기록 일괄 삭제
- 토큰 만료 정책 재조정

---

<div align="center">

**이종하** · [GitHub](https://github.com/bell-ha) · [Portfolio](https://bell-ha.github.io)

</div>
