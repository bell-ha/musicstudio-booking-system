package com.musicstudio.practice.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * 연습실 예약. 시간 구간(during)은 DB의 생성 컬럼이라 매핑하지 않는다.
 * 같은 방·같은 사람의 겹침은 DB 배타 제약이 막는다 (ADR 0010).
 */
@Entity
public class PracticeBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long organizationId;

    private Long roomId;

    private Long memberId;

    private Instant startsAt;

    private Instant endsAt;

    /** 기관 현지 날짜. 하루 한도(R4) 합계와 잠금 키. */
    private LocalDate usageDate;

    private Instant canceledAt;

    private Long canceledByMembershipId;

    private String cancelReason;

    private Instant createdAt;

    protected PracticeBooking() {
    }

    public PracticeBooking(Long organizationId, Long roomId, Long memberId, Instant startsAt, Instant endsAt,
                           LocalDate usageDate) {
        this.organizationId = organizationId;
        this.roomId = roomId;
        this.memberId = memberId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.usageDate = usageDate;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
