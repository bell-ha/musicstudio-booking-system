package com.musicstudio.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.musicstudio.support.IntegrationTest;

/** UC-01 가입·로그인, UC-02 기관 만들기, UC-09 내 기관 목록. */
@IntegrationTest
@AutoConfigureMockMvc
class AuthAndOrganizationApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 가입하고_로그인하고_기관을_만들면_소유자가_된다() throws Exception {
        String token = signupAndLogin(uniqueEmail());

        mvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"하모니 음악학원","type":"ACADEMY","timezone":"Asia/Seoul"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modules", containsInAnyOrder("PRACTICE_ROOM", "ACADEMY")))
                .andExpect(jsonPath("$.joinCode").isString());

        mvc.perform(get("/api/v1/me/organizations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("하모니 음악학원"))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void 학교는_모듈을_고르지_않으면_연습실만_켜진다() throws Exception {
        String token = signupAndLogin(uniqueEmail());

        mvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"뉴뮤직학부","type":"SCHOOL","timezone":"Asia/Seoul"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modules", containsInAnyOrder("PRACTICE_ROOM")));
    }

    @Test
    void 대소문자만_다른_이메일로는_다시_가입할_수_없다() throws Exception {
        String email = uniqueEmail();
        signup(email).andExpect(status().isCreated());

        signup(email.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void 입력이_잘못되면_필드별_오류를_준다() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","name":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.example.com/problems/validation-failed"))
                .andExpect(jsonPath("$.errors", hasSize(3)));
    }

    @Test
    void 비밀번호가_틀리면_401() throws Exception {
        String email = uniqueEmail();
        signup(email).andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 토큰_없이_보호된_API를_부르면_401() throws Exception {
        mvc.perform(get("/api/v1/me/organizations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.example.com/problems/unauthenticated"));
    }

    @Test
    void 알_수_없는_시간대는_거절한다() throws Exception {
        String token = signupAndLogin(uniqueEmail());

        mvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"학원","type":"ACADEMY","timezone":"Mars/Olympus"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
    }

    @Test
    void 바이트로_72를_넘는_한글_비밀번호는_400() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"name\":\"테스트\"}"
                                .formatted(uniqueEmail(), "가".repeat(30))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
    }

    @Test
    void 같은_이메일로_동시에_가입하면_한_건만_성공한다() throws Exception {
        String email = uniqueEmail();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return signup(email).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
    }

    @Test
    void 잘못된_토큰이_붙어_있어도_로그인은_된다() throws Exception {
        String email = uniqueEmail();
        signup(email).andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/login").header("Authorization", "Bearer expired.or.broken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void 없는_유형_값은_한글_문구의_400() throws Exception {
        String token = signupAndLogin(uniqueEmail());

        mvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"학원","type":"FOO","timezone":"Asia/Seoul"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.example.com/problems/validation-failed"))
                .andExpect(jsonPath("$.title").value("요청 형식이 올바르지 않습니다"));
    }

    @Test
    void 오프셋_형식의_시간대는_거절한다() throws Exception {
        String token = signupAndLogin(uniqueEmail());

        mvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"학원","type":"ACADEMY","timezone":"UTC+9"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
    }

    private String signupAndLogin(String email) throws Exception {
        signup(email).andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private org.springframework.test.web.servlet.ResultActions signup(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"테스트\"}".formatted(email)));
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
