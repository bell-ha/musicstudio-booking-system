package com.musicstudio.practice.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.practice.domain.BookingRules;
import com.musicstudio.practice.domain.Floor;
import com.musicstudio.practice.domain.FloorRepository;
import com.musicstudio.practice.domain.PracticeBooking;
import com.musicstudio.practice.domain.PracticeBookingRepository;
import com.musicstudio.practice.domain.PracticeBookingRepository.BookingRow;
import com.musicstudio.practice.domain.PracticePolicy;
import com.musicstudio.practice.domain.PracticeSettings;
import com.musicstudio.practice.domain.PracticeSettingsRepository;
import com.musicstudio.practice.domain.Room;
import com.musicstudio.practice.domain.RoomRepository;

/**
 * 연습실 예약 (UC-23~25). 대표 문제 1: 동시 요청에서도 같은 방·같은 사람의 예약이 겹치지 않고
 * 하루 한도를 넘지 않는다 (ADR 0010).
 */
@Service
public class BookingService {

    private final RoomRepository rooms;
    private final FloorRepository floors;
    private final PracticeBookingRepository bookings;
    private final PracticeSettingsRepository settings;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    BookingService(RoomRepository rooms, FloorRepository floors, PracticeBookingRepository bookings,
                   PracticeSettingsRepository settings, JdbcClient jdbc, PlatformTransactionManager txManager,
                   Clock clock) {
        this.rooms = rooms;
        this.floors = floors;
        this.bookings = bookings;
        this.settings = settings;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    /**
     * 1. 트랜잭션 밖: 잠금 없이 싼 실패를 먼저 거른다 (방, 정책 R3·R5·R6).
     * 2. 트랜잭션 안: advisory lock 두 개를 방 → 사람 순서로 잡는다.
     *    잠금 순서는 방 → 사람으로 고정한다. 나중에 잠금을 거는 다른 경로(대신 예약, 점검 일괄 취소 등)도 이 순서를 따른다.
     *    - 방·날짜: 같은 방 요청을 줄 세운다. EXCLUDE 제약만 두면 몰릴 때 대기 트랜잭션끼리 교착이 난다(실험 0001).
     *    - 사람·날짜: R2(같은 사람 겹침)와 R4(하루 한도)를 검사한다. 예약은 자정을 넘지 않으므로 같은 날 안에서 끝난다.
     * 3. 배타 제약(R1, R2)은 잠금을 빠뜨린 경로가 생겨도 겹친 예약을 막는 마지막 방어선이다.
     */
    public PracticeBooking book(long orgId, long memberId, long roomId, Instant startsAt, Instant endsAt) {
        Room room = rooms.findByIdAndOrganizationId(roomId, orgId).orElseThrow(() -> ApiException.notFound("방을 찾을 수 없습니다"));
        if (!room.canBeBooked()) {
            throw ApiException.policyViolation("ROOM_NOT_BOOKABLE", "지금은 예약할 수 없는 방입니다");
        }
        PracticeSettings s = settings.findById(orgId);
        PracticePolicy policy = s.policy();
        ZoneId zone = s.zone();
        LocalDate date = BookingRules.check(policy, zone, Instant.now(clock), startsAt, endsAt);

        return tx.execute(status -> {
            lock("pr:room:" + roomId + ":" + date);
            lock("pr:member:" + memberId + ":" + date);

            List<PracticeBooking> sameDay = bookings.findByMemberIdAndUsageDateAndCanceledAtIsNull(memberId, date);
            for (PracticeBooking b : sameDay) {
                if (b.overlaps(startsAt, endsAt)) {
                    throw personOverlap().with("conflictingBooking", Map.of("bookingId", b.getId(),
                            "roomId", b.getRoomId(), "startsAt", b.getStartsAt().atZone(zone).toOffsetDateTime(),
                            "endsAt", b.getEndsAt().atZone(zone).toOffsetDateTime()));
                }
            }
            long used = sameDay.stream().mapToLong(PracticeBooking::minutes).sum();
            long requested = java.time.Duration.between(startsAt, endsAt).toMinutes();
            if (used + requested > policy.dailyMaxMinutes()) {
                throw ApiException.policyViolation("DAILY_LIMIT_EXCEEDED", "하루 최대 이용 시간을 넘습니다")
                        .with("rule", "R4").with("limitMinutes", policy.dailyMaxMinutes())
                        .with("usedMinutes", used).with("requestedMinutes", requested);
            }
            try {
                return bookings.saveAndFlush(new PracticeBooking(orgId, roomId, memberId, startsAt, endsAt, date));
            } catch (DataIntegrityViolationException e) {
                String message = String.valueOf(e.getMostSpecificCause().getMessage());
                if (message.contains("ex_booking_room")) {
                    throw ApiException.conflict("ROOM_OVERLAP", "방금 다른 사람이 겹치는 시간을 예약했습니다")
                            .with("rule", "R1");
                }
                if (message.contains("ex_booking_member")) {
                    throw personOverlap(); // 위 잠금 덕분에 사실상 생기지 않지만 DB가 마지막으로 막는다
                }
                throw e;
            }
        });
    }

    /** 본인은 취소 마감 전까지, 관리자는 마감과 무관하게 사유를 적어 취소한다 (UC-24, UC-25). */
    @Transactional
    public PracticeBooking cancel(long orgId, long actorMemberId, boolean manager, long bookingId, String reason) {
        PracticeBooking b = bookings.findByIdAndOrganizationId(bookingId, orgId)
                .filter(x -> manager || x.getMemberId().equals(actorMemberId))
                .orElseThrow(() -> ApiException.notFound("예약을 찾을 수 없습니다"));
        if (b.getCanceledAt() != null) {
            throw ApiException.conflict("ALREADY_CANCELED", "이미 취소된 예약입니다");
        }
        Instant now = Instant.now(clock);
        if (!b.getEndsAt().isAfter(now)) {
            throw ApiException.policyViolation("BOOKING_ENDED", "이미 끝난 예약은 취소할 수 없습니다");
        }
        if (manager) {
            if (reason == null || reason.isBlank()) {
                throw ApiException.invalid("REASON_REQUIRED", "취소 사유를 적어 주세요");
            }
        } else {
            int deadline = settings.findById(orgId).policy().cancelDeadlineMinutes();
            if (!now.isBefore(b.getStartsAt().minusSeconds(deadline * 60L))) {
                throw ApiException.policyViolation("CANCEL_DEADLINE_PASSED",
                        "시작 %d분 전까지만 취소할 수 있습니다".formatted(deadline));
            }
        }
        // 학생과 관리자가 동시에 취소해도 한 건만 반영한다. 읽은 뒤 덮어쓰지 않고 조건부 UPDATE로 바꾼다.
        if (bookings.cancel(bookingId, actorMemberId, now, manager ? reason.trim() : null) == 0) {
            throw ApiException.conflict("ALREADY_CANCELED", "이미 취소된 예약입니다");
        }
        return bookings.findById(bookingId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<BookingRow> mine(long memberId, LocalDate from, LocalDate to) {
        return bookings.findMine(memberId, from, to);
    }

    @Transactional(readOnly = true)
    public List<BookingRow> byDate(long orgId, LocalDate date) {
        return bookings.findByDate(orgId, date);
    }

    /** 지도 (UC-23 1~2): at부터 한 칸 동안 그 층 방마다의 상태. 쿼리 1번. */
    @Transactional(readOnly = true)
    public Availability availability(long orgId, long memberId, long floorId, Instant at) {
        Floor floor = floors.findByIdAndOrganizationId(floorId, orgId).orElseThrow(() -> ApiException.notFound("층을 찾을 수 없습니다"));
        PracticeSettings s = settings.findById(orgId);
        Instant to = at.plusSeconds(s.policy().slotMinutes() * 60L);
        String dayReason = reason(s.policy(), s.zone(), at, to);

        List<Room> floorRooms = rooms.findByFloorId(floorId);
        Map<Long, PracticeBooking> booked = floorRooms.isEmpty() ? Map.of()
                : bookings.findOverlapping(floorRooms.stream().map(Room::getId).toList(), at, to).stream()
                        .collect(Collectors.toMap(PracticeBooking::getRoomId, Function.identity(), (a, b) -> a));

        List<RoomStatus> statuses = floorRooms.stream().map(r -> {
            PracticeBooking b = booked.get(r.getId());
            if (b != null) {
                return new RoomStatus(r.getId(), Status.BOOKED, b.getMemberId() == memberId, null);
            }
            if (!r.isBookable()) {
                return new RoomStatus(r.getId(), Status.UNAVAILABLE, false, "UNDER_MAINTENANCE");
            }
            return dayReason == null ? new RoomStatus(r.getId(), Status.AVAILABLE, false, null)
                    : new RoomStatus(r.getId(), Status.UNAVAILABLE, false, dayReason);
        }).toList();
        return new Availability(floor.getId(), floor.getVersion(), at, statuses);
    }

    /** 시간표 (UC-23 4): 그 방 그날의 칸 목록. 쿼리 1번. 규칙 판단은 예약 요청과 같은 BookingRules를 쓴다. */
    @Transactional(readOnly = true)
    public Timetable timetable(long orgId, long memberId, long roomId, LocalDate date) {
        Room room = rooms.findByIdAndOrganizationId(roomId, orgId).orElseThrow(() -> ApiException.notFound("방을 찾을 수 없습니다"));
        PracticeSettings s = settings.findById(orgId);
        PracticePolicy policy = s.policy();
        ZoneId zone = s.zone();
        List<LocalTime> hours = BookingRules.hours(policy, date);
        if (hours == null) {
            return new Timetable(roomId, date, true, List.of());
        }
        List<PracticeBooking> dayBookings = bookings.findByRoomIdAndUsageDateAndCanceledAtIsNull(roomId, date);
        List<Slot> slots = new ArrayList<>();
        for (LocalTime t = hours.get(0); t.isBefore(hours.get(1)); t = t.plusMinutes(policy.slotMinutes())) {
            Instant start = date.atTime(t).atZone(zone).toInstant();
            Instant end = start.plusSeconds(policy.slotMinutes() * 60L);
            PracticeBooking b = dayBookings.stream().filter(x -> x.overlaps(start, end)).findFirst().orElse(null);
            if (b != null) {
                slots.add(new Slot(start, end, Status.BOOKED, b.getMemberId() == memberId, null));
            } else if (!room.canBeBooked()) {
                slots.add(new Slot(start, end, Status.UNAVAILABLE, false, "ROOM_NOT_BOOKABLE"));
            } else {
                String reason = reason(policy, zone, start, end);
                slots.add(new Slot(start, end, reason == null ? Status.AVAILABLE : Status.UNAVAILABLE, false, reason));
            }
        }
        return new Timetable(roomId, date, false, slots);
    }

    /** 예약 요청과 같은 규칙으로 판단한다. 막히면 그 코드, 아니면 null. */
    private String reason(PracticePolicy policy, ZoneId zone, Instant from, Instant to) {
        try {
            BookingRules.check(policy, zone, Instant.now(clock), from, to);
            return null;
        } catch (ApiException e) {
            return e.code();
        }
    }

    /** 트랜잭션 단위 advisory lock. 커밋·롤백 때 저절로 풀린다. */
    private void lock(String key) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", key).query().listOfRows();
    }

    private static ApiException personOverlap() {
        return ApiException.conflict("PERSON_OVERLAP", "같은 시간에 이미 다른 방을 예약했습니다").with("rule", "R2");
    }

    public enum Status { AVAILABLE, BOOKED, UNAVAILABLE }

    public record RoomStatus(Long roomId, Status status, boolean mine, String reason) {
    }

    public record Availability(Long floorId, long version, Instant at, List<RoomStatus> rooms) {
    }

    public record Slot(Instant start, Instant end, Status status, boolean mine, String reason) {
    }

    public record Timetable(Long roomId, LocalDate date, boolean closed, List<Slot> slots) {
    }
}
