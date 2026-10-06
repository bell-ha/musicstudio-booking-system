# API 명세 (MVP)

| 항목 | 내용 |
|---|---|
| 버전 | 0.2 |
| 작성일 | 2026-10-06 |
| 근거 | [유스케이스](../requirements/02-유스케이스.md), [ERD](01-ERD.md) |

원칙: 유스케이스 하나에 필요한 엔드포인트만 둔다. 화면 하나가 한 번에 필요한 데이터는 응답 하나에 담는다.

## 1. 공통 규칙

### 경로
| 범위 | 접두사 |
|---|---|
| 기관 밖 (계정, 가입) | `/api/v1` |
| 기관 범위 | `/api/v1/organizations/{orgId}` |
| 연습실 모듈 | `/api/v1/organizations/{orgId}/practice` |
| 학원 관리 모듈 | `/api/v1/organizations/{orgId}/academy` |

모듈 API를 접두사로 나눠서 "모듈이 꺼졌으면 막는다"(ADR 0006)를 필터 한 곳에서 처리한다. 아래 표에서 기관 범위 경로는 `/api/v1/organizations/{orgId}`를 빼고 적는다.

### 검사 순서
기관 범위 요청은 이 순서로 검사하고, 처음 걸린 곳에서 멈춘다.

| 순서 | 검사 | 실패 |
|---|---|---|
| 1 | 로그인 | 401 |
| 2 | 요청자가 `orgId`의 `ACTIVE` 멤버 (`PENDING`이면 아무것도 못 본다) | 403 `not-a-member` |
| 3 | 경로의 모듈이 켜져 있음 | 403 `module-disabled` |
| 4 | 역할 | 403 `forbidden` |
| 5 | 경로의 리소스가 그 기관 것 | 404 |

5번이 403이 아니라 404인 것은, 다른 기관에 그 리소스가 있는지 알려 주지 않기 위해서다. 모든 조회에 `organization_id = :orgId`가 붙으므로 구현으로도 404가 자연스럽다.

### 형식
| 항목 | 규칙 |
|---|---|
| 인증 | JWT 접근 토큰 (`Authorization: Bearer`). 기관과 역할은 넣지 않고 요청마다 membership에서 읽는다. 한 사용자가 여러 기관을 오가기 때문이다. 갱신 토큰은 나중 |
| 시각 | ISO 8601, 오프셋 포함. 응답은 기관 시간대로 준다 (NFR-08) |
| 날짜 | `YYYY-MM-DD`, 기관 현지 날짜 |
| 연락처 | 응답에서는 가린 값 (`010-****-1234`) |
| 취소·상태 변경 | `DELETE`가 아니라 `POST .../cancel`. 행이 남고 상태만 바뀐다 |
| 오류 | RFC 9457 Problem Details (`application/problem+json`). `type`으로 종류를, 확장 필드 `code`로 세부 원인을 준다 |

### 오류 종류
| status | type | 언제 |
|---|---|---|
| 400 | `validation-failed` | 입력 형식. `errors: [{field, message}]` |
| 401 | `unauthenticated` | |
| 403 | `not-a-member`, `module-disabled`, `forbidden` | 검사 순서 2~4 |
| 404 | `not-found` | |
| 409 | `conflict` | 상태 충돌 (이미 멤버, 끝난 수강 변경, 예약이 있는 방을 평면도에서 뺌 등). `code`로 구분 |
| 409 | `booking-conflict` | R1 `ROOM_OVERLAP`, R2 `PERSON_OVERLAP` |
| 412 | `stale-version` | 평면도 `If-Match` 불일치 |
| 422 | `policy-violation` | R3~R6, 점검 중인 방, 취소 마감 지남 |
| 428 | `precondition-required` | 평면도 저장에 `If-Match` 없음 |

R1·R2는 애플리케이션 검사에서 걸리든 DB 배타 제약(SQLSTATE `23P01`)에서 걸리든 **같은 응답**을 준다. 예외 처리기가 제약 이름(`ex_booking_room`, `ex_booking_member`)을 코드로 바꾼다.

## 2. 엔드포인트 (36개)

권한: 누구나 / 로그인 / **O** 소유자 / **M** 관리자 이상 / **T** 강사 / **S** 학생 / 멤버 = 그 기관의 활성 멤버

### 기본
| # | 메서드 | 경로 | 권한 | UC | 비고 |
|---|---|---|---|---|---|
| 1 | POST | /api/v1/auth/signup | 누구나 | 01 | 409 이메일 중복 |
| 2 | POST | /api/v1/auth/login | 누구나 | 01 | 접근 토큰 |
| 3 | GET | /api/v1/me/organizations | 로그인 | 09 | 내 기관, 역할, 상태(대기 포함), 켜진 모듈 |
| 4 | POST | /api/v1/organizations | 로그인 | 02 | 만든 사람이 소유자. 연습실이 켜지면 기본 정책을 채운다 |
| 5 | PATCH | /organizations/{orgId} | O | 03 | 이름, 시간대, `modules` |
| 6 | PATCH | /join-code | M | 06 | `enabled`, `joinForm` (신청 때 받을 항목) |
| 7 | POST | /join-code/regenerate | M | 06 | 새 코드. 이전 코드 무효 |
| 8 | POST | /invitations | M (관리자 초대는 O) | 04 | 응답에만 링크 원문 |
| 9 | POST | /api/v1/invitations/accept | 로그인 | 05 | 본문에 토큰. 404 무효·만료·사용됨, 409 이미 멤버 |
| 10 | POST | /api/v1/join-requests | 로그인 | 06 | 본문에 `code`, `answers`. 404 코드 틀림·꺼짐, 409 이미 멤버·대기 중 |
| 11 | GET | /members | M | 07, 08 | `?status=PENDING` 이면 가입 신청 목록 |
| 12 | PATCH | /members/{membershipId} | M (소유자·관리자 지정은 O) | 07, 08 | `status`(`ACTIVE` 승인, `REJECTED` 거절, `INACTIVE` 비활성화), `role`. 409 마지막 소유자 |

초대 토큰과 가입 코드는 URL이 아니라 POST 본문으로 받는다. 접근 로그와 브라우저 기록에 남지 않게 하려는 것이다.

### 연습실 (`/practice`)
| # | 메서드 | 경로 | 권한 | UC | 비고 |
|---|---|---|---|---|---|
| 13 | GET | /practice/floors | 멤버 | 20, 23 | 층 목록. 각 층의 격자, 벽·복도, 배치된 방, `version`. 관리자에게는 배치 안 된 방도 준다 |
| 14 | POST | /practice/floors | M | 20 | |
| 15 | PUT | /practice/floors/{floorId} | M | 20 | 이름, 순서, 평면도, 방 배치 전체. **`If-Match` 필수.** 428, 412, 409 `ROOM_HAS_FUTURE_BOOKINGS`, 422 격자 밖·방 겹침 |
| 16 | POST | /practice/rooms | M | 21 | |
| 17 | PATCH | /practice/rooms/{roomId} | M | 21 | 이름, 수용 인원, 장비, `bookable` |
| 18 | PUT | /practice/policy | M | 22 | 정책 전체. 현재 정책은 3번 응답(기관 정보)에 담긴다 |
| 19 | GET | /practice/floors/{floorId}/availability | 멤버 | 23 | `?at=`. 방마다 `AVAILABLE` / `BOOKED` / `UNAVAILABLE` |
| 20 | GET | /practice/rooms/{roomId}/timetable | 멤버 | 23 | `?date=`. 시간 칸마다 상태 |
| 21 | POST | /practice/bookings | S | 23 | 409 R1·R2, 422 R3~R6·점검 중 |
| 22 | GET | /practice/bookings | 멤버 | 24, 25 | 학생은 내 예약만 (`?from=&to=`). 관리자는 `?date=`로 그날 전체 |
| 23 | POST | /practice/bookings/{bookingId}/cancel | 본인, M | 24, 25 | 본인은 마감 전까지(422). 관리자는 마감과 무관, `reason` 필수 |

- 평면도 저장(15)은 층 하나를 통째로 바꾼다. 본문의 `rooms`가 그 층에 배치될 방의 전체 목록이고, 빠진 방은 평면도에서 빠진다. 그 방에 앞으로의 예약이 있으면 409로 거절하고 예약 목록을 준다 (UC-20 3a).
- 지도(19)와 시간표(20)는 참고용이다. 최종 판단은 예약 요청(21)이 한다 (UC-23 6a).

### 학원 관리 (`/academy`)
| # | 메서드 | 경로 | 권한 | UC | 비고 |
|---|---|---|---|---|---|
| 24 | GET | /academy/catalog | M | 40 | 과목과 상품 전체. 둘 다 기관당 수십 개라 한 번에 준다 |
| 25 | POST | /academy/subjects | M | 40 | |
| 26 | POST | /academy/products | M | 40 | |
| 27 | PATCH | /academy/products/{productId} | M | 40 | 기존 수강의 금액은 바뀌지 않는다 |
| 28 | GET | /academy/students | M, T | 44, 45 | 관리자: `?subjectId=&teacherId=&status=&expiringWithinDays=`. 강사: 수강 중·일시정지인 담당 학생만 |
| 29 | POST | /academy/students | M | 41 | |
| 30 | PATCH | /academy/students/{studentId} | M | 41 | 학생 계정 연결(`membershipId`), `active` 포함 |
| 31 | GET | /academy/students/{studentId} | M, T, S(본인) | 47, 48 | 원생 정보, 수강 목록, 레슨 기록을 시간순으로. 강사는 담당이거나 기록을 쓴 적 있는 원생, 학생은 공개 기록만 |
| 32 | POST | /academy/enrollments | M | 42 | 409 비활성 강사·원생 |
| 33 | POST | /academy/enrollments/{enrollmentId}/{action} | M | 43 | `extend`, `change-teacher`, `pause`, `resume`, `end`, `refund`. 409 허용되지 않는 전이 |
| 34 | POST | /academy/lesson-records | T | 46 | 본문에 `enrollmentId`. 403 담당 아님 |
| 35 | PATCH | /academy/lesson-records/{recordId} | T(쓴 사람) | 46 | |
| 36 | DELETE | /academy/lesson-records/{recordId} | T(쓴 사람) | 46 | 실제로 지우는 유일한 곳 (UC-46 2a) |

학생의 "내 수강 정보"(UC-48)는 3번 응답에 연결된 `studentId`가 있고, 31번으로 읽는다. 별도 엔드포인트를 두지 않는다.

## 3. 예시

### 지도 (UC-23 1~2)
```http
GET /api/v1/organizations/12/practice/floors/3/availability?at=2026-10-10T14:00:00%2B09:00
```
```json
{ "floorId": 3, "version": 8,
  "rooms": [
    { "roomId": 31, "name": "A101", "status": "AVAILABLE" },
    { "roomId": 32, "name": "A102", "status": "BOOKED", "mine": true },
    { "roomId": 33, "name": "A103", "status": "UNAVAILABLE", "reason": "UNDER_MAINTENANCE" } ] }
```
평면도 모양은 13번에서 한 번 받고, 시각을 바꿀 때는 이 응답만 다시 받는다. `version`이 달라졌으면 평면도를 다시 받는다.

### 예약 409 / 422 (UC-23 6a~6c)
```http
POST /api/v1/organizations/12/practice/bookings

{ "roomId": 31, "startsAt": "2026-10-10T14:00:00+09:00", "endsAt": "2026-10-10T15:30:00+09:00" }
```
```json
{ "type": "https://api.example.com/problems/booking-conflict", "status": 409,
  "title": "이미 예약된 시간입니다", "detail": "방금 다른 사람이 A101의 겹치는 시간을 예약했습니다.",
  "code": "ROOM_OVERLAP", "rule": "R1" }
```
```json
{ "type": "https://api.example.com/problems/policy-violation", "status": 422,
  "title": "하루 최대 이용 시간을 넘습니다",
  "code": "DAILY_LIMIT_EXCEEDED", "rule": "R4", "limitMinutes": 180, "usedMinutes": 120, "requestedMinutes": 90 }
```
R2(`PERSON_OVERLAP`)에는 겹치는 내 예약(`conflictingBooking`)을 함께 준다 (UC-23 6b). 같은 요청을 두 번 보내도 두 번째는 이 응답이 되므로, 화면은 "이미 예약됨"으로 보여 줄 수 있다. 이 응답이 나오도록 애플리케이션 검사는 R2를 R1보다 먼저 한다.

| 규칙 | code |
|---|---|
| R3 | `MAX_CONTINUOUS_EXCEEDED` |
| R4 | `DAILY_LIMIT_EXCEEDED` |
| R5 | `SLOT_MISALIGNED`, `OUTSIDE_OPERATING_HOURS`, `IN_THE_PAST` |
| R6 | `NOT_OPEN_YET` (`opensAt` 포함) |
| 점검 중 | `ROOM_NOT_BOOKABLE` |

### 평면도 저장 412 (UC-20 5a)
```http
PUT /api/v1/organizations/12/practice/floors/3
If-Match: "7"

{ "name": "1층", "sortOrder": 1,
  "layout": { "width": 20, "height": 12, "walls": [[0,0],[1,0]], "corridors": [[5,3],[5,4]] },
  "rooms": [ { "id": 31, "x": 0, "y": 1, "w": 2, "h": 2 }, { "id": 33, "x": 6, "y": 1, "w": 3, "h": 2 } ] }
```
```http
HTTP/1.1 412 Precondition Failed
Content-Type: application/problem+json

{ "type": "https://api.example.com/problems/stale-version", "status": 412,
  "title": "다른 관리자가 먼저 저장했습니다", "detail": "평면도를 다시 불러온 뒤 편집해 주세요.", "currentVersion": 9 }
```
성공하면 200과 `ETag: "8"`. 방은 16번으로 먼저 만들고 여기서는 배치만 한다.
