package com.musicstudio.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration ttl) {

    public JwtProperties {
        // v1 교훈: 서명 키가 없거나 짧으면 서버를 띄우지 않는다.
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret(JWT_SECRET)은 32바이트 이상이어야 합니다");
        }
    }
}
