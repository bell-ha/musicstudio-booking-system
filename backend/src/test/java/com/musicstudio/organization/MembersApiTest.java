package com.musicstudio.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

import com.musicstudio.support.ApiClient;
import com.musicstudio.support.IntegrationTest;

/** 초대, 가입 코드, 멤버 관리, 기관 범위 검사 (UC-04~08). */
@IntegrationTest
@AutoConfigureMockMvc
class MembersApiTest {

    @Autowired
    MockMvc mvc;

    ApiClient api;
    String owner;
    long orgId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        owner = api.newUser();
        orgId = api.newOrganization(owner);
    }

    // ---------- 초대 ----------

    @Test
    void 초대_링크로_들어오면_그_역할의_활성_멤버가_된다() throws Exception {
        String token = invite(owner, "TEACHER");
        String teacher = api.newUser();

        accept(teacher, token).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TEACHER"));

        api.call(teacher, HttpMethod.GET, "/api/v1/me/organizations", null)
                .andExpect(jsonPath("$[0].role").value("TEACHER"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void 같은_초대를_동시에_수락하면_한_명만_들어온다() throws Exception {
        String token = invite(owner, "STUDENT");
        List<String> users = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            users.add(api.newUser());
        }

        List<Integer> statuses = concurrently(users.stream()
                .map(u -> (Callable<Integer>) () -> accept(u, token).andReturn().getResponse().getStatus())
                .toList());

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 404).hasSize(5);
    }

    @Test
    void 이미_멤버면_409이고_초대는_소비되지_않는다() throws Exception {
        String token = invite(owner, "STUDENT");
        accept(owner, token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));

        accept(api.newUser(), token).andExpect(status().isOk());
    }

    @Test
    void 관리자는_관리자를_초대할_수_없고_소유자는_할_수_있다() throws Exception {
        String manager = join(invite(owner, "MANAGER"));

        api.call(manager, HttpMethod.POST, orgPath("/invitations"), "{\"role\":\"MANAGER\"}")
                .andExpect(status().isForbidden());
        api.call(manager, HttpMethod.POST, orgPath("/invitations"), "{\"role\":\"TEACHER\"}")
                .andExpect(status().isCreated());
    }

    // ---------- 가입 코드 ----------

    @Test
    void 가입_코드로_신청하고_승인되면_멤버가_된다() throws Exception {
        api.call(owner, HttpMethod.PATCH, orgPath("/join-code"),
                        "{\"joinForm\":[{\"key\":\"학번\",\"label\":\"학번\",\"required\":true}]}")
                .andExpect(status().isOk());
        String code = ApiClient.read(api.call(owner, HttpMethod.GET, orgPath("/join-code"), null), "$.joinCode");
        String student = api.newUser();
        String messyCode = code.substring(0, 4).toLowerCase() + "-" + code.substring(4);

        api.call(student, HttpMethod.POST, "/api/v1/join-requests", "{\"code\":\"%s\",\"answers\":{}}".formatted(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("answers.학번"));
        api.call(student, HttpMethod.POST, "/api/v1/join-requests",
                        "{\"code\":\"%s\",\"answers\":{\"학번\":\"32190001\"}}".formatted(messyCode))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // 대기 중에는 기관 API를 못 쓴다
        api.call(student, HttpMethod.GET, orgPath("/members"), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));

        Number membershipId = ApiClient.read(api.call(owner, HttpMethod.GET, orgPath("/members?status=PENDING"), null),
                "$[0].membershipId");
        api.call(owner, HttpMethod.PATCH, orgPath("/members/" + membershipId), "{\"status\":\"ACTIVE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.학번").value("32190001"));

        api.call(student, HttpMethod.POST, "/api/v1/join-requests",
                        "{\"code\":\"%s\",\"answers\":{\"학번\":\"1\"}}".formatted(code))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
    }

    @Test
    void 거절된_사람은_다시_신청할_수_있다() throws Exception {
        String code = ApiClient.read(api.call(owner, HttpMethod.GET, orgPath("/join-code"), null), "$.joinCode");
        String student = api.newUser();
        String body = "{\"code\":\"%s\"}".formatted(code);
        api.call(student, HttpMethod.POST, "/api/v1/join-requests", body).andExpect(status().isCreated());
        Number id = ApiClient.read(api.call(owner, HttpMethod.GET, orgPath("/members?status=PENDING"), null),
                "$[0].membershipId");
        api.call(owner, HttpMethod.PATCH, orgPath("/members/" + id), "{\"status\":\"REJECTED\"}")
                .andExpect(status().isOk());

        api.call(student, HttpMethod.POST, "/api/v1/join-requests", body)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void 꺼진_가입_코드는_찾을_수_없다() throws Exception {
        String code = ApiClient.read(api.call(owner, HttpMethod.PATCH, orgPath("/join-code"), "{\"enabled\":false}"),
                "$.joinCode");
        api.call(api.newUser(), HttpMethod.POST, "/api/v1/join-requests/lookup", "{\"code\":\"%s\"}".formatted(code))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("JOIN_CODE_INVALID"));
    }

    // ---------- 멤버 관리와 기관 범위 검사 ----------

    @Test
    void 학생은_멤버_목록을_볼_수_없다() throws Exception {
        String student = join(invite(owner, "STUDENT"));
        api.call(student, HttpMethod.GET, orgPath("/members"), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 다른_기관의_멤버는_이_기관_API를_쓸_수_없고_다른_기관의_멤버ID는_404() throws Exception {
        String other = api.newUser();
        long otherOrg = api.newOrganization(other);
        api.call(other, HttpMethod.GET, orgPath("/members"), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));

        Number otherOwnerMembership = ApiClient.read(api.call(other, HttpMethod.GET,
                "/api/v1/organizations/" + otherOrg + "/members", null), "$[0].membershipId");
        api.call(owner, HttpMethod.PATCH, orgPath("/members/" + otherOwnerMembership), "{\"status\":\"INACTIVE\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void 기관ID를_다른_표기로_써서_검사를_우회할_수_없다() throws Exception {
        for (String alias : List.of("0" + orgId, "+" + orgId)) {
            api.call(owner, HttpMethod.GET, "/api/v1/organizations/" + alias + "/members", null)
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void 비활성화하면_다음_요청부터_막히고_다시_활성화할_수_있다() throws Exception {
        String manager = join(invite(owner, "MANAGER"));
        Number id = membershipIdOf("MANAGER");

        api.call(owner, HttpMethod.PATCH, orgPath("/members/" + id), "{\"status\":\"INACTIVE\"}").andExpect(status().isOk());
        api.call(manager, HttpMethod.GET, orgPath("/members"), null).andExpect(status().isForbidden());

        api.call(owner, HttpMethod.PATCH, orgPath("/members/" + id), "{\"status\":\"ACTIVE\"}").andExpect(status().isOk());
        api.call(manager, HttpMethod.GET, orgPath("/members"), null).andExpect(status().isOk());
    }

    @Test
    void 관리자는_소유자를_건드리거나_관리자를_만들_수_없다() throws Exception {
        String manager = join(invite(owner, "MANAGER"));
        join(invite(owner, "STUDENT"));
        Number ownerId = membershipIdOf("OWNER");
        Number studentId = membershipIdOf("STUDENT");

        api.call(manager, HttpMethod.PATCH, orgPath("/members/" + ownerId), "{\"status\":\"INACTIVE\"}")
                .andExpect(status().isForbidden());
        api.call(manager, HttpMethod.PATCH, orgPath("/members/" + studentId), "{\"role\":\"MANAGER\"}")
                .andExpect(status().isForbidden());
        api.call(manager, HttpMethod.PATCH, orgPath("/members/" + studentId), "{\"role\":\"TEACHER\"}")
                .andExpect(status().isOk());
    }

    @Test
    void 역할과_상태를_함께_바꾸면_400() throws Exception {
        api.call(owner, HttpMethod.PATCH, orgPath("/members/" + membershipIdOf("OWNER")),
                "{\"role\":\"OWNER\",\"status\":\"ACTIVE\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 마지막_소유자는_강등할_수_없다() throws Exception {
        api.call(owner, HttpMethod.PATCH, orgPath("/members/" + membershipIdOf("OWNER")), "{\"role\":\"MANAGER\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_OWNER"));
    }

    @Test
    void 소유자_둘이_서로를_동시에_강등하면_한_명만_성공한다() throws Exception {
        for (int round = 0; round < 5; round++) {
            // 회차마다 소유자가 둘인 새 기관
            owner = api.newUser();
            orgId = api.newOrganization(owner);
            String second = join(invite(owner, "MANAGER"));
            Number secondId = membershipIdOf("MANAGER");
            Number firstId = membershipIdOf("OWNER");
            api.call(owner, HttpMethod.PATCH, orgPath("/members/" + secondId), "{\"role\":\"OWNER\"}")
                    .andExpect(status().isOk());

            List<Integer> statuses = concurrently(List.of(
                    () -> demote(owner, secondId), () -> demote(second, firstId)));

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            List<?> owners = ApiClient.read(api.call(owner, HttpMethod.GET, orgPath("/members"), null),
                    "$[?(@.role == 'OWNER')]");
            assertThat(owners).hasSize(1);
        }
    }

    private int demote(String actor, Number target) throws Exception {
        return api.call(actor, HttpMethod.PATCH, orgPath("/members/" + target), "{\"role\":\"MANAGER\"}")
                .andReturn().getResponse().getStatus();
    }

    // ---------- 도우미 ----------

    private String orgPath(String rest) {
        return "/api/v1/organizations/" + orgId + rest;
    }

    private String invite(String actor, String role) throws Exception {
        return ApiClient.read(api.call(actor, HttpMethod.POST, orgPath("/invitations"), "{\"role\":\"%s\"}".formatted(role))
                .andExpect(status().isCreated()), "$.token");
    }

    private org.springframework.test.web.servlet.ResultActions accept(String user, String token) throws Exception {
        return api.call(user, HttpMethod.POST, "/api/v1/invitations/accept", "{\"token\":\"%s\"}".formatted(token));
    }

    /** 새 사용자가 초대를 수락하고 그 사람의 토큰을 돌려준다. */
    private String join(String invitationToken) throws Exception {
        String user = api.newUser();
        accept(user, invitationToken).andExpect(status().isOk());
        return user;
    }

    private Number membershipIdOf(String role) throws Exception {
        List<Number> ids = ApiClient.read(api.call(owner, HttpMethod.GET, orgPath("/members"), null),
                "$[?(@.role == '%s')].membershipId".formatted(role));
        return ids.getFirst();
    }

    private static List<Integer> concurrently(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }
}
