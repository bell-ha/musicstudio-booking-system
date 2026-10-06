package com.musicstudio.practice.domain;

import java.time.ZoneId;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * organization 테이블의 practice_policy 컬럼만 본다. Organization 엔티티는 이 컬럼을 매핑하지 않는다.
 *
 * 같은 테이블을 엔티티 둘이 보는 이유: 연습실 정책은 연습실 모듈의 개념이라 organization 모듈이
 * 그 타입을 알면 안 된다(ADR 0006, 연습실을 끈 기관에 연습실 코드가 끼지 않게). 스키마를 바꾸지 않고
 * 모듈 경계를 지키는 가장 작은 방법이다.
 *
 * 읽고 수정만 한다. 기관 생성은 organization 모듈의 일이라 공개 생성자가 없다(INSERT하면 NOT NULL 위반).
 */
@Entity
@Table(name = "organization")
public class PracticeSettings {

    @Id
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    private PracticePolicy practicePolicy;

    /** 기관 시간대. organization 모듈이 관리하므로 읽기만 한다. */
    @Column(insertable = false, updatable = false)
    private String timezone;

    protected PracticeSettings() {
    }

    /** 저장한 적이 없으면 기본 정책. */
    public PracticePolicy policy() {
        return practicePolicy == null ? PracticePolicy.DEFAULT : practicePolicy;
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public void changePolicy(PracticePolicy policy) {
        this.practicePolicy = policy.validate();
    }
}
