package com.musicstudio.organization;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

import com.musicstudio.support.ApiClient;
import com.musicstudio.support.IntegrationTest;

/** UC-03 기관 설정과 모듈 (API 5). */
@IntegrationTest
@AutoConfigureMockMvc
class OrganizationSettingsApiTest {

    @Autowired
    MockMvc mvc;

    ApiClient api;
    String owner;
    long orgId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        owner = api.newUser();
        orgId = api.newOrganization(owner); // 학원: 연습실 + 학원 관리
    }

    @Test
    void 모듈을_끄면_그_API가_막히고_다시_켜면_데이터가_그대로다() throws Exception {
        api.call(owner, HttpMethod.POST, path("/academy/subjects"), "{\"name\":\"피아노\"}").andExpect(status().isCreated());
        api.call(owner, HttpMethod.PATCH, path(""), "{\"modules\":[\"PRACTICE_ROOM\"]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.modules", containsInAnyOrder("PRACTICE_ROOM")));
        api.call(owner, HttpMethod.GET, path("/academy/catalog"), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
        api.call(owner, HttpMethod.PATCH, path(""), "{\"modules\":[\"PRACTICE_ROOM\",\"ACADEMY\"]}").andExpect(status().isOk());
        api.call(owner, HttpMethod.GET, path("/academy/catalog"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.subjects[0].name").value("피아노"));
    }

    @Test
    void 이름을_바꾸고_빈_이름은_거절하며_소유자만_한다() throws Exception {
        api.call(owner, HttpMethod.PATCH, path(""), "{\"name\":\" 하모니 음악학원 \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("하모니 음악학원"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"));
        api.call(owner, HttpMethod.PATCH, path(""), "{\"name\":\"  \"}").andExpect(status().isBadRequest());

        String token = ApiClient.read(api.call(owner, HttpMethod.POST, path("/invitations"), "{\"role\":\"MANAGER\"}"), "$.token");
        String manager = api.newUser();
        api.call(manager, HttpMethod.POST, "/api/v1/invitations/accept", "{\"token\":\"%s\"}".formatted(token))
                .andExpect(status().isOk());
        api.call(manager, HttpMethod.PATCH, path(""), "{\"name\":\"바꿈\"}").andExpect(status().isForbidden());
    }

    private String path(String rest) {
        return "/api/v1/organizations/" + orgId + rest;
    }
}
