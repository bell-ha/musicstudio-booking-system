package com.musicstudio.academy.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LessonSlotRepository extends JpaRepository<LessonSlot, Long> {

    List<LessonSlot> findByEnrollmentIdOrderByDayOfWeekAscStartTimeAsc(Long enrollmentId);

    List<LessonSlot> findByEnrollmentIdIn(Collection<Long> enrollmentIds);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from LessonSlot s where s.enrollmentId = :enrollmentId")
    void deleteByEnrollment(Long enrollmentId);
}
