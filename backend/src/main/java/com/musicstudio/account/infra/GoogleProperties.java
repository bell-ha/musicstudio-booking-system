package com.musicstudio.account.infra;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 비어 있으면 구글 로그인을 끈 것으로 본다 (503 SOCIAL_LOGIN_DISABLED). */
@ConfigurationProperties("app.social.google")
public record GoogleProperties(String clientId, String clientSecret, List<String> allowedRedirectUris) {

    boolean enabled() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
