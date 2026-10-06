package com.musicstudio.organization.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.common.security.AccessTokens;
import com.musicstudio.organization.application.JoinService;
import com.musicstudio.organization.domain.JoinField;
import com.musicstudio.organization.domain.Membership;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.organization.domain.MembershipStatus;
import com.musicstudio.organization.domain.Organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 아직 멤버가 아닌 사람이 부르는 API (9, 10, 10-1). 토큰과 코드는 접근 로그에 남지 않도록 URL이 아니라 본문으로 받는다.
 */
@RestController
@RequestMapping("/api/v1")
class JoinController {

    private final JoinService joinService;

    JoinController(JoinService joinService) {
        this.joinService = joinService;
    }

    @PostMapping("/invitations/accept")
    AcceptResponse accept(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AcceptRequest request) {
        Membership m = joinService.accept(AccessTokens.userId(jwt), request.token());
        return new AcceptResponse(m.getOrganizationId(), m.getRole());
    }

    @PostMapping("/join-requests/lookup")
    LookupResponse lookup(@Valid @RequestBody LookupRequest request) {
        Organization org = joinService.lookup(request.code());
        return new LookupResponse(org.getName(), org.getJoinForm());
    }

    @PostMapping("/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    JoinRequestResponse apply(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JoinRequest request) {
        JoinService.Applied applied = joinService.apply(AccessTokens.userId(jwt), request.code(), request.answers());
        Membership m = applied.membership();
        return new JoinRequestResponse(m.getOrganizationId(), applied.organizationName(), m.getStatus());
    }

    record AcceptRequest(@NotBlank String token) {
    }

    record AcceptResponse(Long organizationId, MembershipRole role) {
    }

    record LookupRequest(@NotBlank String code) {
    }

    record LookupResponse(String organizationName, List<JoinField> joinForm) {
    }

    record JoinRequest(@NotBlank String code, Map<String, String> answers) {
    }

    record JoinRequestResponse(Long organizationId, String organizationName, MembershipStatus status) {
    }
}
