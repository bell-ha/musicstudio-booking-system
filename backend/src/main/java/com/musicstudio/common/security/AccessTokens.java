package com.musicstudio.common.security;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class AccessTokens {

    private final JwtEncoder encoder;
    private final JwtProperties props;

    AccessTokens(JwtEncoder encoder, JwtProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public Issued issue(long userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Issued(token, expiresAt);
    }

    /** 인증된 요청의 사용자 ID. */
    public static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record Issued(String accessToken, Instant expiresAt) {
    }
}
