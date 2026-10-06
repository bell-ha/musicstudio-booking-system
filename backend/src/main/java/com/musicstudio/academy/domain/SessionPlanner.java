package com.musicstudio.academy.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 고정 주간 일정에서 회차 시각을 만든다. DB와 시계를 모르는 순수 함수라 단위 테스트로 검증한다.
 * 현지 날짜·시각을 기관 시간대로 바꾸므로 DST 지역에서도 매주 같은 현지 시각이다.
 * (DST로 없는 시각이면 java.time 규칙대로 뒤로 밀린다.)
 */
public final class SessionPlanner {

    private SessionPlanner() {
    }

    public record Slot(DayOfWeek day, LocalTime time) {
    }

    public record Planned(Instant startsAt, Instant endsAt, LocalDate localDate) {
    }

    /** from부터 until까지(둘 다 포함) 날짜순, 같은 날은 시각순 */
    public static List<Planned> between(List<Slot> slots, ZoneId zone, int minutes, LocalDate from, LocalDate until) {
        List<Slot> ordered = slots.stream().sorted(Comparator.comparing(Slot::time)).toList();
        List<Planned> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(until); d = d.plusDays(1)) {
            for (Slot s : ordered) {
                if (s.day() == d.getDayOfWeek()) {
                    Instant start = d.atTime(s.time()).atZone(zone).toInstant();
                    out.add(new Planned(start, start.plus(Duration.ofMinutes(minutes)), d));
                }
            }
        }
        return out;
    }

    /** 레슨이 현지 자정을 넘는가 (연습실과 같이 하루 안에서 끝나야 한다) */
    public static boolean crossesMidnight(LocalTime start, int minutes) {
        return start.toSecondOfDay() + minutes * 60L > Duration.ofDays(1).toSeconds(); // 24:00에 끝나는 것은 된다
    }
}
