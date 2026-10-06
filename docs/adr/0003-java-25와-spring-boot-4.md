# 0003. Java 25와 Spring Boot 4.1을 쓴다

| 상태 | 날짜 |
|---|---|
| 채택 | 2026-10-06 |

## 맥락
v1은 Python FastAPI였다. v2는 트랜잭션, 동시성 제어, 배치, 외부 연동(결제, 알림)이 핵심이다. 1인 개발이고 Spring 경험은 없다.

## 결정
Java 25 (LTS)와 Spring Boot 4.1을 쓴다. 데이터 접근은 Spring Data JPA를 기본으로 하고, 겹침 검사나 집계처럼 SQL이 중요한 곳은 SQL을 직접 쓴다.

## 검토한 대안
| 대안 | 장점 | 버린 이유 |
|---|---|---|
| FastAPI 유지 | 익숙하다. v1 경험을 그대로 쓴다 | 트랜잭션 경계, 배치(Spring Batch), 분산 잠금(ShedLock) 같은 도구가 Spring 쪽에 더 갖춰져 있다. 국내 백엔드 채용도 Java/Spring이 다수다 |
| Spring Boot 3.5 | 자료가 가장 많다 | 2026-06-30에 무상 지원이 끝났다 |
| Spring Boot 4.0 | | 2026-12-31에 무상 지원이 끝난다 |
| Java 21 | 자료가 많다 | Java 25에서 가상 스레드가 `synchronized` 안에서 고정되는 문제(JEP 491)가 해결됐다 |
| Kotlin | 간결하다 | 언어와 프레임워크를 동시에 새로 배우는 부담이 크다 |

## 결과
- Spring을 배우는 시간이 MVP 일정에 들어간다.
- Spring Boot 4는 Jackson 3이 기본이다. Boot 3 기준 예제를 그대로 쓰면 컴파일이 깨지는 곳이 있다.
- JPA가 SQL을 숨기는 것을 막기 위해, 개발 중에는 실행되는 SQL을 로그로 켜 두고 확인한다.

## 참고
- https://endoflife.date/spring-boot
- https://openjdk.org/jeps/491
- https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes
