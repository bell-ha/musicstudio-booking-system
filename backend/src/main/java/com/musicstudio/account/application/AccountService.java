package com.musicstudio.account.application;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.account.domain.UserAccount;
import com.musicstudio.account.domain.UserAccountRepository;
import com.musicstudio.common.error.ApiException;
import com.musicstudio.common.security.AccessTokens;

@Service
public class AccountService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokens accessTokens;

    AccountService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, AccessTokens accessTokens) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
    }

    @Transactional
    public UserAccount signup(String email, String password, String name) {
        // bcrypt는 72바이트를 넘으면 예외를 던진다. 한글은 한 글자가 3바이트라 글자 수 검사로는 부족하다.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.invalid("PASSWORD_TOO_LONG", "비밀번호가 너무 깁니다");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        try {
            // 이메일 중복은 유니크 인덱스가 막는다. 동시에 같은 이메일로 가입해도 한 건만 들어간다.
            return accounts.saveAndFlush(new UserAccount(normalized, passwordEncoder.encode(password), name.trim()));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "이미 가입된 이메일입니다");
        }
    }

    @Transactional(readOnly = true)
    public AccessTokens.Issued login(String email, String password) {
        // 이메일이 없는 경우와 비밀번호가 틀린 경우를 같은 응답으로 단순화한다.
        return accounts.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(a -> a.getPasswordHash() != null && passwordEncoder.matches(password, a.getPasswordHash()))
                .map(a -> accessTokens.issue(a.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid-credentials",
                        "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 맞지 않습니다"));
    }
}
