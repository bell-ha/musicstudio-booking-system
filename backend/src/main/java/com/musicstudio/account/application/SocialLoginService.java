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

    /**
     * 로그인한 계정에 구글을 연결한다 (ADR 0013 보완). 이메일이 달라도 된다: 본인이 비밀번호로 로그인한 상태에서
     * 구글 동의까지 거쳤으므로 두 신원이 같은 사람이다. 이메일이 같다고 자동으로 합치지 않는 규칙은 그대로다.
     * 그 구글이 이미 다른 계정에 붙어 있으면 409.
     */
    public void linkGoogle(long userId, String code, String redirectUri, String nonce) {
        SocialProfile profile = google.verify(code, redirectUri, nonce);
        Optional<UserAccount> owner = accounts.findByGoogleSubject(profile.subject());
        if (owner.isPresent()) {
            if (owner.get().getId() == userId) {
                return; // 이미 연결됨 (멱등)
            }
            throw alreadyLinked();
        }
        UserAccount me = accounts.findById(userId).orElseThrow();
        if (!me.linkGoogle(profile.subject())) {
            throw ApiException.conflict("GOOGLE_ALREADY_SET", "이 계정에는 이미 다른 구글 계정이 연결돼 있습니다");
        }
        try {
            accounts.saveAndFlush(me);
        } catch (DataIntegrityViolationException e) {
            throw alreadyLinked(); // 같은 순간 그 구글로 다른 계정이 생겼다
        }
    }

    private static ApiException alreadyLinked() {
        return ApiException.conflict("GOOGLE_ALREADY_LINKED", "이 구글 계정은 이미 다른 계정에 연결돼 있습니다");
    }

    private static ApiException emailInUse() {
        return ApiException.conflict("SOCIAL_EMAIL_IN_USE", "이미 이메일로 가입한 계정이 있습니다");
    }

    public record Result(AccessTokens.Issued token, boolean created) {
    }
}
