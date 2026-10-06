package com.musicstudio.practice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.musicstudio.common.error.ApiException;

/** 예약 규칙 R3·R5·R6의 경계. 시각은 기관 시간대(서울) 기준으로 고정한다. */
class BookingRulesTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-10-09는 금요일이다.
    static final LocalDate FRIDAY = LocalDate.of(2026, 10, 9);
    static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 10, 12);

    static final PracticePolicy DEFAULT = PracticePolicy.DEFAULT; // 09~22, 30분, 최대 120분, ROLLING 14일

    @Test
    void 단위에_맞고_운영_시간_안이면_현지_날짜를_돌려준다() {
        LocalDate date = BookingRules.check(DEFAULT, SEOUL, at(FRIDAY, 8, 0), at(FRIDAY, 14, 0), at(FRIDAY, 15, 30));
        assertThat(date).isEqualTo(FRIDAY);
    }

    @Test
    void 단위가_어긋나면_거절() {
        assertCode("SLOT_MISALIGNED", () -> BookingRules.check(DEFAULT, SEOUL, at(FRIDAY, 8, 0),
                at(FRIDAY, 14, 15), at(FRIDAY, 15, 0)));
    }

    @Test
    void 운영_시간_밖이나_자정을_넘으면_거절() {
        assertCode("OUTSIDE_OPERATING_HOURS", () -> BookingRules.check(DEFAULT, SEOUL, at(FRIDAY, 8, 0),
                at(FRIDAY, 21, 30), at(FRIDAY, 22, 30)));
        assertCode("OUTSIDE_OPERATING_HOURS", () -> BookingRules.check(DEFAULT, SEOUL, at(FRIDAY, 8, 0),
                at(FRIDAY, 23, 30), at(FRIDAY.plusDays(1), 0, 30)));
    }

    @Test
    void 지난_시간은_거절() {
        assertCode("IN_PAST", () -> BookingRules.check(DEFAULT, SEOUL, at(FRIDAY, 14, 1),
                at(FRIDAY, 14, 0), at(FRIDAY, 15, 0)));
    }

    @Test
    void 최대_연속을_넘으면_거절() {
        assertCode("MAX_CONTINUOUS_EXCEEDED", () -> BookingRules.check(DEFAULT, SEOUL, at(FRIDAY, 8, 0),
                at(FRIDAY, 10, 0), at(FRIDAY, 12, 30)));
    }

    @Test
    void ROLLING은_오늘을_포함해_days일() {
        PracticePolicy oneDay = withOpen(new PracticePolicy.Open("ROLLING", null, null, 1));
        assertThat(BookingRules.opensAt(oneDay, SEOUL, at(FRIDAY, 23, 59), FRIDAY)).isNull();
        assertThat(BookingRules.opensAt(oneDay, SEOUL, at(FRIDAY, 23, 59), FRIDAY.plusDays(1)))
                .isEqualTo(at(FRIDAY.plusDays(1), 0, 0));
        // 서울 자정이 지나면 다음 날이 열린다
        assertThat(BookingRules.opensAt(oneDay, SEOUL, at(FRIDAY.plusDays(1), 0, 0), FRIDAY.plusDays(1))).isNull();
    }

    @Test
    void WEEKLY는_금요일_9시_정각에_다음_주가_열린다() {
        PracticePolicy weekly = withOpen(new PracticePolicy.Open("WEEKLY", DayOfWeek.FRIDAY, "09:00", null));

        assertThat(BookingRules.opensAt(weekly, SEOUL, at(FRIDAY, 8, 59), NEXT_MONDAY)).isEqualTo(at(FRIDAY, 9, 0));
        assertThat(BookingRules.opensAt(weekly, SEOUL, at(FRIDAY, 9, 0), NEXT_MONDAY)).isNull();
        assertThat(BookingRules.opensAt(weekly, SEOUL, at(FRIDAY, 9, 0), NEXT_MONDAY.plusDays(6))).isNull();
        // 다다음 주는 다음 주 금요일에 열린다
        assertThat(BookingRules.opensAt(weekly, SEOUL, at(FRIDAY, 9, 0), NEXT_MONDAY.plusDays(7)))
                .isEqualTo(at(FRIDAY.plusDays(7), 9, 0));
        // 이번 주 남은 날은 이미 열려 있다
        assertThat(BookingRules.opensAt(weekly, SEOUL, at(FRIDAY, 8, 0), FRIDAY.plusDays(1))).isNull();
    }

    @Test
    void 일요일에_여는_정책도_같은_공식으로_계산한다() {
        PracticePolicy sunday = withOpen(new PracticePolicy.Open("WEEKLY", DayOfWeek.SUNDAY, "20:00", null));
        LocalDate thisSunday = FRIDAY.plusDays(2);
        assertThat(BookingRules.opensAt(sunday, SEOUL, at(thisSunday, 19, 59), NEXT_MONDAY))
                .isEqualTo(at(thisSunday, 20, 0));
    }

    private static PracticePolicy withOpen(PracticePolicy.Open open) {
        PracticePolicy d = PracticePolicy.DEFAULT;
        return new PracticePolicy(d.hours(), d.slotMinutes(), d.maxContinuousMinutes(), d.dailyMaxMinutes(), open,
                d.cancelDeadlineMinutes());
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(SEOUL).toInstant();
    }

    private static void assertCode(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
