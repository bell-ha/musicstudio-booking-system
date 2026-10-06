package com.musicstudio.organization.api;

import com.musicstudio.organization.domain.MembershipRole;

/** OrgAccessInterceptor가 검사를 통과시킨 요청자. 컨트롤러 메서드 인자로 받는다. */
public record CurrentMember(long organizationId, long membershipId, long userId, MembershipRole role) {

    public boolean isOwner() {
        return role == MembershipRole.OWNER;
    }
}
