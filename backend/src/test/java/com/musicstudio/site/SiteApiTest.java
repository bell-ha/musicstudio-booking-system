package com.musicstudio.site;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.musicstudio.support.ApiClient;
import com.musicstudio.support.IntegrationTest;

/** 기관 사이트: 꾸미기, 공개 설정, 로고, 공지, 공개 페이지, 공개 페이지 가입 (UC-10~14, API 37~48). */
@IntegrationTest
@AutoConfigureMockMvc
class SiteApiTest {

    /** 가장 작은 PNG(1×1). 앞 8바이트가 PNG 서명이다 */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

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

    // ---------- 꾸미기 (37, 38) ----------

    @Test
    void 저장한_적이_없으면_기본값이고_학생에게는_공개_설정을_주지_않는다() throws Exception {
        api.call(owner, HttpMethod.GET, path("/site"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("INDIGO"))
                .andExpect(jsonPath("$.intro").value(""))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.acceptJoin").value(false));
        api.call(member("STUDENT"), HttpMethod.GET, path("/site"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(nullValue()))
                .andExpect(jsonPath("$.slug").value(nullValue()))
                .andExpect(jsonPath("$.acceptJoin").value(nullValue()));
    }

    @Test
    void 관리자는_꾸미고_공개_주소는_못_바꾼다() throws Exception {
        String manager = member("MANAGER");
        api.call(manager, HttpMethod.PUT, path("/site"), describe("VIOLET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("VIOLET"))
                .andExpect(jsonPath("$.intro").value("피아노 전문\n1:1 레슨"))
                .andExpect(jsonPath("$.published").value(false))     // 관리자는 공개 여부를 본다
                .andExpect(jsonPath("$.acceptJoin").value(nullValue())); // 가입 받기는 소유자만
        api.call(manager, HttpMethod.PUT, path("/site/publishing"), publishing("harmony", true, false))
                .andExpect(status().isForbidden());
        api.call(member("STUDENT"), HttpMethod.PUT, path("/site"), describe("BLUE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 목록_밖의_색은_400() throws Exception {
        api.call(owner, HttpMethod.PUT, path("/site"), describe("GREEN")).andExpect(status().isBadRequest());
    }

    // ---------- 공개 설정 (39) ----------

    @Test
    void 공개_주소_규칙() throws Exception {
        api.call(owner, HttpMethod.PUT, path("/site/publishing"), publishing("-bad", false, false))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SLUG"));
        api.call(owner, HttpMethod.PUT, path("/site/publishing"), publishing("admin", false, false))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SLUG_RESERVED"));
        api.call(owner, HttpMethod.PUT, path("/site/publishing"), publishing("", true, false))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("SLUG_REQUIRED"));

        String slug = slug();
        api.call(owner, HttpMethod.PUT, path("/site/publishing"), publishing(slug.toUpperCase(), true, false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slug").value(slug)); // 소문자로 저장

        String other = api.newUser();
        long otherOrg = api.newOrganization(other);
        api.call(other, HttpMethod.PUT, "/api/v1/organizations/" + otherOrg + "/site/publishing",
                        publishing(slug, false, false))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLUG_TAKEN"));
    }

    // ---------- 공개 페이지 (46) ----------

    @Test
    void 없는_주소와_비공개는_같은_404() throws Exception {
        String slug = slug();
        api.call(owner, HttpMethod.PUT, path("/site/publishing"), publishing(slug, false, false))
                .andExpect(status().isOk());
        String privateBody = mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/sites/" + slug))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missingBody = mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/sites/" + slug()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(stripInstance(privateBody)).isEqualTo(stripInstance(missingBody));
    }

    @Test
    void 공개_페이지는_정해_둔_항목과_외부_공개_공지만_준다() throws Exception {
        String slug = publish(false);
        notice("강사 회의", "STAFF");
        notice("이번 주 휴강", "MEMBERS");
        notice("신입생 모집", "PUBLIC");

        // 만료된 토큰이 붙어 와도 공개 경로에서는 읽지 않는다
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/sites/" + slug).header("Authorization", "Bearer broken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("테스트 학원"))
                .andExpect(jsonPath("$.intro").value("피아노 전문\n1:1 레슨"))
                .andExpect(jsonPath("$.notices", hasSize(1)))
                .andExpect(jsonPath("$.notices[0].title").value("신입생 모집"))
                .andExpect(jsonPath("$.join.open").value(false))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.joinCode").doesNotExist())
                .andExpect(content().string(not(containsString(joinCode()))));
    }

    // ---------- 공지 (42~45) ----------

    @Test
    void 학생은_강사_이상_공지를_보지_못하고_쓰지도_못한다() throws Exception {
        notice("강사 회의", "STAFF");
        notice("이번 주 휴강", "MEMBERS");
        long pinned = notice("상담 주간", "MEMBERS");
        api.call(owner, HttpMethod.PATCH, path("/notices/" + pinned),
                "{\"title\":\"상담 주간\",\"body\":\"\",\"visibility\":\"MEMBERS\",\"pinned\":true}")
                .andExpect(status().isOk());

        String student = member("STUDENT");
        api.call(student, HttpMethod.GET, path("/notices"), null)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("상담 주간")); // 고정 먼저
        api.call(member("TEACHER"), HttpMethod.GET, path("/notices"), null).andExpect(jsonPath("$", hasSize(3)));
        api.call(student, HttpMethod.POST, path("/notices"),
                "{\"title\":\"x\",\"body\":\"\",\"visibility\":\"MEMBERS\"}").andExpect(status().isForbidden());
    }

    @Test
    void 다른_기관의_공지는_고치거나_지울_수_없다() throws Exception {
        long id = notice("휴강", "MEMBERS");
        String other = api.newUser();
        long otherOrg = api.newOrganization(other);
        String body = "{\"title\":\"x\",\"body\":\"\",\"visibility\":\"MEMBERS\"}";
        api.call(other, HttpMethod.PATCH, "/api/v1/organizations/" + otherOrg + "/notices/" + id, body)
                .andExpect(status().isNotFound());
        api.call(other, HttpMethod.DELETE, "/api/v1/organizations/" + otherOrg + "/notices/" + id, null)
                .andExpect(status().isNotFound());
        api.call(owner, HttpMethod.DELETE, path("/notices/" + id), null).andExpect(status().isNoContent());
    }

    // ---------- 로고 (40, 41, 47) ----------

    @Test
    void 로고는_형식을_내용으로_확인하고_크기를_제한한다() throws Exception {
        uploadLogo("image/svg+xml", "<svg onload=alert(1)/>".getBytes()).andExpect(status().isUnsupportedMediaType());
        uploadLogo("image/png", "not an image".getBytes())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        byte[] big = new byte[200 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        uploadLogo("image/png", big).andExpect(status().isContentTooLarge());
        uploadLogo("image/jpeg", PNG).andExpect(status().isBadRequest()); // 선언과 내용이 다르다
    }

    @Test
    void 로고는_무작위_키_URL로_로그인_없이_받고_바꾸면_키가_바뀐다() throws Exception {
        String first = ApiClient.read(uploadLogo("image/png", PNG).andExpect(status().isOk()), "$.logoUrl");
        org.assertj.core.api.Assertions.assertThat(first).startsWith("/api/v1/public/logos/");
        mvc.perform(MockMvcRequestBuilders.get(first))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(content().bytes(PNG));

        String second = ApiClient.read(uploadLogo("image/png", PNG), "$.logoUrl");
        org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(first);
        mvc.perform(MockMvcRequestBuilders.get(first)).andExpect(status().isNotFound());
        api.call(member("STUDENT"), HttpMethod.GET, path("/site"), null).andExpect(jsonPath("$.logoUrl").value(second));

        api.call(owner, HttpMethod.DELETE, path("/site/logo"), null).andExpect(status().isNoContent());
        api.call(owner, HttpMethod.GET, path("/site"), null).andExpect(jsonPath("$.logoUrl").value(nullValue()));
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/logos/not-a-uuid")).andExpect(status().isNotFound());
    }

    // ---------- 공개 페이지에서 가입 (48) ----------

    @Test
    void 가입_받기를_켜야_공개_페이지에서_신청할_수_있다() throws Exception {
        String slug = publish(false);
        String visitor = api.newUser();
        api.call(visitor, HttpMethod.POST, "/api/v1/sites/" + slug + "/join-requests", "{}")
                .andExpect(status().isNotFound());

        api.call(owner, HttpMethod.PUT, path("/site/publishing"), publishing(slug, true, true)).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/sites/" + slug)).andExpect(jsonPath("$.join.open").value(true));
        api.call(visitor, HttpMethod.POST, "/api/v1/sites/" + slug + "/join-requests", "{\"answers\":{}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.organizationName").value("테스트 학원"));
        api.call(visitor, HttpMethod.POST, "/api/v1/sites/" + slug + "/join-requests", "{}")
                .andExpect(status().isConflict()); // 이미 신청함
    }

    @Test
    void 가입_코드를_끄면_공개_페이지_가입도_닫힌다() throws Exception {
        String slug = publish(true);
        api.call(owner, HttpMethod.PATCH, path("/join-code"), "{\"enabled\":false}").andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/sites/" + slug)).andExpect(jsonPath("$.join.open").value(false));
        api.call(api.newUser(), HttpMethod.POST, "/api/v1/sites/" + slug + "/join-requests", "{}")
                .andExpect(status().isNotFound());
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/sites/" + slug + "/join-requests"))
                .andExpect(status().isUnauthorized()); // 신청은 로그인해야 한다
    }

    // ---------- 도움 ----------

    private String path(String p) {
        return "/api/v1/organizations/" + orgId + p;
    }

    private static String slug() {
        return "s-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String describe(String color) {
        return """
                {"intro":"피아노 전문\\n1:1 레슨","address":"서울시 어딘가 1","phone":"02-123-4567",
                 "hoursText":"평일 14~22시","color":"%s"}""".formatted(color);
    }

    private static String publishing(String slug, boolean published, boolean acceptJoin) {
        return "{\"slug\":\"%s\",\"published\":%s,\"acceptJoin\":%s}".formatted(slug, published, acceptJoin);
    }

    /** 소개를 쓰고 공개한다. 주소를 돌려준다 */
    private String publish(boolean acceptJoin) throws Exception {
        String slug = slug();
        api.call(owner, HttpMethod.PUT, path("/site"), describe("BLUE")).andExpect(status().isOk());
        api.call(owner, HttpMethod.PUT, path("/site/publishing"), publishing(slug, true, acceptJoin))
                .andExpect(status().isOk());
        return slug;
    }

    private long notice(String title, String visibility) throws Exception {
        Number id = ApiClient.read(api.call(owner, HttpMethod.POST, path("/notices"),
                "{\"title\":\"%s\",\"body\":\"본문\",\"visibility\":\"%s\"}".formatted(title, visibility))
                .andExpect(status().isCreated()), "$.id");
        return id.longValue();
    }

    private String joinCode() throws Exception {
        return ApiClient.read(api.call(owner, HttpMethod.GET, path("/join-code"), null), "$.joinCode");
    }

    private ResultActions uploadLogo(String type, byte[] bytes) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put(path("/site/logo"))
                .header("Authorization", "Bearer " + owner).contentType(type).content(bytes));
    }

    private static String stripInstance(String problem) {
        return problem.replaceAll("\"instance\":\"[^\"]*\",?", "");
    }

    private String member(String role) throws Exception {
        String token = ApiClient.read(api.call(owner, HttpMethod.POST, path("/invitations"),
                "{\"role\":\"%s\"}".formatted(role)), "$.token");
        String user = api.newUser();
        api.call(user, HttpMethod.POST, "/api/v1/invitations/accept", "{\"token\":\"%s\"}".formatted(token))
                .andExpect(status().isOk());
        return user;
    }
}
