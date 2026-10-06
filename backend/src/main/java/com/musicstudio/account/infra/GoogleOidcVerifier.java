package com.musicstudio.account.infra;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.musicstudio.account.application.SocialVerifier;
import com.musicstudio.common.error.ApiException;

/**
 * 구글 인가 코드를 토큰 엔드포인트에서 교환하고, 받은 id_token의 서명·iss·aud·exp·nonce를 검증한다.
 * 별도 라이브러리 없이 Spring Security의 NimbusJwtDecoder를 쓴다.
 */
@Component
@EnableConfigurationProperties(GoogleProperties.class)
class GoogleOidcVerifier implements SocialVerifier {

    static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final Logger log = LoggerFactory.getLogger(GoogleOidcVerifier.class);

    private final GoogleProperties props;
    private final RestClient rest;
    private final NimbusJwtDecoder decoder;

    @Autowired
    GoogleOidcVerifier(GoogleProperties props) {
        this(props, RestClient.builder().requestFactory(timeouts()).build(),
                NimbusJwtDecoder.withJwkSetUri(JWKS_URI).restOperations(new RestTemplate(timeouts())).build());
    }

    /** 구글이 느려지면 요청 스레드가 무한정 묶이지 않도록 연결·읽기 모두 5초. */
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    GoogleOidcVerifier(GoogleProperties props, RestClient rest, NimbusJwtDecoder decoder) {
        this.props = props;
        this.rest = rest;
        this.decoder = decoder;
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<Object>("iss", iss -> ISSUERS.contains(String.valueOf(iss))),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(props.clientId()))));
    }

    @Override
    public SocialProfile verify(String code, String redirectUri, String nonce) {
        if (!props.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "social-login-disabled",
                    "SOCIAL_LOGIN_DISABLED", "구글 로그인을 쓸 수 없습니다");
        }
        if (props.allowedRedirectUris() == null || !props.allowedRedirectUris().contains(redirectUri)) {
            throw failed("허용되지 않은 redirectUri: " + redirectUri, null);
        }
        Jwt idToken = decode(exchange(code, redirectUri));
        if (nonce == null || !nonce.equals(idToken.getClaimAsString("nonce"))) {
            throw failed("nonce 불일치", null);
        }
        return new SocialProfile(idToken.getSubject(), idToken.getClaimAsString("email"),
                Boolean.TRUE.equals(idToken.getClaimAsBoolean("email_verified")), idToken.getClaimAsString("name"));
    }

    private String exchange(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("redirect_uri", redirectUri);
        try {
            Map<String, Object> response = rest.post().uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            Object idToken = response == null ? null : response.get("id_token");
            if (idToken == null) {
                throw failed("토큰 응답에 id_token 없음", null);
            }
            return idToken.toString();
        } catch (RestClientException e) {
            throw failed("토큰 교환 실패", e);
        }
    }

    private Jwt decode(String idToken) {
        try {
            return decoder.decode(idToken);
        } catch (JwtException e) {
            throw failed("id_token 검증 실패", e);
        }
    }

    /** 제공자 오류 문구는 로그에만 남긴다. */
    private static ApiException failed(String reason, Exception cause) {
        log.warn("구글 로그인 실패: {}", reason, cause);
        return new ApiException(HttpStatus.BAD_REQUEST, "social-auth-failed", "SOCIAL_AUTH_FAILED",
                "구글 로그인에 실패했습니다");
    }
}
