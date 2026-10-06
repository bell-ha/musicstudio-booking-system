package com.musicstudio.organization.api;

import java.util.Arrays;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.common.security.AccessTokens;
import com.musicstudio.organization.domain.Membership;
import com.musicstudio.organization.domain.MembershipRepository;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.organization.domain.MembershipStatus;
import com.musicstudio.organization.domain.Module;
import com.musicstudio.organization.domain.OrganizationRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 기관 범위 요청 검사 (02-API.md 1절 검사 순서). 401은 Security가 먼저 처리한다.
 * 2. 활성 멤버인가 → 403 not-a-member
 * 3. 경로의 모듈이 켜져 있나 → 403 module-disabled
 * 4. 역할 (@OrgRole) → 403 forbidden
 * 5. 리소스가 그 기관 것인가 → 서비스가 기관 조건으로 조회해서 404
 * 멤버십을 캐시하지 않아서 비활성화가 다음 요청부터 바로 적용된다.
 */
@Component
class OrgAccessInterceptor implements HandlerInterceptor {

    static final String ATTRIBUTE = CurrentMember.class.getName();

    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;

    OrgAccessInterceptor(MembershipRepository memberships, OrganizationRepository organizations) {
        this.memberships = memberships;
        this.organizations = organizations;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        OrgRole required = method.getMethodAnnotation(OrgRole.class);
        if (required == null) {
            throw forbidden("이 기관 API에 권한 규칙이 없습니다"); // 기본 거부: 빠뜨리면 막힌다
        }
        long orgId = organizationId(request);
        long userId = AccessTokens.userId(((JwtAuthenticationToken) SecurityContextHolder.getContext()
                .getAuthentication()).getToken());

        Membership membership = memberships.findByOrganizationIdAndUserId(orgId, userId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "not-a-member", "NOT_A_MEMBER",
                        "이 기관의 멤버가 아닙니다"));

        Module module = moduleOf(request.getRequestURI(), orgId);
        if (module != null && !organizations.findById(orgId).orElseThrow().getModules().contains(module)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "module-disabled", "MODULE_DISABLED",
                    "이 기관에서 켜지 않은 기능입니다");
        }

        if (!allowed(membership.getRole(), required.value())) {
            throw forbidden("권한이 없습니다");
        }
        request.setAttribute(ATTRIBUTE,
                new CurrentMember(orgId, membership.getId(), userId, membership.getRole()));
        return true;
    }

    static boolean allowed(MembershipRole role, MembershipRole[] required) {
        if (required.length == 0) {
            return true;
        }
        return Arrays.stream(required)
                .anyMatch(r -> r == role || (r == MembershipRole.MANAGER && role == MembershipRole.OWNER));
    }

    private static long organizationId(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, String> vars = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        try {
            return Long.parseLong(vars.get("orgId"));
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not-found", "NOT_FOUND", "찾을 수 없습니다");
        }
    }

    private static Module moduleOf(String uri, long orgId) {
        String rest = uri.substring(uri.indexOf("/organizations/" + orgId) + ("/organizations/" + orgId).length());
        if (rest.startsWith("/practice")) {
            return Module.PRACTICE_ROOM;
        }
        if (rest.startsWith("/academy")) {
            return Module.ACADEMY;
        }
        return null;
    }

    private static ApiException forbidden(String title) {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", "FORBIDDEN", title);
    }
}
