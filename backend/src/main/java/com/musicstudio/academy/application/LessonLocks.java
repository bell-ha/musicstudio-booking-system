package com.musicstudio.academy.application;

import java.util.Collection;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 레슨 회차를 바꾸는 모든 경로가 부르는 잠금 하나 (ADR 0010).
 * 순서: 수강 → 강사 → 원생, 같은 종류가 여럿이면 id 오름차순. 이 한 규칙으로 강사 변경(옛·새 강사),
 * 그날 전체 휴강(수강 여럿), 보강이 서로 교착하지 않는다. 트랜잭션 단위라 커밋·롤백 때 풀린다.
 * 키가 날짜가 아니라 사람인 이유: 일정 하나가 수십 날짜에 걸치고, 일정 변경은 드물다.
 */
@Component
class LessonLocks {

    private final JdbcClient jdbc;

    LessonLocks(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void lock(Collection<Long> enrollments, Collection<Long> teachers, Collection<Long> students) {
        enrollments.stream().distinct().sorted().forEach(id -> advisory("lesson:enrollment:" + id));
        teachers.stream().distinct().sorted().forEach(id -> advisory("lesson:teacher:" + id));
        students.stream().distinct().sorted().forEach(id -> advisory("lesson:student:" + id));
    }

    private void advisory(String key) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", key).query().listOfRows();
    }
}
