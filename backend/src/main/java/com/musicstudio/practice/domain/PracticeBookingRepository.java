package com.musicstudio.practice.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PracticeBookingRepository extends JpaRepository<PracticeBooking, Long> {

    /** 평면도에서 방을 뺄 때 확인하는 앞으로의 예약 (UC-20 3a). */
    @Query("""
            select b from PracticeBooking b
            where b.roomId in :roomIds and b.canceledAt is null and b.endsAt > :now
            order by b.startsAt""")
    List<PracticeBooking> findFutureByRooms(Collection<Long> roomIds, Instant now);
}
