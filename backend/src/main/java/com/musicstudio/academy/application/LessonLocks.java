package com.musicstudio.academy.application;

import java.util.Collection;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 레슨 회차를 바꾸는 모든 경로가 부르는 잠금 (ADR 0010).
 * 순서: 수강 → 강사 → 원생, 같은 종류가 여럿이면 id 오름차순. 수강을 잠근 뒤 다시 읽고, 그 값으로 강사·원생을 잠근다. 이 한 규칙으로 강사 변경(옛·새 강사),
 * 그날 전체 휴강(수강 여럿), 보강이 서로 교착하지 않는다. 트랜잭션 단위라 커밋·롤백 때 풀린다.
 * 키가 날짜가 아니라 사람인 이유: 일정 하나가 수십 날짜에 걸치고, 일정 변경은 드물다.
 */
@Component
class LessonLocks {

    private final JdbcClient jdbc;

    LessonLocks(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 1단계: 수강. 강사·원생은 수강 잠금을 쥔 동안에만 바뀌므로, 이걸 잡은 뒤 다시 읽은 값이 그 트랜잭션 동안 그대로다 */
    void lockEnrollments(Collection<Long> enrollments) {
        enrollments.stream().distinct().sorted().forEach(id -> advisory("lesson:enrollment:" + id));
    }

    /**
     * 2단계: 수강 잠금 뒤 다시 읽은 강사·원생 (강사 → 원생). 잠그기 전에 읽은 값으로 키를 정하면, 그사이 강사가 바뀌었을 때
     * 옛 강사를 잠근 채 새 강사의 회차를 만든다 (교차 리뷰 28 1-1).
     */
    void lockPeople(Collection<Long> teachers, Collection<Long> students) {
        teachers.stream().distinct().sorted().forEach(id -> advisory("lesson:teacher:" + id));
        students.stream().distinct().sorted().forEach(id -> advisory("lesson:student:" + id));
    }

    private void advisory(String key) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", key).query().listOfRows();
    }
}
