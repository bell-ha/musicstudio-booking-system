package com.musicstudio.account.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.musicstudio.account.application.SocialVerifier.SocialProfile;
import com.musicstudio.common.error.ApiException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/** 토큰 교환과 id_token 검증. 구글 대신 테스트용 RSA 키와 가짜 토큰 엔드포인트를 쓴다. */
class GoogleOidcVerifierTest {

    static final String CLIENT_ID = "test-client";
    static final String REDIRECT = "http://localhost:5173/auth/callback/google";

    KeyPair keys;
    MockRestServiceServer server;
    GoogleOidcVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        verifier = verifier(new GoogleProperties(CLIENT_ID, "secret", List.of(REDIRECT)));
    }

    @Test
    void 올바른_id_token이면_사용자_정보를_돌려준다() throws Exception {
        tokenEndpointReturns(idToken(claims().build()));

        SocialProfile profile = verifier.verify("code", REDIRECT, "nonce-1");

        assertThat(profile.subject()).isEqualTo("google-123");
        assertThat(profile.email()).isEqualTo("a@gmail.com");
        assertThat(profile.emailVerified()).isTrue();
        server.verify();
    }

    @Test
    void 다른_앱에_발급된_토큰은_거절한다() throws Exception {
        tokenEndpointReturns(idToken(claims().audience("other-client").build()));
        assertFails(() -> verifier.verify("code", REDIRECT, "nonce-1"));
    }

    @Test
    void 다른_발급자의_토큰은_거절한다() throws Exception {
        tokenEndpointReturns(idToken(claims().issuer("https://evil.example").build()));
        assertFails(() -> verifier.verify("code", REDIRECT, "nonce-1"));
    }

    @Test
    void nonce가_다르면_거절한다() throws Exception {
        tokenEndpointReturns(idToken(claims().build()));
        assertFails(() -> verifier.verify("code", REDIRECT, "other-nonce"));
    }

    @Test
    void 만료된_토큰은_거절한다() throws Exception {
        tokenEndpointReturns(idToken(claims().expirationTime(Date.from(Instant.now().minusSeconds(3600))).build()));
        assertFails(() -> verifier.verify("code", REDIRECT, "nonce-1"));
    }

    @Test
    void 다른_키로_서명한_토큰은_거절한다() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair(); // 검증기는 처음 키를 안다
        tokenEndpointReturns(idToken(claims().build()));
        assertFails(() -> verifier.verify("code", REDIRECT, "nonce-1"));
    }

    @Test
    void 허용되지_않은_redirectUri는_토큰_교환_전에_거절한다() {
        assertFails(() -> verifier.verify("code", "https://evil.example/callback", "nonce-1"));
    }

    @Test
    void 설정이_없으면_503() {
        GoogleOidcVerifier disabled = verifier(new GoogleProperties("", "", List.of(REDIRECT)));
        assertThatThrownBy(() -> disabled.verify("code", REDIRECT, "nonce-1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SOCIAL_LOGIN_DISABLED"));
    }

    private GoogleOidcVerifier verifier(GoogleProperties props) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new GoogleOidcVerifier(props, builder.build(),
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("google-123")
                .claim("email", "a@gmail.com")
                .claim("email_verified", true)
                .claim("nonce", "nonce-1")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(600)));
    }

    private String idToken(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
        return jwt.serialize();
    }

    private void tokenEndpointReturns(String idToken) {
        server.expect(requestTo(GoogleOidcVerifier.TOKEN_URI))
                .andRespond(withSuccess("{\"id_token\":\"%s\"}".formatted(idToken), MediaType.APPLICATION_JSON));
    }

    private static void assertFails(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SOCIAL_AUTH_FAILED"));
    }
}
