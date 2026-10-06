package com.musicstudio.account.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.account.application.SocialLoginService;
import com.musicstudio.account.domain.UserAccount;
import com.musicstudio.account.domain.UserAccountRepository;
import com.musicstudio.common.security.AccessTokens;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** 내 계정: 정보와 구글 연결. 로그인해야 부른다 */
@RestController
@RequestMapping("/api/v1/me")
class AccountController {

    private final UserAccountRepository accounts;
    private final SocialLoginService social;

    AccountController(UserAccountRepository accounts, SocialLoginService social) {
        this.accounts = accounts;
        this.social = social;
    }

    @GetMapping
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UserAccount a = accounts.findById(AccessTokens.userId(jwt)).orElseThrow();
        return new MeResponse(a.getName(), a.getEmail(), a.isGoogleLinked(), a.getPasswordHash() != null);
    }

    @PostMapping("/social/google")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void linkGoogle(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LinkRequest req) {
        social.linkGoogle(AccessTokens.userId(jwt), req.code(), req.redirectUri(), req.nonce());
    }

    record LinkRequest(@NotBlank String code, @NotBlank String redirectUri, @NotBlank String nonce) {
    }

    record MeResponse(String name, String email, boolean googleLinked, boolean hasPassword) {
    }
}
