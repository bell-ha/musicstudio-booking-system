package com.musicstudio.common.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * 접근 토큰(JWT, HS256)만 쓰는 무상태 API. 세션과 CSRF 토큰을 쓰지 않는다.
 * 토큰에는 사용자 ID만 담는다. 기관과 역할은 요청마다 멤버십에서 읽는다 (02-API.md 1절).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/actuator/health/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(ignoringAuthPaths())
                        .jwt(jwt -> {})
                        .authenticationEntryPoint(unauthenticated()))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthenticated()))
                .build();
    }

    /**
     * 가입·로그인 요청에 남은(만료된) 토큰이 붙어 와도 401을 내지 않도록 그 경로에서는 토큰을 읽지 않는다.
     */
    private static BearerTokenResolver ignoringAuthPaths() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        return request -> request.getRequestURI().startsWith("/api/v1/auth/") ? null : resolver.resolve(request);
    }

    private AuthenticationEntryPoint unauthenticated() {
        return (request, response, e) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("""
                    {"type":"https://api.example.com/problems/unauthenticated","title":"로그인이 필요합니다","status":401}""");
        };
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties props) {
        return NimbusJwtDecoder.withSecretKey(key(props)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static SecretKey key(JwtProperties props) {
        return new SecretKeySpec(props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
