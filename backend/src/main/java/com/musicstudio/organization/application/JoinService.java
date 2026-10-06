package com.musicstudio.organization.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.common.error.ApiException;
import com.musicstudio.organization.domain.Invitation;
import com.musicstudio.organization.domain.InvitationRepository;
import com.musicstudio.organization.domain.JoinCodes;
import com.musicstudio.organization.domain.JoinField;
import com.musicstudio.organization.domain.Membership;
import com.musicstudio.organization.domain.MembershipRepository;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.organization.domain.Organization;
import com.musicstudio.organization.domain.OrganizationRepository;

/** 기관에 들어오는 두 길: 초대 링크(UC-04, 05)와 가입 코드 + 승인(UC-06). */
@Service
public class JoinService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ANSWER_LENGTH = 100;

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;

    JoinService(OrganizationRepository organizations, MembershipRepository memberships,
                InvitationRepository invitations) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.invitations = invitations;
    }

    // ---------- 가입 코드 설정 (관리자) ----------

    @Transactional(readOnly = true)
    public Organization joinCode(long orgId) {
        return organizations.findById(orgId).orElseThrow();
    }

    @Transactional
    public Organization configureJoinCode(long orgId, Boolean enabled, List<JoinField> joinForm) {
        if (joinForm != null) {
            var keys = new HashSet<String>();
            for (JoinField f : joinForm) {
                if (f.key() == null || f.key().isBlank() || f.label() == null || f.label().isBlank()
                        || !keys.add(f.key())) {
                    throw ApiException.invalid("INVALID_JOIN_FORM", "신청 항목의 키와 이름을 확인해 주세요");
                }
            }
        }
        Organization org = organizations.findById(orgId).orElseThrow();
        org.configureJoinCode(enabled, joinForm);
        return org;
    }

    @Transactional
    public Organization regenerateJoinCode(long orgId) {
        Organization org = organizations.findById(orgId).orElseThrow();
        org.changeJoinCode(JoinCodes.next());
        try {
            return organizations.saveAndFlush(org);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("JOIN_CODE_COLLISION", "잠시 후 다시 시도해 주세요");
        }
    }

    // ---------- 초대 링크 ----------

    /** 관리자는 강사·학생만, 소유자는 관리자까지 초대한다. 소유자 초대는 없다. */
    @Transactional
    public CreatedInvitation invite(long orgId, MembershipRole actorRole, MembershipRole role, int expiresInDays) {
        if (role == MembershipRole.OWNER) {
            throw ApiException.invalid("INVALID_ROLE", "소유자는 초대할 수 없습니다");
        }
        if (role == MembershipRole.MANAGER && actorRole != MembershipRole.OWNER) {
            throw forbidden();
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(Duration.ofDays(expiresInDays));
        invitations.save(new Invitation(orgId, role, sha256(token), expiresAt));
        return new CreatedInvitation(token, expiresAt);
    }

    /**
     * 초대를 쓰는 것과 멤버십을 만드는 것이 한 트랜잭션이다. 멤버십 단계에서 409가 나면 초대는 소비되지 않는다.
     */
    @Transactional
    public Membership accept(long userId, String token) {
        String hash = sha256(token);
        if (invitations.markUsed(hash, Instant.now()) == 0) {
            // 없음, 만료, 이미 사용됨을 구분하지 않는다.
            throw new ApiException(HttpStatus.NOT_FOUND, "not-found", "INVITATION_INVALID", "쓸 수 없는 초대 링크입니다");
        }
        Invitation invitation = invitations.findByTokenHash(hash).orElseThrow();
        Optional<Membership> existing = memberships.findByOrganizationIdAndUserId(invitation.getOrganizationId(), userId);
        if (existing.isPresent()) {
            Membership m = existing.get();
            switch (m.getStatus()) {
                case ACTIVE -> throw alreadyMember();
                case INACTIVE -> throw memberInactive(); // 예전 링크로 비활성화가 풀리지 않게 한다
                case PENDING, REJECTED -> m.acceptInvitation(invitation.getRole());
            }
            return m;
        }
        try {
            return memberships.saveAndFlush(Membership.invited(invitation.getOrganizationId(), userId, invitation.getRole()));
        } catch (DataIntegrityViolationException e) {
            throw alreadyMember(); // 같은 순간 가입 신청이 먼저 들어갔다
        }
    }

    // ---------- 가입 코드로 신청 ----------

    @Transactional(readOnly = true)
    public Organization lookup(String code) {
        return organizations.findByJoinCode(JoinCodes.normalize(code))
                .filter(Organization::isJoinCodeEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "JOIN_CODE_INVALID",
                        "가입 코드를 확인해 주세요"));
    }

    @Transactional
    public Applied apply(long userId, String code, Map<String, String> answers) {
        return applyTo(lookup(code), userId, answers);
    }

    /**
     * 가입 코드 없이 기관 ID로 신청한다. 공개 소개 페이지(UC-14)가 주소로 기관을 찾은 뒤 부른다.
     * 그 입구가 열려 있는지(공개, 가입 받기, 가입 코드 사용 중)는 부르는 쪽이 확인한다.
     */
    @Transactional
    public Applied applyToOrganization(long userId, long orgId, Map<String, String> answers) {
        return applyTo(organizations.findById(orgId).orElseThrow(), userId, answers);
    }

    private Applied applyTo(Organization org, long userId, Map<String, String> answers) {
        Map<String, String> profile = validateAnswers(org.getJoinForm(), answers);
        Optional<Membership> existing = memberships.findByOrganizationIdAndUserId(org.getId(), userId);
        if (existing.isPresent()) {
            Membership m = existing.get();
            switch (m.getStatus()) {
                case PENDING -> throw ApiException.conflict("ALREADY_PENDING", "이미 신청해서 승인을 기다리고 있습니다");
                case ACTIVE -> throw alreadyMember();
                case INACTIVE -> throw memberInactive();
                case REJECTED -> m.reapply(profile);
            }
            return new Applied(m, org.getName());
        }
        try {
            return new Applied(memberships.saveAndFlush(Membership.applied(org.getId(), userId, profile)), org.getName());
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("ALREADY_PENDING", "이미 신청해서 승인을 기다리고 있습니다");
        }
    }

    /** 정의된 항목만 남긴다. 필수 항목이 비었거나 값이 너무 길면 400. */
    private static Map<String, String> validateAnswers(List<JoinField> form, Map<String, String> answers) {
        Map<String, String> given = answers == null ? Map.of() : answers;
        Map<String, String> profile = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        for (JoinField field : form) {
            String value = given.get(field.key());
            if (value == null || value.isBlank()) {
                if (field.required()) {
                    errors.put("answers." + field.key(), field.label() + "을(를) 입력해 주세요");
                }
            } else if (value.length() > MAX_ANSWER_LENGTH) {
                errors.put("answers." + field.key(), MAX_ANSWER_LENGTH + "자 이하로 입력해 주세요");
            } else {
                profile.put(field.key(), value.trim());
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.invalidFields("INVALID_ANSWERS", errors);
        }
        return profile;
    }

    private static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException alreadyMember() {
        return ApiException.conflict("ALREADY_MEMBER", "이미 이 기관의 멤버입니다");
    }

    private static ApiException memberInactive() {
        return ApiException.conflict("MEMBER_INACTIVE", "비활성화된 멤버입니다. 기관 관리자에게 문의해 주세요");
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", "FORBIDDEN", "권한이 없습니다");
    }

    public record CreatedInvitation(String token, Instant expiresAt) {
    }

    public record Applied(Membership membership, String organizationName) {
    }
}
