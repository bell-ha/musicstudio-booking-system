# 음악학원·음악학교 운영 플랫폼

여러 음악학원과 음악학교가 각자 공간을 만들어 쓰는 운영 플랫폼이다. 연습실 예약만 써도 되고, 원생·수강·레슨 기록 관리까지 함께 써도 된다.

> **상태: 설계 중 (v2).** 백엔드 뼈대만 있다.
> v1(단국대학교 뉴뮤직학부 연습실 예약, FastAPI, 2025-03~2026-07 실운영)은 [`legacy-fastapi`](../../tree/legacy-fastapi) 브랜치에 있다.

## 문서

| 문서 | 내용 |
|---|---|
| [요구사항 정의서](docs/requirements/01-요구사항-정의.md) | 배경(v1에서 배운 것), 목표, 사용자, 모듈, 기능·비기능 요구사항 |
| [유스케이스](docs/requirements/02-유스케이스.md) | 액터, 유스케이스 목록, 주요 유스케이스 상세 |
| [결정 기록 (ADR)](docs/adr/README.md) | 기술과 설계 결정, 버린 대안, 이유 |
| [아키텍처](docs/architecture/) | ERD, API 명세, 스키마 초안 |
| [디자인](DESIGN.md) | 화면 디자인 규칙 (색, 글자, 컴포넌트, 반응형) |

## 로컬 실행

필요한 것: Java 25, Docker

```bash
docker compose up -d          # PostgreSQL 17 (개발용)
cd backend && ./gradlew bootRun
curl localhost:8080/actuator/health
```

테스트는 Testcontainers가 일회용 PostgreSQL을 따로 띄우므로 위 DB가 없어도 된다: `cd backend && ./gradlew test`

## 계획한 기술

Java 25 · Spring Boot 4.1 · PostgreSQL · React + Vite + TypeScript · Docker · AWS (Terraform) · GitHub Actions
