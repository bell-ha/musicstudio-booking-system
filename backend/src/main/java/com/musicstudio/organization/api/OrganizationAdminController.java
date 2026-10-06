package com.musicstudio.organization.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.organization.application.JoinService;
import com.musicstudio.organization.application.MemberService;
import com.musicstudio.organization.domain.JoinField;
import com.musicstudio.organization.domain.MembershipRepository.MemberRow;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.organization.domain.MembershipStatus;
import com.musicstudio.organization.domain.Organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 기관 관리: 가입 코드, 초대, 멤버 (API 6~8, 11, 12). 경로의 orgId는 OrgAccessInterceptor가 검사한다. */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}")
class OrganizationAdminController {

    private final JoinService joinService;
    private final MemberService memberService;

    OrganizationAdminController(JoinService joinService, MemberService memberService) {
        this.joinService = joinService;
        this.memberService = memberService;
    }

    @GetMapping("/join-code")
    @OrgRole(MembershipRole.MANAGER)
    JoinCodeResponse joinCode(CurrentMember me) {
        return JoinCodeResponse.of(joinService.joinCode(me.organizationId()));
    }

    @PatchMapping("/join-code")
    @OrgRole(MembershipRole.MANAGER)
    JoinCodeResponse configureJoinCode(CurrentMember me, @RequestBody JoinCodeRequest request) {
        return JoinCodeResponse.of(joinService.configureJoinCode(me.organizationId(), request.enabled(), request.joinForm()));
    }

    @PostMapping("/join-code/regenerate")
    @OrgRole(MembershipRole.MANAGER)
    JoinCodeResponse regenerateJoinCode(CurrentMember me) {
        return JoinCodeResponse.of(joinService.regenerateJoinCode(me.organizationId()));
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @OrgRole(MembershipRole.MANAGER)
    InvitationResponse invite(CurrentMember me, @Valid @RequestBody InvitationRequest request) {
        int days = request.expiresInDays() == null ? 7 : request.expiresInDays();
        JoinService.CreatedInvitation created = joinService.invite(me.organizationId(), me.role(), request.role(), days);
        return new InvitationResponse(created.token(), created.expiresAt());
    }

    @GetMapping("/members")
    @OrgRole(MembershipRole.MANAGER)
    List<MemberResponse> members(CurrentMember me, @RequestParam(defaultValue = "ACTIVE") MembershipStatus status) {
        return memberService.members(me.organizationId(), status).stream().map(MemberResponse::of).toList();
    }

    @PatchMapping("/members/{membershipId}")
    @OrgRole(MembershipRole.MANAGER)
    MemberResponse changeMember(CurrentMember me, @PathVariable long membershipId,
                                @RequestBody MemberChangeRequest request) {
        return MemberResponse.of(memberService.change(me.organizationId(), me.role(), membershipId,
                request.role(), request.status()));
    }

    record JoinCodeRequest(Boolean enabled, List<JoinField> joinForm) {
    }

    record JoinCodeResponse(String joinCode, boolean enabled, List<JoinField> joinForm) {
        static JoinCodeResponse of(Organization o) {
            return new JoinCodeResponse(o.getJoinCode(), o.isJoinCodeEnabled(), o.getJoinForm());
        }
    }

    record InvitationRequest(@NotNull MembershipRole role, @Min(1) @Max(30) Integer expiresInDays) {
    }

    record InvitationResponse(String token, Instant expiresAt) {
    }

    record MemberChangeRequest(MembershipRole role, MembershipStatus status) {
    }

    record MemberResponse(Long membershipId, Long userId, String name, String email, MembershipRole role,
                          MembershipStatus status, Map<String, String> profile, Instant createdAt) {
        static MemberResponse of(MemberRow r) {
            var m = r.getMembership();
            return new MemberResponse(m.getId(), m.getUserId(), r.getName(), r.getEmail(), m.getRole(),
                    m.getStatus(), m.getProfile(), m.getCreatedAt());
        }
    }
}
