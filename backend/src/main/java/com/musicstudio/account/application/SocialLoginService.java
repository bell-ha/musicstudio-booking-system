package com.musicstudio.account.application;

import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.musicstudio.account.application.SocialVerifier.SocialProfile;
import com.musicstudio.account.domain.UserAccount;
import com.musicstudio.account.domain.UserAccountRepository;
import com.musicstudio.common.error.ApiException;
import com.musicstudio.common.security.AccessTokens;

@Service
public class SocialLoginService {

    private final SocialVerifier google;
    private final UserAccountRepository accounts;
    private final AccessTokens accessTokens;

    SocialLoginService(SocialVerifier google, UserAccountRepository accounts, AccessTokens accessTokens) {
        this.google = google;
        this.accounts = accounts;
        this.accessTokens = accessTokens;
    }

    /**
     * 계정 연결 정책 (ADR 0013):
     * 1. 구글 subject로 찾으면 로그인한다.
     * 2. 같은 이메일의 계정이 이미 있으면 자동 연결하지 않고 409.
     * 3. 아니면 새 계정을 만든다. 이메일은 구글이 검증한 경우에만 저장한다.
     *
     * 트랜잭션으로 묶지 않는다. 구글 호출(토큰 교환, JWKS)이 느려도 DB 커넥션을 쥐고 있지 않게 하려는 것이다.
     * 저장소 호출마다 따로 트랜잭션이고, 경합은 유니크 제약이 막는다.
     */
    public Result loginWithGoogle(String code, String redirectUri, String nonce) {
        SocialProfile profile = google.verify(code, redirectUri, nonce);

        Optional<UserAccount> linked = accounts.findByGoogleSubject(profile.subject());
        if (linked.isPresent()) {
            return new Result(accessTokens.issue(linked.get().getId()), false);
        }

        String email = profile.email() == null ? null : profile.email().trim().toLowerCase(Locale.ROOT);
        if (email != null && accounts.findByEmail(email).isPresent()) {
            throw emailInUse();
        }
        String name = (profile.name() == null || profile.name().isBlank()) ? "사용자" : profile.name();
        try {
            UserAccount created = accounts.saveAndFlush(
                    UserAccount.google(profile.subject(), profile.emailVerified() ? email : null, name));
            return new Result(accessTokens.issue(created.getId()), true);
        } catch (DataIntegrityViolationException e) {
            // 같은 사람이 처음 로그인을 동시에 두 번 했으면 먼저 만든 계정으로 로그인시킨다.
            // 아니면 그사이 같은 이메일로 가입한 것이다.
            return accounts.findByGoogleSubject(profile.subject())
                    .map(a -> new Result(accessTokens.issue(a.getId()), false))
                    .orElseThrow(SocialLoginService::emailInUse);
        }
    }

    private static ApiException emailInUse() {
        return ApiException.conflict("SOCIAL_EMAIL_IN_USE", "이미 이메일로 가입한 계정이 있습니다");
    }

    public record Result(AccessTokens.Issued token, boolean created) {
    }
}
