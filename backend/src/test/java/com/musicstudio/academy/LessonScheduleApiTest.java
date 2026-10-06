package com.musicstudio.academy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

/**
 * 레슨 일정·출결 (UC-49~53, API 49~55). 오늘은 2026-10-12(월) 09:00 서울로 시작한다.
 * 규칙, 수강 동작과의 관계, 동시성, 그리고 무작위 동작 수열로 불변식을 검사한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(LessonScheduleApiTest.Clocks.class)
class LessonScheduleApiTest {

    static final OffsetDateTime START = OffsetDateTime.parse("2026-10-12T09:00:00+09:00");

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
    long ownerMembership;
    long countProduct;
    long periodProduct;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(START.toInstant());
        api = new ApiClient(mvc);
        owner = api.newUser();
        orgId = api.newOrganization(owner);
        ownerMembership = jdbc.queryForObject("select id from membership where organization_id = ?", Long.class, orgId);
        long subject = id(post(owner, "/subjects", "{\"name\":\"피아노\"}"));
        countProduct = id(post(owner, "/products", """
                {"subjectId":%d,"name":"피아노 10회","kind":"COUNT","sessionCount":10,"lessonMinutes":50,"price":300000}"""
                .formatted(subject)));
        periodProduct = id(post(owner, "/products", """
                {"subjectId":%d,"name":"피아노 1개월","kind":"PERIOD","periodMonths":1,"lessonMinutes":50,"price":200000}"""
                .formatted(subject)));
    }

    // ---------- 일정과 회차 (49) ----------

    @Test
    void 횟수권_일정을_정하면_총_회차만큼_만든다() throws Exception {
        long e = enroll(countProduct, ownerMembership);
        schedule(e, "[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"16:00\"},{\"dayOfWeek\":\"THURSDAY\",\"startTime\":\"16:00\"}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(10))
                .andExpect(jsonPath("$.firstDate").value("2026-10-13"))
                .andExpect(jsonPath("$.lastDate").value("2026-11-12"));
        enrollmentDetail(e).andExpect(jsonPath("$.enrollments[0].remainingSessions").value(10))
                .andExpect(jsonPath("$.enrollments[0].schedule", hasSize(2)))
                .andExpect(jsonPath("$.enrollments[0].nextLessonDate").value("2026-10-13"));
    }

    @Test
    void 기간권은_종료일까지_오늘의_남은_시각부터_만든다() throws Exception {
        long e = enroll(periodProduct, ownerMembership); // 10/12 ~ 11/11
        schedule(e, "[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"16:00\"}]")
                .andExpect(jsonPath("$.created").value(5)) // 10/12(오늘 16시), 19, 26, 11/2, 11/9
                .andExpect(jsonPath("$.firstDate").value("2026-10-12"));
    }

    @Test
    void 같은_강사의_겹치는_일정은_409와_겹치는_날짜를_준다() throws Exception {
        long a = enroll(countProduct, ownerMembership);
        long b = enroll(countProduct, ownerMembership);
        schedule(a, "[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"16:00\"}]").andExpect(status().isOk());
        schedule(b, "[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"16:30\"}]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"))
                .andExpect(jsonPath("$.conflicts[0].date").value("2026-10-13"))
                .andExpect(jsonPath("$.conflicts[0].with").value("TEACHER"))
                .andExpect(jsonPath("$.conflictCount").value(10));
        assertThat(count("select count(*) from lesson_session where enrollment_id = ?", b)).isZero(); // 하나도 안 생김
    }

    @Test
    void 일정_검증() throws Exception {
        long e = enroll(countProduct, ownerMembership);
        schedule(e, "[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"23:30\"}]").andExpect(jsonPath("$.code").value("CROSSES_MIDNIGHT"));
        schedule(e, "[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"16:03\"}]").andExpect(jsonPath("$.code").value("INVALID_SLOTS"));
        schedule(e, "[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"16:00\"},{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"16:30\"}]")
                .andExpect(jsonPath("$.code").value("INVALID_SLOTS"));
        api.call(owner, HttpMethod.PUT, path("/enrollments/" + e + "/schedule"),
                "{\"version\":99,\"slots\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"16:00\"}]}")
                .andExpect(jsonPath("$.code").value("CONFLICTING_UPDATE"));
    }

    // ---------- 출결 (52) ----------

    @Test
    void 출석과_결석은_시작한_뒤에만_사전_결석은_언제나() throws Exception {
        long e = enroll(countProduct, ownerMembership);
        schedule(e, "[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"10:00\"}]").andExpect(status().isOk());
        long first = sessionAt(e, 0);
        mark(first, "ATTENDED").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("TOO_EARLY"));
        mark(first, "EXCUSED").andExpect(status().isOk()).andExpect(jsonPath("$.shortfall").value(0));
        // 사전 결석은 차감하지 않아서 맨 뒤에 하나 이어 붙는다: 차감 0 + 앞으로 10 = 10
        assertThat(count("select count(*) from lesson_session where enrollment_id = ? and status = 'SCHEDULED'", e)).isEqualTo(10);

        clock.set(START.plusHours(1).plusMinutes(10).toInstant()); // 10:10
        mark(first, "ABSENT").andExpect(status().isOk()); // 사전 결석 → 결석으로 고치면 붙였던 회차가 빠진다
        assertThat(count("select count(*) from lesson_session where enrollment_id = ? and status = 'SCHEDULED'", e)).isEqualTo(9);
        mark(first, "ABSENT").andExpect(status().isOk()); // 같은 값은 그대로
        enrollmentDetail(e).andExpect(jsonPath("$.enrollments[0].remainingSessions").value(9));
    }

    @Test
    void 다른_강사의_회차에는_출결을_남길_수_없다() throws Exception {
        String teacher = member("TEACHER");
        long e = enroll(countProduct, ownerMembership);
        schedule(e, "[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"16:00\"}]").andExpect(status().isOk());
        api.call(teacher, HttpMethod.PUT, path("/sessions/" + sessionAt(e, 0) + "/attendance"), "{\"status\":\"EXCUSED\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ASSIGNED"));
        api.call(teacher, HttpMethod.GET, path("/sessions?from=2026-10-12&to=2026-10-31"), null)
                .andExpect(jsonPath("$", hasSize(0))); // 강사는 자기 회차만
    }

    // ---------- 수강 동작과 회차 (FR-AC-17) ----------

    @Test
    void 오후에_정지해도_출결_안_한_오늘_오전_회차는_남는다() throws Exception {
        long e = enroll(countProduct, ownerMembership);
        schedule(e, "[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"10:00\"},{\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"10:00\"}]")
                .andExpect(status().isOk());
        clock.set(START.plusHours(5).toInstant()); // 14:00
        act(e, "pause", null).andExpect(status().isOk());
        assertThat(count("select count(*) from lesson_session where enrollment_id = ?", e)).isEqualTo(1);
        api.call(owner, HttpMethod.GET, path("/sessions?unmarked=true"), null)
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].localDate").value("2026-10-12"));

        clock.set(START.plusDays(7).toInstant()); // 1주 쉬고 재개 → 오늘부터 다시
        act(e, "resume", null).andExpect(status().isOk());
        assertThat(count("select count(*) from lesson_session where enrollment_id = ? and status = 'SCHEDULED'", e)).isEqualTo(10);
    }

    @Test
    void 새_강사와_겹치면_강사_변경_전체가_되돌아간다() throws Exception {
        String teacherToken = member("TEACHER");
        long teacher = membershipOf(teacherToken);
        long mine = enroll(countProduct, ownerMembership);
        long theirs = enroll(countProduct, teacher);
        schedule(mine, "[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"16:00\"}]").andExpect(status().isOk());
        schedule(theirs, "[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"16:00\"}]").andExpect(status().isOk()); // 강사가 달라 된다
        act(mine, "change-teacher", "{\"teacherMembershipId\":" + teacher + "}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));
        assertThat(jdbc.queryForObject("select teacher_membership_id from enrollment where id = ?", Long.class, mine))
                .isEqualTo(ownerMembership);
    }

    // ---------- 휴강·보강 (53~55) ----------

    @Test
    void 그날_전체_휴강은_횟수권마다_뒤에_이어_붙인다() throws Exception {
        long a = enroll(countProduct, ownerMembership);
        long b = enroll(periodProduct, ownerMembership);
        schedule(a, "[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"16:00\"}]").andExpect(status().isOk());
        schedule(b, "[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"18:00\"}]").andExpect(status().isOk());
        post(owner, "/sessions/cancel-day", "{\"date\":\"2026-10-13\",\"reason\":\"추석 연휴\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canceled").value(2))
                .andExpect(jsonPath("$.appended").value(1)); // 기간권은 이어 붙이지 않는다
        assertThat(count("select count(*) from lesson_session where enrollment_id = ? and status = 'SCHEDULED'", a)).isEqualTo(10);
    }

    @Test
    void 보강은_사전_결석으로_빈_시간에_들어가고_횟수권은_맨_뒤가_빠진다() throws Exception {
        long e = enroll(countProduct, ownerMembership);
        schedule(e, "[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"16:00\"}]").andExpect(status().isOk());
        String last = jdbc.queryForObject("select max(local_date)::text from lesson_session where enrollment_id = ?", String.class, e);
        post(owner, "/enrollments/" + e + "/makeups", "{\"startsAt\":\"2026-10-13T16:00:00+09:00\"}")
                .andExpect(status().isConflict()); // 정규 회차와 겹친다
        mark(sessionAt(e, 0), "EXCUSED").andExpect(status().isOk());
        post(owner, "/enrollments/" + e + "/makeups", "{\"startsAt\":\"2026-10-13T16:00:00+09:00\"}")
                .andExpect(status().isCreated());
        assertThat(count("select count(*) from lesson_session where enrollment_id = ? and status = 'SCHEDULED'", e)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select max(local_date)::text from lesson_session where enrollment_id = ?", String.class, e))
                .isEqualTo(last); // 사전 결석으로 붙었던 회차가 보강으로 빠져서 끝 날짜가 처음과 같다
        post(owner, "/enrollments/" + e + "/makeups", "{\"startsAt\":\"2026-10-01T16:00:00+09:00\"}")
                .andExpect(jsonPath("$.code").value("IN_THE_PAST"));
    }

    @Test
    void 학생은_자기_회차와_휴강_사유만_본다() throws Exception {
        String studentToken = member("STUDENT");
        long student = id(post(owner, "/students", "{\"name\":\"학생\"}"));
        api.call(owner, HttpMethod.PUT, path("/students/" + student + "/account"),
                "{\"membershipId\":" + membershipOf(studentToken) + "}").andExpect(status().isOk());
        long e = id(post(owner, "/enrollments", """
                {"studentId":%d,"productId":%d,"teacherMembershipId":%d,"startsOn":"2026-10-12"}"""
                .formatted(student, countProduct, ownerMembership)));
        schedule(e, "[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"16:00\"}]").andExpect(status().isOk());
        post(owner, "/sessions/" + sessionAt(e, 0) + "/cancel", "{\"reason\":\"강사 병가\"}").andExpect(status().isOk());
        mark(sessionAt(e, 1), "EXCUSED", "{\"status\":\"EXCUSED\",\"note\":\"강사용 메모\"}").andExpect(status().isOk());
        api.call(studentToken, HttpMethod.GET, path("/students/me/sessions?from=2026-10-12&to=2026-10-31"), null)
                .andExpect(jsonPath("$[0].status").value("CANCELED"))
                .andExpect(jsonPath("$[0].note").value("강사 병가"))
                .andExpect(jsonPath("$[1].note").value(nullValue()));
    }

    // ---------- 동시성 ----------

    /** 관리자 둘이 같은 강사의 서로 다른 수강에 겹치는 일정을 동시에 저장: 쌍마다 하나만 성공, 교착·500 없음 */
    @Test
    void 겹치는_일정을_동시에_저장하면_하나만_된다() throws Exception {
        for (int round = 0; round < 20; round++) {
            long a = enroll(countProduct, ownerMembership);
            long b = enroll(countProduct, ownerMembership);
            String day = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY").get(round % 5);
            String time = "%02d:00".formatted(9 + round / 5 * 2);
            String slots = "[{\"dayOfWeek\":\"%s\",\"startTime\":\"%s\"}]".formatted(day, time);
            List<Integer> codes = concurrently(List.of(() -> code(schedule(a, slots)), () -> code(schedule(b, slots))));
            assertThat(codes).as("round " + round).containsExactlyInAnyOrder(200, 409);
        }
    }

    /** 출결로 이어 붙이는 순간 같은 강사의 다른 수강 일정을 저장해도 실패·교착이 없다 (결정 8, 리뷰 조건 3) */
    @Test
    void 출결의_자동_이어_붙이기와_일정_저장이_동시에_와도_실패하지_않는다() throws Exception {
        for (int round = 0; round < 10; round++) {
            clock.set(START.toInstant());
            long a = enroll(countProduct, ownerMembership);
            schedule(a, "[{\"dayOfWeek\":\"FRIDAY\",\"startTime\":\"%02d:00\"}]".formatted(9 + round)).andExpect(status().isOk());
            long first = sessionAt(a, 0);
            long b = enroll(countProduct, ownerMembership);
            // b는 a의 마지막 회차 다음 금요일 같은 시각(=a가 이어 붙일 자리)부터 일정을 잡는다
            String from = jdbc.queryForObject("select (max(local_date) + 7)::text from lesson_session where enrollment_id = ?",
                    String.class, a);
            String bSlots = "{\"version\":%d,\"from\":\"%s\",\"slots\":[{\"dayOfWeek\":\"FRIDAY\",\"startTime\":\"%02d:00\"}]}"
                    .formatted(version(b), from, 9 + round);
            List<Integer> codes = concurrently(List.of(
                    () -> code(mark(first, "EXCUSED")),
                    () -> code(api.call(owner, HttpMethod.PUT, path("/enrollments/" + b + "/schedule"), bSlots))));
            assertThat(codes.get(0)).as("출결은 늘 성공").isEqualTo(200);
            assertThat(codes.get(1)).as("일정은 성공하거나 겹쳐서 409").isIn(200, 409);
            assertNoOverlap();
        }
    }

    // ---------- 불변식 ----------

    /**
     * 무작위 동작 수열 200개(시드 고정). 매 단계 뒤:
     * ① 횟수권: 차감 + SCHEDULED ≤ 총 회차(부족분 ≥ 0), 다른 수강과 시간이 겹치지 않으면 부족분 = 0
     * ② 강사·원생 겹침 0 ③ 출결한 회차는 지워지지 않는다
     */
    @Test
    void 무작위_동작_수열에서도_불변식이_지켜진다() throws Exception {
        Random random = new Random(20261012);
        long a = enroll(countProduct, ownerMembership);
        long b = enroll(countProduct, ownerMembership);
        long c = enroll(periodProduct, ownerMembership);
        schedule(a, "[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"14:00\"},{\"dayOfWeek\":\"THURSDAY\",\"startTime\":\"14:00\"}]")
                .andExpect(status().isOk());
        schedule(b, "[{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"15:00\"}]").andExpect(status().isOk());
        schedule(c, "[{\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"16:00\"}]").andExpect(status().isOk());
        List<Long> all = List.of(a, b, c);
        Set<Long> marked = new HashSet<>();
        Instant now = START.toInstant();

        for (int step = 0; step < 200; step++) {
            long e = all.get(random.nextInt(all.size()));
            List<Map<String, Object>> sessions = jdbc.queryForList(
                    "select id, status, starts_at from lesson_session where enrollment_id = ? order by starts_at", e);
            int op = random.nextInt(8);
            if (op <= 2 && !sessions.isEmpty()) { // 출결 (가장 흔함)
                Map<String, Object> s = sessions.get(random.nextInt(Math.min(sessions.size(), 6)));
                String st = List.of("ATTENDED", "ABSENT", "EXCUSED").get(random.nextInt(3));
                int code = code(mark((Long) s.get("id"), st));
                if (code == 200) {
                    marked.add((Long) s.get("id"));
                }
            } else if (op == 3 && !sessions.isEmpty()) {
                Map<String, Object> s = sessions.get(random.nextInt(sessions.size()));
                code(post(owner, "/sessions/" + s.get("id") + "/cancel", "{\"reason\":\"사정\"}"));
            } else if (op == 4) {
                Instant at = now.plus(Duration.ofDays(1 + random.nextInt(20))).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
                code(post(owner, "/enrollments/" + e + "/makeups", "{\"startsAt\":\"" + at + "\"}"));
            } else if (op == 5) {
                code(e == c ? act(e, "extend", "{\"months\":1}") : act(e, "extend", "{\"sessions\":1}"));
            } else if (op == 6) {
                String state = jdbc.queryForObject("select status from enrollment where id = ?", String.class, e);
                code(act(e, "ACTIVE".equals(state) ? "pause" : "resume", null));
            } else {
                now = now.plus(Duration.ofHours(1 + random.nextInt(60)));
                clock.set(now);
            }

            assertNoOverlap();
            for (long id : marked) {
                assertThat(count("select count(*) from lesson_session where id = ?", id)).as("step %d: 출결한 회차 %d", step, id).isOne();
            }
            for (long en : List.of(a, b)) {
                Map<String, Object> row = jdbc.queryForMap("""
                        select en.total_sessions as total, en.status as status,
                               count(*) filter (where s.status in ('ATTENDED','ABSENT')) as deducted,
                               count(*) filter (where s.status = 'SCHEDULED') as scheduled
                        from enrollment en left join lesson_session s on s.enrollment_id = en.id
                        where en.id = ? group by en.id""", en);
                long total = ((Number) row.get("total")).longValue();
                long used = ((Number) row.get("deducted")).longValue() + ((Number) row.get("scheduled")).longValue();
                assertThat(used).as("step %d: 수강 %d 초과", step, en).isLessThanOrEqualTo(total);
                if ("ACTIVE".equals(row.get("status"))) {
                    // 세 수강의 요일·시각이 서로 달라 자동 이어 붙이기가 막힐 일이 없다(보강이 막는 것은 다음 슬롯으로 넘어감)
                    assertThat(used).as("step %d: 수강 %d 부족분", step, en).isEqualTo(total);
                }
            }
        }
    }

    // ---------- 도움 ----------

    private void assertNoOverlap() {
        Long overlaps = jdbc.queryForObject("""
                select count(*) from lesson_session x join lesson_session y on x.id < y.id
                  and (x.teacher_membership_id = y.teacher_membership_id or x.student_id = y.student_id)
                  and x.during && y.during
                where x.status in ('SCHEDULED','ATTENDED','ABSENT') and y.status in ('SCHEDULED','ATTENDED','ABSENT')
                  and x.organization_id = ?""", Long.class, orgId);
        assertThat(overlaps).isZero();
    }

    private long enroll(long product, long teacherMembership) throws Exception {
        long student = id(post(owner, "/students", "{\"name\":\"원생\"}"));
        return id(post(owner, "/enrollments", """
                {"studentId":%d,"productId":%d,"teacherMembershipId":%d,"startsOn":"2026-10-12"}"""
                .formatted(student, product, teacherMembership)));
    }

    private ResultActions schedule(long enrollment, String slotsJson) throws Exception {
        return api.call(owner, HttpMethod.PUT, path("/enrollments/" + enrollment + "/schedule"),
                "{\"version\":%d,\"slots\":%s}".formatted(version(enrollment), slotsJson));
    }

    private ResultActions mark(long session, String status) throws Exception {
        return mark(session, status, "{\"status\":\"" + status + "\"}");
    }

    private ResultActions mark(long session, String status, String body) throws Exception {
        return api.call(owner, HttpMethod.PUT, path("/sessions/" + session + "/attendance"), body);
    }

    private ResultActions act(long enrollment, String action, String body) throws Exception {
        String extra = body == null ? "" : "," + body.substring(1, body.length() - 1);
        return post(owner, "/enrollments/" + enrollment + "/" + action, "{\"version\":" + version(enrollment) + extra + "}");
    }

    private ResultActions enrollmentDetail(long enrollment) throws Exception {
        long student = jdbc.queryForObject("select student_id from enrollment where id = ?", Long.class, enrollment);
        return api.call(owner, HttpMethod.GET, path("/students/" + student), null);
    }

    private long sessionAt(long enrollment, int index) {
        return jdbc.queryForList("select id from lesson_session where enrollment_id = ? order by starts_at", Long.class, enrollment)
                .get(index);
    }

    private long version(long enrollment) {
        return jdbc.queryForObject("select version from enrollment where id = ?", Long.class, enrollment);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private ResultActions post(String token, String rest, String body) throws Exception {
        return api.call(token, HttpMethod.POST, path(rest), body);
    }

    private String path(String rest) {
        return "/api/v1/organizations/" + orgId + "/academy" + rest;
    }

    private static long id(ResultActions r) throws Exception {
        Number n = ApiClient.read(r, "$.id");
        return n.longValue();
    }

    private static int code(ResultActions r) {
        int code = r.andReturn().getResponse().getStatus();
        assertThat(code).as("500이나 교착(TRY_AGAIN)이 아니어야 한다").isLessThan(500);
        return code;
    }

    private String member(String role) throws Exception {
        String token = ApiClient.read(api.call(owner, HttpMethod.POST, "/api/v1/organizations/" + orgId + "/invitations",
                "{\"role\":\"%s\"}".formatted(role)), "$.token");
        String user = api.newUser();
        api.call(user, HttpMethod.POST, "/api/v1/invitations/accept", "{\"token\":\"%s\"}".formatted(token))
                .andExpect(status().isOk());
        return user;
    }

    private long membershipOf(String token) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        long userId = Long.parseLong(payload.replaceAll(".*\"sub\":\"(\\d+)\".*", "$1"));
        return jdbc.queryForObject("select id from membership where organization_id = ? and user_id = ?", Long.class,
                orgId, userId);
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
