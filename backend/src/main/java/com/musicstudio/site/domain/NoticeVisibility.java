package com.musicstudio.site.domain;

import com.musicstudio.organization.domain.MembershipRole;

/** 공지 공개 범위 (FR-SITE-03). */
public enum NoticeVisibility {
    /** 소유자·관리자·강사 */
    STAFF,
    /** 활성 멤버 전부 */
    MEMBERS,
    /** 멤버 전부 + 공개 소개 페이지 */
    PUBLIC;

    public boolean visibleTo(MembershipRole role) {
        return this != STAFF || role != MembershipRole.STUDENT;
    }
}
