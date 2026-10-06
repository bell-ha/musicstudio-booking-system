package com.musicstudio.organization.application;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.organization.domain.Membership;
import com.musicstudio.organization.domain.MembershipRepository;
import com.musicstudio.organization.domain.MembershipRepository.MemberRow;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.organization.domain.MembershipStatus;
import com.musicstudio.organization.domain.OrganizationRepository;

/** 가입 신청 승인·거절, 역할 변경, 비활성화·재활성화 (UC-07, UC-08). */
@Service
public class MemberService {

    private static final Set<MembershipRole> PRIVILEGED = EnumSet.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;

    MemberService(MembershipRepository memberships, OrganizationRepository organizations) {
        this.memberships = memberships;
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public List<MemberRow> members(long orgId, MembershipStatus status) {
        return memberships.findMembers(orgId, status);
    }

    /**
     * role과 status 중 하나만 바꾼다.
     * 대상이 소유자·관리자이거나 결과가 소유자·관리자인 변경은 소유자만 한다.
     */
    @Transactional
    public MemberRow change(long orgId, MembershipRole actorRole, long membershipId,
                           MembershipRole role, MembershipStatus status) {
        if ((role == null) == (status == null)) {
            throw ApiException.invalid("ROLE_OR_STATUS", "역할과 상태 중 하나만 바꿀 수 있습니다");
        }
        Membership target = memberships.findByIdAndOrganizationId(membershipId, orgId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "NOT_FOUND", "멤버를 찾을 수 없습니다"));

        boolean privileged = PRIVILEGED.contains(target.getRole()) || (role != null && PRIVILEGED.contains(role));
        if (privileged && actorRole != MembershipRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "FORBIDDEN", "소유자만 할 수 있습니다");
        }

        boolean removesOwner = target.isActiveOwner()
                && ((role != null && role != MembershipRole.OWNER) || status == MembershipStatus.INACTIVE);
        if (removesOwner) {
            // 소유자 둘이 서로를 동시에 강등하면 둘 다 "다른 소유자가 남는다"를 보고 통과한다(write skew).
            // 기관 행을 잠가서 같은 기관의 소유자 변경을 한 줄로 세운다.
            organizations.lockById(orgId);
            if (memberships.countOtherActiveOwners(orgId, membershipId) == 0) {
                throw ApiException.conflict("LAST_OWNER", "마지막 소유자는 바꿀 수 없습니다");
            }
        }

        boolean changed = role != null ? target.changeRole(role) : target.changeStatus(status);
        if (!changed) {
            throw ApiException.conflict("INVALID_TRANSITION", "지금 상태에서는 바꿀 수 없습니다");
        }
        return memberships.findMember(membershipId);
    }
}
