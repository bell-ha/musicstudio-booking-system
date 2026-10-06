package com.musicstudio.account.application;

/**
 * 제공자의 인가 코드를 확인된 사용자 정보로 바꾼다 (ADR 0013).
 * 토큰 교환과 id_token 검증을 여기 뒤에 숨겨서, 계정 연결 정책은 가짜 구현으로 테스트한다.
 */
public interface SocialVerifier {

    SocialProfile verify(String code, String redirectUri, String nonce);

    record SocialProfile(String subject, String email, boolean emailVerified, String name) {
    }
}
