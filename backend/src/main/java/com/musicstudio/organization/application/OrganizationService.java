package com.musicstudio.organization.application;

import java.security.SecureRandom;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.organization.domain.Membership;
import com.musicstudio.organization.domain.MembershipRepository;
import com.musicstudio.organization.domain.Module;
import com.musicstudio.organization.domain.Organization;
import com.musicstudio.organization.domain.OrganizationRepository;
import com.musicstudio.organization.domain.OrganizationType;

@Service
public class OrganizationService {

    // 헷갈리는 글자(0, O, 1, I)를 뺐다. 8자리면 약 1조 가지라 충돌은 유니크 제약에 맡긴다.
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

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
        Organization organization = organizations.save(
                new Organization(name.trim(), type, timezone, enabled, newJoinCode()));
        memberships.save(Membership.owner(organization.getId(), userId));
        return organization;
    }

    @Transactional(readOnly = true)
    public List<MembershipRepository.MyOrganization> myOrganizations(long userId) {
        return memberships.findMyOrganizations(userId);
    }

    private static String newJoinCode() {
        char[] code = new char[8];
        for (int i = 0; i < code.length; i++) {
            code[i] = CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)];
        }
        return new String(code);
    }
}
