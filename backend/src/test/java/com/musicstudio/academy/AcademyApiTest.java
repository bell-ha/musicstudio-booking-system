package com.musicstudio.academy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.musicstudio.support.ApiClient;
import com.musicstudio.support.IntegrationTest;
import com.musicstudio.support.MutableClock;

/** 학원 관리 (UC-40~48, API 24~36). 오늘은 2026-10-09(서울)로 시작하고, 필요하면 날짜를 옮긴다. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(AcademyApiTest.Clocks.class)
class AcademyApiTest {

    static final OffsetDateTime START = OffsetDateTime.parse("2026-10-09T10:00:00+09:00");

    @TestConfiguration
    static class Clocks {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START.toInstant());
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    ApiClient api;
    String owner;
    long orgId;
    long subjectId;
    long periodProduct;
    long countProduct;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(START.toInstant());
        api = new ApiClient(mvc);
        owner = api.newUser();
        orgId = api.newOrganization(owner);
        subjectId = id(post(owner, "/subjects", "{\"name\":\"피아노\"}"));
        periodProduct = id(post(owner, "/products", """
                {"subjectId":%d,"name":"피아노 3개월","kind":"PERIOD","periodMonths":3,"lessonMinutes":50,"price":450000}"""
                .formatted(subjectId)));
        countProduct = id(post(owner, "/products", """
                {"subjectId":%d,"name":"피아노 10회","kind":"COUNT","sessionCount":10,"lessonMinutes":50,"price":300000}"""
                .formatted(subjectId)));
    }

    // ---------- 과목·상품 ----------

    @Test
    void 상품은_종류에_맞는_값만_받고_같은_과목_이름은_막는다() throws Exception {
        post(owner, "/products", """
                {"subjectId":%d,"name":"잘못","kind":"COUNT","periodMonths":3,"lessonMinutes":50,"price":1}"""
                .formatted(subjectId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PRODUCT"));
        post(owner, "/subjects", "{\"name\":\"피아노\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBJECT_NAME_TAKEN"));
        api.call(owner, HttpMethod.GET, path("/catalog"), null)
                .andExpect(jsonPath("$.subjects", hasSize(1))).andExpect(jsonPath("$.products", hasSize(2)));
    }

    // ---------- 원생과 연락처 ----------

    @Test
    void 연락처는_암호화해서_저장하고_목록에서는_가린다() throws Exception {
        long student = id(post(owner, "/students", """
                {"name":"김민지","birthYear":2012,"phone":"010-1234-5678","guardianName":"김엄마","guardianPhone":"01098765432"}""")
                .andExpect(jsonPath("$.phone").value("01012345678")));

        api.call(owner, HttpMethod.GET, path("/students"), null)
                .andExpect(jsonPath("$[0].phone").value("010-****-5678"));
        byte[] stored = jdbc.queryForObject("select phone_enc from student where id = ?", byte[].class, student);
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("01012345678");

        post(owner, "/students", "{\"name\":\"이름\",\"phone\":\"123\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("phone"));
    }

    // ---------- 수강 ----------

    @Test
    void 기간권은_시작일부터_N개월_전날까지이고_금액은_등록_때_값으로_남는다() throws Exception {
        long student = student("김민지");
        long ownerMembership = membershipId(owner);

        enroll(student, periodProduct, ownerMembership, "2026-03-15").andExpect(status().isCreated())
                .andExpect(jsonPath("$.endsOn").value("2026-06-14"))
                .andExpect(jsonPath("$.price").value(450000));

        api.call(owner, HttpMethod.PATCH, path("/products/" + periodProduct), "{\"price\":500000}").andExpect(status().isOk());
        api.call(owner, HttpMethod.GET, path("/students/" + student), null)
                .andExpect(jsonPath("$.enrollments[0].price").value(450000));
    }

    @Test
    void 정지한_날수만큼_종료일이_늘어난다() throws Exception {
        long e = id(enroll(student("김민지"), periodProduct, membershipId(owner), "2026-10-01"));

        act(e, "pause", null).andExpect(jsonPath("$.status").value("PAUSED"));
        clock.set(START.plusDays(10).toInstant());
        act(e, "resume", null).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.endsOn").value("2027-01-10"));

        act(e, "end", null).andExpect(jsonPath("$.status").value("ENDED"));
        act(e, "resume", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void 기간권은_개월로_횟수권은_횟수로_연장한다() throws Exception {
        long student = student("김민지");
        long period = id(enroll(student, periodProduct, membershipId(owner), "2026-10-01"));
        long count = id(enroll(student, countProduct, membershipId(owner), "2026-10-01"));

        act(period, "extend", "{\"months\":1}").andExpect(jsonPath("$.endsOn").value("2027-01-31"));
        act(count, "extend", "{\"sessions\":4}").andExpect(jsonPath("$.totalSessions").value(14));
        act(count, "extend", "{\"months\":1}").andExpect(status().isBadRequest());
    }

    @Test
    void 비활성_원생과_비활성_강사는_수강에_쓸_수_없다() throws Exception {
        String teacher = member("TEACHER");
        long teacherId = membershipId(teacher);
        long student = student("김민지");

        api.call(owner, HttpMethod.PATCH, path("/students/" + student), "{\"name\":\"김민지\",\"active\":false}")
                .andExpect(status().isOk());
        enroll(student, periodProduct, teacherId, "2026-10-01")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("STUDENT_INACTIVE"));

        long other = student("이서준");
        api.call(owner, HttpMethod.PATCH, "/api/v1/organizations/" + orgId + "/members/" + teacherId,
                "{\"status\":\"INACTIVE\"}").andExpect(status().isOk());
        enroll(other, periodProduct, teacherId, "2026-10-01")
                .andExpect(jsonPath("$.code").value("TEACHER_INACTIVE"));
        enroll(other, periodProduct, membershipId(member("STUDENT")), "2026-10-01")
                .andExpect(jsonPath("$.code").value("INVALID_TEACHER"));
    }

    @Test
    void 강사는_담당_원생만_보고_만료_임박_필터가_동작한다() throws Exception {
        String teacher = member("TEACHER");
        long mine = student("담당생");
        student("다른생");
        enroll(mine, periodProduct, membershipId(teacher), "2026-07-20"); // 종료 2026-10-19, 오늘(10-09)부터 10일 뒤

        api.call(teacher, HttpMethod.GET, path("/students"), null)
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value("담당생"));
        api.call(owner, HttpMethod.GET, path("/students"), null).andExpect(jsonPath("$", hasSize(2)));
        api.call(owner, HttpMethod.GET, path("/students?mine=true"), null).andExpect(jsonPath("$", hasSize(0)));
        api.call(owner, HttpMethod.GET, path("/students?expiringWithinDays=14"), null)
                .andExpect(jsonPath("$", hasSize(1)));
        api.call(owner, HttpMethod.GET, path("/students?expiringWithinDays=5"), null)
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- 레슨 기록 ----------

    @Test
    void 담당_강사만_기록을_쓰고_쓴_사람만_고치며_강사가_바뀌면_새_강사가_이전_기록을_본다() throws Exception {
        String first = member("TEACHER");
        String second = member("TEACHER");
        long student = student("김민지");
        long e = id(enroll(student, periodProduct, membershipId(first), "2026-10-01"));

        writeRecord(second, e, false).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ASSIGNED"));
        long record = id(writeRecord(first, e, false).andExpect(status().isCreated()));
        api.call(second, HttpMethod.GET, path("/students/" + student), null).andExpect(status().isNotFound());

        act(e, "change-teacher", "{\"teacherMembershipId\":%d}".formatted(membershipId(second)));

        api.call(second, HttpMethod.GET, path("/students/" + student), null)
                .andExpect(jsonPath("$.records", hasSize(1)))
                .andExpect(jsonPath("$.records[0].mine").value(false))
                .andExpect(jsonPath("$.student.phone").doesNotExist());
        api.call(second, HttpMethod.PATCH, path("/lesson-records/" + record), recordBody(e, true))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        // 이전 강사는 자기가 쓴 기록을 계속 보고 고친다
        api.call(first, HttpMethod.GET, path("/students/" + student), null).andExpect(jsonPath("$.records", hasSize(1)));
        api.call(first, HttpMethod.PATCH, path("/lesson-records/" + record), recordBody(e, true)).andExpect(status().isOk());
    }

    @Test
    void 원장이_직접_가르치면_기록을_쓸_수_있다() throws Exception {
        long e = id(enroll(student("김민지"), periodProduct, membershipId(owner), "2026-10-01"));
        writeRecord(owner, e, false).andExpect(status().isCreated());
    }

    @Test
    void 학생은_연결된_자기_수강과_공개된_기록만_본다() throws Exception {
        String studentUser = member("STUDENT");
        long student = student("김민지");
        api.call(studentUser, HttpMethod.GET, path("/students/me"), null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_LINKED"));

        api.call(owner, HttpMethod.PUT, path("/students/" + student + "/account"),
                "{\"membershipId\":%d}".formatted(membershipId(studentUser))).andExpect(status().isOk());
        long other = student("다른생");
        api.call(owner, HttpMethod.PUT, path("/students/" + other + "/account"),
                        "{\"membershipId\":%d}".formatted(membershipId(studentUser)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_LINKED"));

        long e = id(enroll(student, periodProduct, membershipId(owner), "2026-10-01"));
        writeRecord(owner, e, true);
        writeRecord(owner, e, false);

        api.call(studentUser, HttpMethod.GET, path("/students/me"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records", hasSize(1)))
                .andExpect(jsonPath("$.enrollments[0].price").doesNotExist());
        api.call(studentUser, HttpMethod.GET, path("/students/" + student), null).andExpect(status().isForbidden());
    }

    @Test
    void 학원_관리_모듈을_끈_기관은_학원_API를_쓸_수_없다() throws Exception {
        Number school = ApiClient.read(api.call(owner, HttpMethod.POST, "/api/v1/organizations",
                "{\"name\":\"학교\",\"type\":\"SCHOOL\",\"timezone\":\"Asia/Seoul\"}"), "$.id");
        api.call(owner, HttpMethod.GET, "/api/v1/organizations/" + school + "/academy/catalog", null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
    }

    // ---------- 도우미 ----------

    private String path(String rest) {
        return "/api/v1/organizations/" + orgId + "/academy" + rest;
    }

    private ResultActions post(String token, String rest, String body) throws Exception {
        return api.call(token, HttpMethod.POST, path(rest), body);
    }

    private long student(String name) throws Exception {
        return id(post(owner, "/students", "{\"name\":\"%s\"}".formatted(name)));
    }

    private ResultActions enroll(long student, long product, long teacherMembership, String startsOn) throws Exception {
        return post(owner, "/enrollments", """
                {"studentId":%d,"productId":%d,"teacherMembershipId":%d,"startsOn":"%s"}"""
                .formatted(student, product, teacherMembership, startsOn));
    }

    private ResultActions act(long enrollment, String action, String body) throws Exception {
        return post(owner, "/enrollments/" + enrollment + "/" + action, body);
    }

    private ResultActions writeRecord(String token, long enrollment, boolean visible) throws Exception {
        return post(token, "/lesson-records", recordBody(enrollment, visible));
    }

    private static String recordBody(long enrollment, boolean visible) {
        return """
                {"enrollmentId":%d,"lessonDate":"2026-10-09","progress":"체르니 30번 12","homework":"스케일 연습",
                 "memo":"손목 힘 빼기","visibleToStudent":%s}""".formatted(enrollment, visible);
    }

    /** 초대로 들어온 새 멤버의 토큰. */
    private String member(String role) throws Exception {
        String token = ApiClient.read(api.call(owner, HttpMethod.POST, "/api/v1/organizations/" + orgId + "/invitations",
                "{\"role\":\"%s\"}".formatted(role)), "$.token");
        String user = api.newUser();
        api.call(user, HttpMethod.POST, "/api/v1/invitations/accept", "{\"token\":\"%s\"}".formatted(token))
                .andExpect(status().isOk());
        return user;
    }

    /** 토큰 주인의 이 기관 멤버십 ID. 접근 토큰의 sub가 사용자 ID다. */
    private long membershipId(String token) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        long userId = Long.parseLong(payload.replaceAll(".*\"sub\":\"(\\d+)\".*", "$1"));
        return jdbc.queryForObject("select id from membership where organization_id = ? and user_id = ?",
                Long.class, orgId, userId);
    }

    private static long id(ResultActions result) throws Exception {
        Number id = ApiClient.read(result, "$.id");
        return id.longValue();
    }
}
