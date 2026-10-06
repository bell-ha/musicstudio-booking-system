package com.musicstudio.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** API 테스트에서 반복되는 가입·로그인·기관 만들기. */
public final class ApiClient {

    private final MockMvc mvc;

    public ApiClient(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** 새 사용자를 만들고 접근 토큰을 돌려준다. */
    public String newUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        call(null, HttpMethod.POST, "/api/v1/auth/signup",
                "{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"테스트\"}".formatted(email))
                .andExpect(status().isCreated());
        return read(call(null, HttpMethod.POST, "/api/v1/auth/login",
                "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)), "$.accessToken");
    }

    /** 기관을 만들고 ID를 돌려준다. 만든 사람이 소유자다. */
    public long newOrganization(String ownerToken) throws Exception {
        Number id = read(call(ownerToken, HttpMethod.POST, "/api/v1/organizations",
                "{\"name\":\"테스트 학원\",\"type\":\"ACADEMY\",\"timezone\":\"Asia/Seoul\"}"), "$.id");
        return id.longValue();
    }

    public ResultActions call(String token, HttpMethod method, String path, String body) throws Exception {
        MockHttpServletRequestBuilder req = request(method, path);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    public static <T> T read(ResultActions result, String jsonPath) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath);
    }
}
