package com.musicstudio.practice.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.musicstudio.common.error.ApiException;

/**
 * 기관의 연습실 예약 정책 (FR-PR-04). organization.practice_policy jsonb에 저장한다.
 * 시각은 "HH:mm" 문자열로 둔다. JSON 모양을 단순하게 두고 검증은 validate()에서 한 번에 한다.
 *
 * @param hours 요일별 [시작, 끝]. null이면 그 요일은 휴무
 */
public record PracticePolicy(Map<DayOfWeek, List<String>> hours,
                             int slotMinutes,
                             int maxContinuousMinutes,
                             int dailyMaxMinutes,
                             Open open,
                             int cancelDeadlineMinutes) {

    /** 정책을 저장한 적 없는 기관에 쓰는 기본값. DB에 쓰지 않는다. */
    public static final PracticePolicy DEFAULT;

    static {
        Map<DayOfWeek, List<String>> hours = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            hours.put(day, List.of("09:00", "22:00"));
        }
        DEFAULT = new PracticePolicy(hours, 30, 120, 180, new Open(Open.ROLLING, null, null, 14), 10);
    }

    private static final Set<Integer> SLOTS = Set.of(30, 60);

    /**
     * @param mode ROLLING(오늘을 포함해 days일) 또는 WEEKLY(dayOfWeek time에 다음 주 월~일을 연다)
     */
    public record Open(String mode, DayOfWeek dayOfWeek, String time, Integer days) {
        public static final String ROLLING = "ROLLING";
        public static final String WEEKLY = "WEEKLY";
    }

    /** 위반이 있으면 400 INVALID_POLICY와 필드별 오류. */
    public PracticePolicy validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!SLOTS.contains(slotMinutes)) {
            errors.put("slotMinutes", "30분 또는 60분만 쓸 수 있습니다");
        } else {
            if (maxContinuousMinutes <= 0 || maxContinuousMinutes % slotMinutes != 0) {
                errors.put("maxContinuousMinutes", "시간 단위의 배수여야 합니다");
            }
            if (dailyMaxMinutes < maxContinuousMinutes || dailyMaxMinutes % slotMinutes != 0) {
                errors.put("dailyMaxMinutes", "1회 최대 이상이고 시간 단위의 배수여야 합니다");
            }
            if (hours == null || hours.size() != 7) {
                errors.put("hours", "일곱 요일을 모두 정해 주세요");
            } else {
                hours.forEach((day, range) -> {
                    if (range != null && !validRange(range)) {
                        errors.put("hours." + day.name().substring(0, 3), "시작이 끝보다 앞서고 시간 단위에 맞아야 합니다");
                    }
                });
            }
        }
        if (cancelDeadlineMinutes < 0) {
            errors.put("cancelDeadlineMinutes", "0 이상이어야 합니다");
        }
        if (open == null || !(Open.ROLLING.equals(open.mode()) || Open.WEEKLY.equals(open.mode()))) {
            errors.put("open.mode", "ROLLING 또는 WEEKLY입니다");
        } else if (Open.ROLLING.equals(open.mode()) && (open.days() == null || open.days() < 1)) {
            errors.put("open.days", "1일 이상이어야 합니다");
        } else if (Open.WEEKLY.equals(open.mode()) && (open.dayOfWeek() == null || parse(open.time()) == null)) {
            errors.put("open.dayOfWeek", "여는 요일과 시각을 정해 주세요");
        }
        if (!errors.isEmpty()) {
            throw ApiException.invalidFields("INVALID_POLICY", errors);
        }
        return this;
    }

    private boolean validRange(List<String> range) {
        if (range.size() != 2) {
            return false;
        }
        LocalTime from = parse(range.get(0));
        LocalTime to = parse(range.get(1));
        return from != null && to != null && from.isBefore(to)
                && aligned(from) && aligned(to);
    }

    private boolean aligned(LocalTime t) {
        return t.getSecond() == 0 && (t.getHour() * 60 + t.getMinute()) % slotMinutes == 0;
    }

    static LocalTime parse(String value) {
        try {
            return value == null ? null : LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
