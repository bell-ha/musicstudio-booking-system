package com.musicstudio.academy.domain;

/** 회차 상태 (FR-AC-14·15). */
public enum SessionStatus {
    /** 아직 출결을 안 남겼다. 지난 회차여도 그대로다(자동 출석 처리 없음) */
    SCHEDULED,
    ATTENDED,
    /** 무단 결석. 횟수권에서 차감한다 */
    ABSENT,
    /** 사전 결석. 차감하지 않고 시간을 비운다(보강이 들어갈 수 있다) */
    EXCUSED,
    /** 휴강. 차감하지 않는다. 사유 필수 */
    CANCELED;

    /** 횟수권에서 빠지는 회차 */
    public boolean deducts() {
        return this == ATTENDED || this == ABSENT;
    }

    /** 강사·원생의 시간을 차지하는 회차 (EXCLUDE 조건과 같다) */
    public boolean occupies() {
        return this == SCHEDULED || this == ATTENDED || this == ABSENT;
    }
}
