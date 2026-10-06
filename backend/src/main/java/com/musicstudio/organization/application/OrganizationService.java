package com.musicstudio.organization.application;

import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.organization.domain.JoinCodes;
import com.musicstudio.organization.domain.Membership;
import com.musicstudio.organization.domain.MembershipRepository;
import com.musicstudio.organization.domain.Module;
import com.musicstudio.organization.domain.Organization;
import com.musicstudio.organization.domain.OrganizationRepository;
import com.musicstudio.organization.domain.OrganizationType;

@Service
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;

    OrganizationService(OrganizationRepository organizations, MembershipRepository memberships) {
        this.organizations = organizations;
        this.memberships = memberships;
    }

    /** UC-02. 만든 사람이 소유자가 된다. */
    @Transactional
    public Organization create(long userId, String name, OrganizationType type, String timezone, Set<Module> modules) {
        // IANA 이름만 받는다. "+09:00", "UTC+9" 같은 오프셋은 PostgreSQL의 AT TIME ZONE에서 부호가 반대로 해석될 수 있다.
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw ApiException.invalid("INVALID_TIMEZONE", "알 수 없는 시간대입니다");
        }
        Set<Module> enabled = (modules == null || modules.isEmpty()) ? type.defaultModules() : modules;
        requireConsistent(enabled);
        Organization organization = organizations.save(
                new Organization(name.trim(), type, timezone, enabled, JoinCodes.next()));
        memberships.save(Membership.owner(organization.getId(), userId));
        return organization;
    }

    /** UC-03 (소유자). 빼먹은 값은 그대로 둔다 */
    @Transactional
    public Organization update(long orgId, String name, Set<Module> modules) {
        if (name != null && name.isBlank()) {
            throw ApiException.invalid("INVALID_NAME", "기관 이름을 입력해 주세요");
        }
        Set<Module> wanted = modules == null ? null : EnumSet.copyOf(modules.isEmpty() ? EnumSet.noneOf(Module.class) : modules);
        if (wanted != null) {
            requireConsistent(wanted);
        }
        Organization organization = organizations.findById(orgId).orElseThrow();
        organization.update(name == null ? null : name.strip(), wanted);
        return organization;
    }

    /** 청구는 원생·수강에 붙어서 학원 관리 없이 켤 수 없다. 만들기·수정 모두 이 한 곳 (DB CHECK도 있다) */
    private static void requireConsistent(Set<Module> modules) {
        if (modules.contains(Module.BILLING) && !modules.contains(Module.ACADEMY)) {
            throw ApiException.policyViolation("BILLING_NEEDS_ACADEMY", "청구·결제는 학원 관리를 켜야 쓸 수 있어요");
        }
    }

    @Transactional(readOnly = true)
    public List<MembershipRepository.MyOrganization> myOrganizations(long userId) {
        return memberships.findMyOrganizations(userId);
    }
}
