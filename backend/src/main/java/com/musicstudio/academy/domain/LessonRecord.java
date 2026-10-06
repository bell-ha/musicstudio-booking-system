package com.musicstudio.academy.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** 레슨 기록 (UC-46). 쓴 사람만 고치거나 지운다. 학생에게는 공개한 기록만 보인다. */
@Entity
public class LessonRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long organizationId;

    private Long enrollmentId;

    private Long authorMembershipId;

    private LocalDate lessonDate;

    private String progress;

    private String homework;

    private String memo;

    private boolean visibleToStudent;

    private Instant createdAt;

    protected LessonRecord() {
    }

    public LessonRecord(Long organizationId, Long enrollmentId, Long authorMembershipId) {
        this.organizationId = organizationId;
        this.enrollmentId = enrollmentId;
        this.authorMembershipId = authorMembershipId;
        this.createdAt = Instant.now();
    }

    public void write(LocalDate lessonDate, String progress, String homework, String memo, boolean visibleToStudent) {
        this.lessonDate = lessonDate;
        this.progress = progress;
        this.homework = homework;
        this.memo = memo;
        this.visibleToStudent = visibleToStudent;
    }

    public Long getId() {
        return id;
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public Long getAuthorMembershipId() {
        return authorMembershipId;
    }

    public LocalDate getLessonDate() {
        return lessonDate;
    }

    public String getProgress() {
        return progress;
    }

    public String getHomework() {
        return homework;
    }

    public String getMemo() {
        return memo;
    }

    public boolean isVisibleToStudent() {
        return visibleToStudent;
    }
}
