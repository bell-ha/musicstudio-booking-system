package com.musicstudio.practice;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.musicstudio.support.ApiClient;
import com.musicstudio.support.IntegrationTest;

/** 층·평면도·방·정책 (UC-20, 21, 22, API 13~18). */
@IntegrationTest
@AutoConfigureMockMvc
class FloorPlanApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    ApiClient api;
    String owner;
    long orgId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        owner = api.newUser();
        orgId = api.newOrganization(owner);
    }

    @Test
    void 정책을_저장한_적이_없으면_기본_정책을_준다() throws Exception {
        api.call(owner, HttpMethod.GET, path("/floors"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.slotMinutes").value(30))
                .andExpect(jsonPath("$.policy.open.mode").value("ROLLING"))
                .andExpect(jsonPath("$.floors", hasSize(0)));
    }

    @Test
    void 방을_만들어_평면도에_놓으면_버전이_오르고_지도에_보인다() throws Exception {
        long a = room("A101");
        long b = room("A102");
        long floor = floor("1층", 6, 4);

        saveFloor(floor, 0, """
                {"name":"1층","sortOrder":0,
                 "layout":{"width":6,"height":4,"walls":[[0,3],[1,3]],"corridors":[[2,3]]},
                 "rooms":[{"id":%d,"x":0,"y":0,"w":2,"h":2},{"id":%d,"x":2,"y":0,"w":2,"h":2}]}""".formatted(a, b))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.rooms", hasSize(2)));

        api.call(owner, HttpMethod.GET, path("/floors"), null)
                .andExpect(jsonPath("$.floors[0].version").value(1))
                .andExpect(jsonPath("$.floors[0].layout.walls", hasSize(2)))
                .andExpect(jsonPath("$.unplacedRooms", hasSize(0)));
    }

    @Test
    void If_Match가_없으면_428_낡은_버전이면_412() throws Exception {
        long floor = floor("1층", 4, 4);
        String body = "{\"name\":\"1층\",\"sortOrder\":0,\"layout\":{\"width\":4,\"height\":4},\"rooms\":[]}";

        api.call(owner, HttpMethod.PUT, path("/floors/" + floor), body)
                .andExpect(status().isPreconditionRequired());

        saveFloor(floor, 0, body).andExpect(status().isOk());
        saveFloor(floor, 0, body)
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.currentVersion").value(1));
    }

    @Test
    void 방이_벽과_겹치거나_격자_밖이면_422() throws Exception {
        long a = room("A101");
        long floor = floor("1층", 4, 4);

        saveFloor(floor, 0, """
                {"name":"1층","sortOrder":0,"layout":{"width":4,"height":4,"walls":[[1,1]]},
                 "rooms":[{"id":%d,"x":0,"y":0,"w":2,"h":2}]}""".formatted(a))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LAYOUT_INVALID"));
        saveFloor(floor, 0, """
                {"name":"1층","sortOrder":0,"layout":{"width":4,"height":4},
                 "rooms":[{"id":%d,"x":3,"y":3,"w":2,"h":1}]}""".formatted(a))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void 앞으로의_예약이_있는_방은_평면도에서_뺄_수_없다() throws Exception {
        long a = room("A101");
        long floor = floor("1층", 4, 4);
        saveFloor(floor, 0, """
                {"name":"1층","sortOrder":0,"layout":{"width":4,"height":4},
                 "rooms":[{"id":%d,"x":0,"y":0,"w":1,"h":1}]}""".formatted(a)).andExpect(status().isOk());
        Long memberId = jdbc.queryForObject("select id from membership where organization_id = ?", Long.class, orgId);
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        jdbc.update("""
                insert into practice_booking (organization_id, room_id, member_id, starts_at, ends_at, usage_date)
                values (?, ?, ?, ?, ?, current_date + 1)""",
                orgId, a, memberId, java.sql.Timestamp.from(start), java.sql.Timestamp.from(start.plusSeconds(3600)));

        saveFloor(floor, 1, "{\"name\":\"1층\",\"sortOrder\":0,\"layout\":{\"width\":4,\"height\":4},\"rooms\":[]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_HAS_FUTURE_BOOKINGS"))
                .andExpect(jsonPath("$.bookings[0].roomName").value("A101"));
    }

    @Test
    void 같은_이름의_방은_만들_수_없다() throws Exception {
        room("A101");
        api.call(owner, HttpMethod.POST, path("/rooms"), "{\"name\":\"A101\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROOM_NAME_TAKEN"));
    }

    @Test
    void 정책을_검증하고_저장한다() throws Exception {
        api.call(owner, HttpMethod.PUT, path("/policy"), policy(45, "{\"mode\":\"ROLLING\",\"days\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_POLICY"))
                .andExpect(jsonPath("$.errors[?(@.field == 'slotMinutes')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'open.days')]").exists());

        api.call(owner, HttpMethod.PUT, path("/policy"),
                        policy(60, "{\"mode\":\"WEEKLY\",\"dayOfWeek\":\"FRIDAY\",\"time\":\"09:00\"}"))
                .andExpect(status().isOk());
        api.call(owner, HttpMethod.GET, path("/floors"), null)
                .andExpect(jsonPath("$.policy.slotMinutes").value(60))
                .andExpect(jsonPath("$.policy.open.mode").value("WEEKLY"));
    }

    @Test
    void 학생은_층을_볼_수_있지만_방을_만들_수_없다() throws Exception {
        String token = ApiClient.read(api.call(owner, HttpMethod.POST,
                "/api/v1/organizations/" + orgId + "/invitations", "{\"role\":\"STUDENT\"}"), "$.token");
        String student = api.newUser();
        api.call(student, HttpMethod.POST, "/api/v1/invitations/accept", "{\"token\":\"%s\"}".formatted(token))
                .andExpect(status().isOk());

        api.call(student, HttpMethod.GET, path("/floors"), null).andExpect(status().isOk());
        api.call(student, HttpMethod.POST, path("/rooms"), "{\"name\":\"B1\"}").andExpect(status().isForbidden());
    }

    @Test
    void 연습실_모듈을_끈_기관은_연습실_API를_쓸_수_없다() throws Exception {
        Number academyOnly = ApiClient.read(api.call(owner, HttpMethod.POST, "/api/v1/organizations",
                "{\"name\":\"학원\",\"type\":\"ACADEMY\",\"timezone\":\"Asia/Seoul\",\"modules\":[\"ACADEMY\"]}"), "$.id");

        api.call(owner, HttpMethod.GET, "/api/v1/organizations/" + academyOnly + "/practice/floors", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
    }

    // ---------- 도우미 ----------

    private String path(String rest) {
        return "/api/v1/organizations/" + orgId + "/practice" + rest;
    }

    private long room(String name) throws Exception {
        Number id = ApiClient.read(api.call(owner, HttpMethod.POST, path("/rooms"), "{\"name\":\"%s\"}".formatted(name))
                .andExpect(status().isCreated()), "$.id");
        return id.longValue();
    }

    private long floor(String name, int w, int h) throws Exception {
        Number id = ApiClient.read(api.call(owner, HttpMethod.POST, path("/floors"),
                "{\"name\":\"%s\",\"width\":%d,\"height\":%d}".formatted(name, w, h)).andExpect(status().isCreated()), "$.id");
        return id.longValue();
    }

    private ResultActions saveFloor(long floorId, long version, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put(path("/floors/" + floorId))
                .header("Authorization", "Bearer " + owner)
                .header("If-Match", "\"" + version + "\"")
                .contentType("application/json").content(body));
    }

    private static String policy(int slot, String open) {
        String hours = "{\"MONDAY\":[\"09:00\",\"22:00\"],\"TUESDAY\":[\"09:00\",\"22:00\"],\"WEDNESDAY\":[\"09:00\",\"22:00\"],"
                + "\"THURSDAY\":[\"09:00\",\"22:00\"],\"FRIDAY\":[\"09:00\",\"22:00\"],\"SATURDAY\":[\"10:00\",\"18:00\"],\"SUNDAY\":null}";
        return "{\"hours\":%s,\"slotMinutes\":%d,\"maxContinuousMinutes\":120,\"dailyMaxMinutes\":180,\"open\":%s,\"cancelDeadlineMinutes\":10}"
                .formatted(hours, slot, open);
    }
}
