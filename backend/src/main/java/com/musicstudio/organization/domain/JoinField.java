package com.musicstudio.organization.domain;

/** 가입 코드로 신청할 때 받을 항목 하나 (예: 학번, 전공). organization.join_form에 저장한다. */
public record JoinField(String key, String label, boolean required) {
}
