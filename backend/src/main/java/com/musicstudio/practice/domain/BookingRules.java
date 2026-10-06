package com.musicstudio.practice.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import com.musicstudio.common.error.ApiException;

/**
 * DB 없이 판단할 수 있는 예약 규칙 (UC-23의 R3, R5, R6). 현재 시각은 인자로 받는다.
 * 같은 방·같은 사람 겹침(R1, R2)과 하루 한도(R4)는 동시성이 걸려 있어 BookingService가 다룬다.
 */
public final class BookingRules {

    private BookingRules() {
    }

    /** 통과하면 기관 현지 날짜(usage_date)를 돌려준다. */
    public static LocalDate check(PracticePolicy policy, ZoneId zone, Instant now, Instant startsAt, Instant endsAt) {
        ZonedDateTime start = startsAt.atZone(zone);
        ZonedDateTime end = endsAt.atZone(zone);
        LocalDate date = start.toLocalDate();

        if (!endsAt.isAfter(startsAt) || !aligned(start.toLocalTime(), policy) || !aligned(end.toLocalTime(), policy)) {
            throw violation("SLOT_MISALIGNED", "R5", "시간 단위(%d분)에 맞춰 골라 주세요".formatted(policy.slotMinutes()));
        }
        if (!end.toLocalDate().equals(date) || !withinHours(policy, date, start.toLocalTime(), end.toLocalTime())) {
            throw violation("OUTSIDE_OPERATING_HOURS", "R5", "운영 시간이 아닙니다");
        }
        if (startsAt.isBefore(now)) {
            throw violation("IN_PAST", "R5", "지난 시간은 예약할 수 없습니다");
        }
        if (Duration.between(startsAt, endsAt).toMinutes() > policy.maxContinuousMinutes()) {
            throw violation("MAX_CONTINUOUS_EXCEEDED", "R3",
                    "한 번에 %d분까지 예약할 수 있습니다".formatted(policy.maxContinuousMinutes()));
        }
        Instant opensAt = opensAt(policy, zone, now, date);
        if (opensAt != null) {
            throw violation("NOT_OPEN_YET", "R6", "아직 예약이 열리지 않은 날입니다").with("opensAt", opensAt.atZone(zone).toOffsetDateTime());
        }
        return date;
    }

    /** 그날 예약이 아직 열리지 않았으면 열리는 시각, 열렸으면 null (R6). 지난 날짜는 R5가 따로 막는다. */
    public static Instant opensAt(PracticePolicy policy, ZoneId zone, Instant now, LocalDate date) {
        PracticePolicy.Open open = policy.open();
        LocalDate today = now.atZone(zone).toLocalDate();
        if (PracticePolicy.Open.ROLLING.equals(open.mode())) {
            // 오늘을 포함해 days일: today ~ today + days - 1
            LocalDate firstClosed = today.plusDays(open.days());
            return date.isBefore(firstClosed) ? null : date.minusDays(open.days() - 1L).atStartOfDay(zone).toInstant();
        }
        // WEEKLY: 날짜 d가 속한 주(월~일)는 그 전 주의 dayOfWeek time에 열린다.
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate openDay = monday.minusWeeks(1).plusDays(open.dayOfWeek().getValue() - 1L);
        Instant opens = openDay.atTime(PracticePolicy.parse(open.time())).atZone(zone).toInstant();
        return now.isBefore(opens) ? opens : null;
    }

    /** 그 요일 운영 시간. 휴무면 null. */
    public static List<LocalTime> hours(PracticePolicy policy, LocalDate date) {
        List<String> range = policy.hours().get(date.getDayOfWeek());
        return range == null ? null : List.of(PracticePolicy.parse(range.get(0)), PracticePolicy.parse(range.get(1)));
    }

    private static boolean withinHours(PracticePolicy policy, LocalDate date, LocalTime from, LocalTime to) {
        List<LocalTime> hours = hours(policy, date);
        return hours != null && !from.isBefore(hours.get(0)) && !to.isAfter(hours.get(1));
    }

    private static boolean aligned(LocalTime t, PracticePolicy policy) {
        return t.getSecond() == 0 && t.getNano() == 0 && (t.getHour() * 60 + t.getMinute()) % policy.slotMinutes() == 0;
    }

    private static ApiException violation(String code, String rule, String title) {
        return ApiException.policyViolation(code, title).with("rule", rule);
    }
}
