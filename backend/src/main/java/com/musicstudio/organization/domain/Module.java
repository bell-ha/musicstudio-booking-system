package com.musicstudio.organization.domain;

/** 기관별로 켜고 끄는 기능 모듈 (ADR 0006). */
public enum Module {
    PRACTICE_ROOM,
    ACADEMY,
    /** 청구·결제(수납). 학원 관리가 켜져 있어야 켤 수 있다 */
    BILLING
}
