package com.musicstudio.practice.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.organization.api.CurrentMember;
import com.musicstudio.organization.api.OrgRole;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.practice.application.BookingService;
import com.musicstudio.practice.domain.PracticeBooking;
import com.musicstudio.practice.domain.PracticeBookingRepository.BookingRow;
import com.musicstudio.practice.domain.PracticeSettingsRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 지도, 시간표, 예약, 취소, 현황 (API 19~23). 시각은 기관 시간대의 오프셋으로 응답한다. */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}/practice")
class BookingController {

    private final BookingService service;
    private final PracticeSettingsRepository settings;

    BookingController(BookingService service, PracticeSettingsRepository settings) {
        this.service = service;
        this.settings = settings;
    }

    @GetMapping("/floors/{floorId}/availability")
    @OrgRole
    AvailabilityResponse availability(CurrentMember me, @PathVariable long floorId,
                                      @RequestParam OffsetDateTime at) {
        BookingService.Availability a = service.availability(me.organizationId(), me.membershipId(), floorId,
                at.toInstant());
        ZoneId zone = zone(me);
        return new AvailabilityResponse(a.floorId(), a.version(), local(a.at(), zone), a.rooms());
    }

    @GetMapping("/rooms/{roomId}/timetable")
    @OrgRole
    TimetableResponse timetable(CurrentMember me, @PathVariable long roomId,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        BookingService.Timetable t = service.timetable(me.organizationId(), me.membershipId(), roomId, date);
        ZoneId zone = zone(me);
        return new TimetableResponse(t.roomId(), t.date(), t.closed(), t.slots().stream()
                .map(s -> new SlotResponse(local(s.start(), zone), local(s.end(), zone), s.status(), s.mine(), s.reason()))
                .toList());
    }

    /** MVP에서는 학생만 예약한다 (요구사항 Q2). */
    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @OrgRole(MembershipRole.STUDENT)
    BookingResponse book(CurrentMember me, @Valid @RequestBody BookRequest req) {
        PracticeBooking b = service.book(me.organizationId(), me.membershipId(), req.roomId(),
                req.startsAt().toInstant(), req.endsAt().toInstant());
        return BookingResponse.of(b, null, null, zone(me));
    }

    /** 학생은 내 예약(from~to), 관리자는 date를 주면 그날 기관 전체. */
    @GetMapping("/bookings")
    @OrgRole
    List<BookingResponse> bookings(CurrentMember me,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ZoneId zone = zone(me);
        if (date != null) {
            if (!isManager(me)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "FORBIDDEN", "권한이 없습니다");
            }
            return service.byDate(me.organizationId(), date).stream().map(r -> BookingResponse.of(r, zone)).toList();
        }
        LocalDate today = LocalDate.now(zone);
        LocalDate start = from == null ? today : from;
        LocalDate end = to == null ? start.plusDays(30) : to;
        if (end.isBefore(start) || start.plusDays(92).isBefore(end)) {
            throw ApiException.invalid("INVALID_RANGE", "기간은 92일 이내로 골라 주세요");
        }
        return service.mine(me.membershipId(), start, end)
                .stream().map(r -> BookingResponse.of(r, zone)).toList();
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    @OrgRole
    BookingResponse cancel(CurrentMember me, @PathVariable long bookingId, @RequestBody(required = false) CancelRequest req) {
        PracticeBooking b = service.cancel(me.organizationId(), me.membershipId(), isManager(me), bookingId,
                req == null ? null : req.reason());
        return BookingResponse.of(b, null, null, zone(me));
    }

    private ZoneId zone(CurrentMember me) {
        return settings.findById(me.organizationId()).zone();
    }

    private static boolean isManager(CurrentMember me) {
        return me.role() == MembershipRole.OWNER || me.role() == MembershipRole.MANAGER;
    }

    private static OffsetDateTime local(Instant i, ZoneId zone) {
        return i == null ? null : i.atZone(zone).toOffsetDateTime();
    }

    record BookRequest(@NotNull Long roomId, @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt) {
    }

    record CancelRequest(String reason) {
    }

    record AvailabilityResponse(Long floorId, long version, OffsetDateTime at, List<BookingService.RoomStatus> rooms) {
    }

    record SlotResponse(OffsetDateTime start, OffsetDateTime end, BookingService.Status status, boolean mine,
                        String reason) {
    }

    record TimetableResponse(Long roomId, LocalDate date, boolean closed, List<SlotResponse> slots) {
    }

    record BookingResponse(Long id, Long roomId, String roomName, String memberName, OffsetDateTime startsAt,
                           OffsetDateTime endsAt, LocalDate usageDate, OffsetDateTime canceledAt, String cancelReason) {
        static BookingResponse of(PracticeBooking b, String roomName, String memberName, ZoneId zone) {
            return new BookingResponse(b.getId(), b.getRoomId(), roomName, memberName, local(b.getStartsAt(), zone),
                    local(b.getEndsAt(), zone), b.getUsageDate(), local(b.getCanceledAt(), zone), b.getCancelReason());
        }

        static BookingResponse of(BookingRow r, ZoneId zone) {
            String member = r.getMemberName() == null || r.getMemberName().isEmpty() ? null : r.getMemberName();
            return of(r.getBooking(), r.getRoomName(), member, zone);
        }
    }
}
