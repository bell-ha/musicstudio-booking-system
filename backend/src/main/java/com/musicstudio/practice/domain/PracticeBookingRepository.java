package com.musicstudio.practice.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PracticeBookingRepository extends JpaRepository<PracticeBooking, Long> {

    Optional<PracticeBooking> findByIdAndOrganizationId(Long id, Long organizationId);

    /** 같은 사람·같은 날의 유효한 예약 (R2 겹침, R4 합계). ix_booking_member_date를 탄다. */
    List<PracticeBooking> findByMemberIdAndUsageDateAndCanceledAtIsNull(Long memberId, LocalDate usageDate);

    List<PracticeBooking> findByRoomIdAndUsageDateAndCanceledAtIsNull(Long roomId, LocalDate usageDate);

    /** 지도: 여러 방에서 [from, to)와 겹치는 유효한 예약. 배타 제약의 gist 인덱스(room_id, during)를 탄다. */
    @Query(value = """
            select * from practice_booking
            where room_id in (:roomIds) and canceled_at is null
              and during && tstzrange(:from, :to, '[)')""", nativeQuery = true)
    List<PracticeBooking> findOverlapping(Collection<Long> roomIds, Instant from, Instant to);

    /** 평면도에서 방을 뺄 때 확인하는 앞으로의 예약 (UC-20 3a). */
    @Query("""
            select b from PracticeBooking b
            where b.roomId in :roomIds and b.canceledAt is null and b.endsAt > :now
            order by b.startsAt""")
    List<PracticeBooking> findFutureByRooms(Collection<Long> roomIds, Instant now);

    /** 내 예약 (취소된 것도 사유와 함께 보인다). */
    @Query("""
            select b as booking, r.name as roomName, '' as memberName
            from PracticeBooking b join Room r on r.id = b.roomId
            where b.memberId = :memberId and b.usageDate between :from and :to
            order by b.startsAt""")
    List<BookingRow> findMine(Long memberId, LocalDate from, LocalDate to);

    /** 관리자 현황: 그날 기관 전체 예약과 예약한 사람 이름. */
    @Query("""
            select b as booking, r.name as roomName, u.name as memberName
            from PracticeBooking b
              join Room r on r.id = b.roomId
              join com.musicstudio.organization.domain.Membership m on m.id = b.memberId
              join com.musicstudio.account.domain.UserAccount u on u.id = m.userId
            where b.organizationId = :organizationId and b.usageDate = :date
            order by r.name, b.startsAt""")
    List<BookingRow> findByDate(Long organizationId, LocalDate date);

    interface BookingRow {
        PracticeBooking getBooking();

        String getRoomName();

        String getMemberName();
    }
}
