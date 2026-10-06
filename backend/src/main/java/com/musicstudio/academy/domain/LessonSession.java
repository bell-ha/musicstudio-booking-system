package com.musicstudio.academy.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * 레슨 회차. 강사·원생 겹침은 DB의 EXCLUDE가 마지막으로 막는다 (V10).
 * during(tstzrange)은 DB가 starts_at·ends_at에서 만드는 생성 열이라 매핑하지 않는다.
 */
@Entity
public class LessonSession {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long organizationId;

    private Long enrollmentId;

    private Long studentId;

    private Long teacherMembershipId;

    @Enumerated(EnumType.STRING)
    private SessionKind kind;

    private Instant startsAt;

    private Instant endsAt;

    private LocalDate localDate;

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.SCHEDULED;

    private String note;

    private Long markedByMembershipId;

    private Instant markedAt;

    protected LessonSession() {
    }

    public LessonSession(Enrollment e, SessionKind kind, Instant startsAt, Instant endsAt, LocalDate localDate) {
        this.organizationId = e.getOrganizationId();
        this.enrollmentId = e.getId();
        this.studentId = e.getStudentId();
        this.teacherMembershipId = e.getTeacherMembershipId();
        this.kind = kind;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.localDate = localDate;
    }

    public void mark(SessionStatus status, String note, long byMembershipId, Instant now) {
        this.status = status;
        this.note = note;
        this.markedByMembershipId = byMembershipId;
        this.markedAt = now;
    }

    public void assignTeacher(Long teacherMembershipId) {
        this.teacherMembershipId = teacherMembershipId;
    }

    public boolean overlaps(Instant start, Instant end) {
        return startsAt.isBefore(end) && start.isBefore(endsAt);
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getTeacherMembershipId() {
        return teacherMembershipId;
    }

    public SessionKind getKind() {
        return kind;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }
}
