package com.musicstudio.academy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.musicstudio.academy.domain.SessionPlanner;
import com.musicstudio.academy.domain.SessionPlanner.Planned;
import com.musicstudio.academy.domain.SessionPlanner.Slot;

/** 고정 일정 → 회차 시각 (순수 함수) */
class SessionPlannerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void 날짜순_같은_날은_시각순으로_만든다() {
        List<Planned> p = SessionPlanner.between(List.of(new Slot(DayOfWeek.THURSDAY, LocalTime.of(16, 0)),
                        new Slot(DayOfWeek.TUESDAY, LocalTime.of(18, 0)), new Slot(DayOfWeek.TUESDAY, LocalTime.of(9, 0))),
                SEOUL, 50, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));
        assertThat(p).extracting(x -> x.startsAt().atZone(SEOUL).toLocalDateTime().toString())
                .containsExactly("2026-10-13T09:00", "2026-10-13T18:00", "2026-10-15T16:00");
        assertThat(p.getFirst().endsAt()).isEqualTo(p.getFirst().startsAt().plusSeconds(50 * 60));
    }

    @Test
    void 서머타임이_바뀌는_주에도_매주_같은_현지_시각이다() {
        ZoneId newYork = ZoneId.of("America/New_York"); // 2026-11-01 서머타임 끝
        List<Planned> p = SessionPlanner.between(List.of(new Slot(DayOfWeek.MONDAY, LocalTime.of(16, 0))),
                newYork, 60, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2));
        assertThat(p).extracting(x -> x.startsAt().atZone(newYork).toLocalTime()).containsOnly(LocalTime.of(16, 0));
        assertThat(p.get(1).startsAt().getEpochSecond() - p.get(0).startsAt().getEpochSecond())
                .isEqualTo(7 * 24 * 3600 + 3600); // 실제 간격은 한 시간 더 길다
    }

    @Test
    void 자정을_넘는_레슨() {
        assertThat(SessionPlanner.crossesMidnight(LocalTime.of(23, 0), 60)).isFalse(); // 24:00에 끝남
        assertThat(SessionPlanner.crossesMidnight(LocalTime.of(23, 30), 50)).isTrue();
    }
}
