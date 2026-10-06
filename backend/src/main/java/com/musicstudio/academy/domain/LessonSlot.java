package com.musicstudio.academy.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** 수강의 고정 주간 일정 하나(요일 + 현지 시작 시각). 회차를 다시 만들 때의 원본이다 (UC-49). */
@Entity
public class LessonSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long enrollmentId;

    private short dayOfWeek;

    private LocalTime startTime;

    protected LessonSlot() {
    }

    public LessonSlot(Long enrollmentId, DayOfWeek day, LocalTime startTime) {
        this.enrollmentId = enrollmentId;
        this.dayOfWeek = (short) day.getValue();
        this.startTime = startTime;
    }

    public DayOfWeek getDayOfWeek() {
        return DayOfWeek.of(dayOfWeek);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }
}
