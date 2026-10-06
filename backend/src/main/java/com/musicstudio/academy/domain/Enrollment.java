package com.musicstudio.academy.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

/**
 * 수강 (UC-42, UC-43).
 * 상태: ACTIVE → PAUSED → ACTIVE, ACTIVE·PAUSED → ENDED | REFUNDED. ENDED와 REFUNDED는 끝이다.
 * 기간권의 종료일은 일시정지한 날수만큼 늘어난다(재개할 때 계산).
 * 회차(LessonSession)는 고정 일정에서 만든다. 횟수권의 남은 회차는 출결에서 센다 (LessonScheduleService).
 * 종료일이 지나도 자동으로 끝내지 않는다. 관리자가 end를 누른다.
 */
@Entity
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long organizationId;

    private Long studentId;

    private Long productId;

    private Long teacherMembershipId;

    @Enumerated(EnumType.STRING)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    /** 등록 시점의 상품 가격. */
    private long price;

    private LocalDate startsOn;

    private LocalDate endsOn;

    private Short totalSessions;

    private LocalDate pausedAt;

    /** 관리자 둘이 같은 수강을 동시에 바꾸면 나중 것이 실패한다. */
    @Version
    private long version;

    private Instant createdAt;

    protected Enrollment() {
    }

    public Enrollment(Long organizationId, Long studentId, Product product, Long teacherMembershipId, LocalDate startsOn) {
        this.organizationId = organizationId;
        this.studentId = studentId;
        this.productId = product.getId();
        this.teacherMembershipId = teacherMembershipId;
        this.price = product.getPrice();
        this.startsOn = startsOn;
        if (product.getKind() == ProductKind.PERIOD) {
            this.endsOn = endOf(startsOn, product.getPeriodMonths());
        } else {
            this.totalSessions = product.getSessionCount();
        }
        this.createdAt = Instant.now();
    }

    /** 3/15 시작 3개월 → 6/14. */
    public static LocalDate endOf(LocalDate startsOn, int months) {
        return startsOn.plusMonths(months).minusDays(1);
    }

    public boolean isOpen() {
        return status == EnrollmentStatus.ACTIVE || status == EnrollmentStatus.PAUSED;
    }

    /** 허용되지 않으면 false. */
    public boolean pause(LocalDate today) {
        if (status != EnrollmentStatus.ACTIVE) {
            return false;
        }
        status = EnrollmentStatus.PAUSED;
        pausedAt = today;
        return true;
    }

    public boolean resume(LocalDate today) {
        if (status != EnrollmentStatus.PAUSED) {
            return false;
        }
        if (endsOn != null) {
            endsOn = endsOn.plusDays(ChronoUnit.DAYS.between(pausedAt, today));
        }
        status = EnrollmentStatus.ACTIVE;
        pausedAt = null;
        return true;
    }

    public boolean finish(EnrollmentStatus end) {
        if (!isOpen()) {
            return false;
        }
        status = end;
        pausedAt = null;
        return true;
    }

    /** 기간권은 months, 횟수권은 sessions를 늘린다. 맞지 않는 값이면 false. */
    public boolean extend(Integer months, Integer sessions) {
        if (!isOpen()) {
            return false;
        }
        if (endsOn != null && months != null && months > 0 && sessions == null) {
            endsOn = endsOn.plusMonths(months);
            return true;
        }
        if (totalSessions != null && sessions != null && sessions > 0 && months == null) {
            totalSessions = (short) (totalSessions + sessions);
            return true;
        }
        return false;
    }

    public boolean changeTeacher(Long teacherMembershipId) {
        if (!isOpen()) {
            return false;
        }
        this.teacherMembershipId = teacherMembershipId;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getTeacherMembershipId() {
        return teacherMembershipId;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public long getPrice() {
        return price;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public Short getTotalSessions() {
        return totalSessions;
    }

    public LocalDate getPausedAt() {
        return pausedAt;
    }

    public long getVersion() {
        return version;
    }
}
