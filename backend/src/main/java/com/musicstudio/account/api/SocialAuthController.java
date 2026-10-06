package com.musicstudio.account.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.account.application.SocialLoginService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/auth/social")
class SocialAuthController {

    private final SocialLoginService socialLoginService;

    SocialAuthController(SocialLoginService socialLoginService) {
        this.socialLoginService = socialLoginService;
    }

    @PostMapping("/google")
    SocialLoginResponse google(@Valid @RequestBody SocialLoginRequest request) {
        SocialLoginService.Result result =
                socialLoginService.loginWithGoogle(request.code(), request.redirectUri(), request.nonce());
        return new SocialLoginResponse(result.token().accessToken(), result.token().expiresAt(), result.created());
    }

    record SocialLoginRequest(@NotBlank String code, @NotBlank String redirectUri, @NotBlank String nonce) {
    }

    record SocialLoginResponse(String accessToken, Instant expiresAt, boolean created) {
    }
}
