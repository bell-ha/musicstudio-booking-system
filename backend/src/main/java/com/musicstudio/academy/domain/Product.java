package com.musicstudio.academy.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** 수업 상품. 가격을 바꿔도 이미 등록된 수강의 금액은 바뀌지 않는다(수강에 복사해 둔다). */
@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long organizationId;

    private Long subjectId;

    private String name;

    @Enumerated(EnumType.STRING)
    private ProductKind kind;

    private Short sessionCount;

    private Short periodMonths;

    private short lessonMinutes;

    private long price;

    protected Product() {
    }

    public Product(Long organizationId, Long subjectId, String name, ProductKind kind, Integer sessionCount,
                   Integer periodMonths, int lessonMinutes, long price) {
        this.organizationId = organizationId;
        this.subjectId = subjectId;
        this.name = name;
        this.kind = kind;
        this.sessionCount = sessionCount == null ? null : sessionCount.shortValue();
        this.periodMonths = periodMonths == null ? null : periodMonths.shortValue();
        this.lessonMinutes = (short) lessonMinutes;
        this.price = price;
    }

    public void update(String name, Long price) {
        if (name != null) {
            this.name = name;
        }
        if (price != null) {
            this.price = price;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getName() {
        return name;
    }

    public ProductKind getKind() {
        return kind;
    }

    public Short getSessionCount() {
        return sessionCount;
    }

    public Short getPeriodMonths() {
        return periodMonths;
    }

    public int getLessonMinutes() {
        return lessonMinutes;
    }

    public long getPrice() {
        return price;
    }
}
