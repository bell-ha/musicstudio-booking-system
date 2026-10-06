package com.musicstudio.account;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.musicstudio.account.application.SocialVerifier;
import com.musicstudio.account.application.SocialVerifier.SocialProfile;
import com.musicstudio.support.IntegrationTest;

/** 구글 로그인의 계정 연결 정책 (ADR 0013). 제공자 호출은 가짜로 바꾼다. */
@IntegrationTest
@AutoConfigureMockMvc
class SocialLoginApiTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SocialVerifier google;

    @Test
    void 처음이면_계정을_만들고_다음에는_같은_계정으로_로그인한다() throws Exception {
        String subject = UUID.randomUUID().toString();
        googleReturns(new SocialProfile(subject, null, false, "구글 사용자"));

        String token = JsonPath.read(googleLogin().andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/me/organizations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        googleLogin().andExpect(status().isOk()).andExpect(jsonPath("$.created").value(false));
    }

    @Test
    void 같은_이메일의_계정이_있으면_자동으로_연결하지_않는다() throws Exception {
        String email = uniqueEmail();
        signup(email);
        googleReturns(new SocialProfile(UUID.randomUUID().toString(), email, true, "구글 사용자"));

        googleLogin().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOCIAL_EMAIL_IN_USE"));
    }

    @Test
    void 검증되지_않은_이메일은_저장하지_않아서_그_이메일로_나중에_가입할_수_있다() throws Exception {
        String email = uniqueEmail();
        googleReturns(new SocialProfile(UUID.randomUUID().toString(), email, false, "구글 사용자"));
        googleLogin().andExpect(status().isOk()).andExpect(jsonPath("$.created").value(true));

        signup(email);
    }

    @Test
    void 비밀번호가_없는_소셜_계정에_이메일_로그인을_시도하면_401() throws Exception {
        String email = uniqueEmail();
        googleReturns(new SocialProfile(UUID.randomUUID().toString(), email, true, "구글 사용자"));
        googleLogin().andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"anything123\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인한_계정에_이메일이_다른_구글을_연결하면_그_구글로_같은_계정에_들어온다() throws Exception {
        String email = uniqueEmail();
        signup(email);
        String password = JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        String subject = "google-" + UUID.randomUUID();
        googleReturns(new SocialProfile(subject, "someone-else@gmail.com", true, "구글 이름"));

        mvc.perform(post("/api/v1/me/social/google").header("Authorization", "Bearer " + password)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\",\"redirectUri\":\"http://localhost:3000/auth/callback/google\",\"nonce\":\"n\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + password))
                .andExpect(jsonPath("$.googleLinked").value(true)).andExpect(jsonPath("$.email").value(email));

        String viaGoogle = JsonPath.read(googleLogin().andExpect(jsonPath("$.created").value(false))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + viaGoogle))
                .andExpect(jsonPath("$.email").value(email)); // 새 계정이 아니라 원래 계정
    }

    @Test
    void 다른_계정에_이미_붙은_구글은_연결할_수_없고_로그인하지_않으면_401() throws Exception {
        googleReturns(new SocialProfile("google-" + UUID.randomUUID(), uniqueEmail(), true, "구글"));
        googleLogin().andExpect(status().isOk()); // 그 구글로 새 계정이 생김
        String other = uniqueEmail();
        signup(other);
        String token = JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(other)))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        String body = "{\"code\":\"c\",\"redirectUri\":\"http://localhost:3000/auth/callback/google\",\"nonce\":\"n\"}";
        mvc.perform(post("/api/v1/me/social/google").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GOOGLE_ALREADY_LINKED"));
        mvc.perform(post("/api/v1/me/social/google").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    private void googleReturns(SocialProfile profile) {
        given(google.verify(anyString(), anyString(), anyString())).willReturn(profile);
    }

    private ResultActions googleLogin() throws Exception {
        return mvc.perform(post("/api/v1/auth/social/google").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"c","redirectUri":"http://localhost:5173/auth/callback/google","nonce":"n"}"""));
    }

    private void signup(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"테스트\"}".formatted(email)))
                .andExpect(status().isCreated());
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
