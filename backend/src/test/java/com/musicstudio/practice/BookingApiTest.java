package com.musicstudio.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.musicstudio.support.ApiClient;
import com.musicstudio.support.IntegrationTest;

/**
 * 연습실 예약 (UC-23~25, API 19~23). 대표 문제 1의 동시성 테스트 포함.
 * 지금 시각을 2026-10-09(금) 08:00 서울로 고정한다. 기본 정책은 09~22시, 30분 단위, 1회 120분, 하루 180분, 14일.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(BookingApiTest.FixedClock.class)
class BookingApiTest {

    static final String DAY = "2026-10-09";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(OffsetDateTime.parse(DAY + "T08:00:00+09:00").toInstant(), ZoneId.of("UTC"));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    ApiClient api;
    String owner;
    long orgId;
    long roomA;
    long roomB;
    long floorId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        owner = api.newUser();
        orgId = api.newOrganization(owner);
        roomA = room("A101");
        roomB = room("A102");
        Number floor = ApiClient.read(api.call(owner, HttpMethod.POST, path("/floors"),
                "{\"name\":\"1층\",\"width\":4,\"height\":2}"), "$.id");
        floorId = floor.longValue();
        mvc.perform(MockMvcRequestBuilders.put(path("/floors/" + floorId)).header("Authorization", "Bearer " + owner)
                .header("If-Match", "\"0\"").contentType("application/json").content("""
                        {"name":"1층","sortOrder":0,"layout":{"width":4,"height":2},
                         "rooms":[{"id":%d,"x":0,"y":0,"w":2,"h":2},{"id":%d,"x":2,"y":0,"w":2,"h":2}]}"""
                        .formatted(roomA, roomB))).andExpect(status().isOk());
    }

    @Test
    void 빈_시간을_예약하면_시간표와_지도에_내_예약으로_보인다() throws Exception {
        String student = student();
        book(student, roomA, "14:00", "15:30").andExpect(status().isCreated())
                .andExpect(jsonPath("$.startsAt").value(DAY + "T14:00:00+09:00"))
                .andExpect(jsonPath("$.usageDate").value(DAY));

        api.call(student, HttpMethod.GET, path("/rooms/" + roomA + "/timetable?date=" + DAY), null)
                .andExpect(jsonPath("$.slots", hasSize(26)))
                .andExpect(jsonPath("$.slots[10].status").value("BOOKED"))
                .andExpect(jsonPath("$.slots[10].mine").value(true))
                .andExpect(jsonPath("$.slots[13].status").value("AVAILABLE"));

        String other = student();
        api.call(other, HttpMethod.GET, path("/floors/" + floorId + "/availability?at=" + DAY + "T05:30:00Z"), null)
                .andExpect(jsonPath("$.rooms[?(@.roomId == " + roomA + ")].status").value("BOOKED"))
                .andExpect(jsonPath("$.rooms[?(@.roomId == " + roomA + ")].mine").value(false))
                .andExpect(jsonPath("$.rooms[?(@.roomId == " + roomB + ")].status").value("AVAILABLE"));
    }

    @Test
    void 같은_방_겹치는_시간은_409_ROOM_OVERLAP() throws Exception {
        book(student(), roomA, "14:00", "15:00").andExpect(status().isCreated());
        book(student(), roomA, "14:30", "15:30")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROOM_OVERLAP"));
        // 끝과 시작이 맞닿은 것은 겹치지 않는다
        book(student(), roomA, "15:00", "16:00").andExpect(status().isCreated());
    }

    @Test
    void 같은_사람이_같은_시간에_다른_방을_잡으면_409와_겹친_예약() throws Exception {
        String student = student();
        book(student, roomA, "14:00", "15:00").andExpect(status().isCreated());
        book(student, roomB, "14:30", "15:30")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERSON_OVERLAP"))
                .andExpect(jsonPath("$.conflictingBooking.roomId").value(roomA))
                .andExpect(jsonPath("$.conflictingBooking.startsAt").value(DAY + "T14:00:00+09:00"));
    }

    @Test
    void 정책_위반은_422와_규칙_코드() throws Exception {
        String student = student();
        book(student, roomA, "14:00", "16:30").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MAX_CONTINUOUS_EXCEEDED"));
        book(student, roomA, "14:10", "15:00").andExpect(jsonPath("$.code").value("SLOT_MISALIGNED"));
        book(student, roomA, "21:30", "22:30").andExpect(jsonPath("$.code").value("OUTSIDE_OPERATING_HOURS"));
        api.call(student, HttpMethod.POST, path("/bookings"),
                        "{\"roomId\":%d,\"startsAt\":\"2026-10-30T14:00:00+09:00\",\"endsAt\":\"2026-10-30T15:00:00+09:00\"}"
                                .formatted(roomA))
                .andExpect(jsonPath("$.code").value("NOT_OPEN_YET"));

        book(student, roomA, "10:00", "12:00").andExpect(status().isCreated());
        book(student, roomB, "13:00", "14:30")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.usedMinutes").value(120));
    }

    @Test
    void 점검_중인_방과_학생이_아닌_사람의_예약은_막힌다() throws Exception {
        api.call(owner, HttpMethod.PATCH, path("/rooms/" + roomB), "{\"bookable\":false}").andExpect(status().isOk());
        book(student(), roomB, "14:00", "15:00").andExpect(jsonPath("$.code").value("ROOM_NOT_BOOKABLE"));
        book(owner, roomA, "14:00", "15:00").andExpect(status().isForbidden());
    }

    @Test
    void 같은_방_같은_시간에_20명이_동시에_예약하면_1명만_성공한다() throws Exception {
        List<String> students = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            students.add(student());
        }
        List<Integer> statuses = concurrently(students.stream()
                .map(s -> (Callable<Integer>) () -> httpStatus(book(s, roomA, "18:00", "19:00")))
                .toList());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(19);
    }

    @Test
    void 한_사람의_동시_예약_두_건이_각자_하루_한도를_통과하지_못한다() throws Exception {
        // 120분 + 120분 = 240분 > 하루 180분. 겹치지 않는 두 방이라 배타 제약으로는 못 막는 write skew다.
        // 회차마다 다른 날을 써서 앞 회차의 예약과 부딪히지 않게 한다.
        for (int round = 0; round < 5; round++) {
            String student = student();
            String day = java.time.LocalDate.parse(DAY).plusDays(round).toString();
            List<Integer> statuses = concurrently(List.of(
                    () -> httpStatus(book(student, roomA, day, "10:00", "12:00")),
                    () -> httpStatus(book(student, roomB, day, "13:00", "15:00"))));

            assertThat(statuses).containsExactlyInAnyOrder(201, 422);
        }
    }

    @Test
    void 본인은_마감_전까지_관리자는_사유를_적어_취소한다() throws Exception {
        String student = student();
        Number mine = ApiClient.read(book(student, roomA, "14:00", "15:00"), "$.id");
        api.call(student, HttpMethod.POST, path("/bookings/" + mine + "/cancel"), null).andExpect(status().isOk());
        api.call(student, HttpMethod.POST, path("/bookings/" + mine + "/cancel"), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_CANCELED"));

        Number second = ApiClient.read(book(student, roomA, "16:00", "17:00"), "$.id");
        api.call(owner, HttpMethod.POST, path("/bookings/" + second + "/cancel"), "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        api.call(owner, HttpMethod.POST, path("/bookings/" + second + "/cancel"), "{\"reason\":\"공연 리허설\"}")
                .andExpect(status().isOk());

        api.call(student, HttpMethod.GET, path("/bookings?from=" + DAY + "&to=" + DAY), null)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].cancelReason").value("공연 리허설"));
        api.call(owner, HttpMethod.GET, path("/bookings?date=" + DAY), null)
                .andExpect(jsonPath("$[0].memberName").value("테스트"));
        api.call(student, HttpMethod.GET, path("/bookings?date=" + DAY), null).andExpect(status().isForbidden());
    }

    @Test
    void 학생과_관리자가_동시에_취소하면_한_건만_반영된다() throws Exception {
        for (int round = 0; round < 5; round++) {
            String student = student();
            String day = LocalDate.parse(DAY).plusDays(round).toString();
            Number id = ApiClient.read(book(student, roomA, day, "20:00", "21:00"), "$.id");
            List<Integer> statuses = concurrently(List.of(
                    () -> httpStatus(api.call(student, HttpMethod.POST, path("/bookings/" + id + "/cancel"), null)),
                    () -> httpStatus(api.call(owner, HttpMethod.POST, path("/bookings/" + id + "/cancel"),
                            "{\"reason\":\"점검\"}"))));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test
    void 이미_끝난_예약은_관리자도_취소할_수_없다() throws Exception {
        student();
        Long memberId = jdbc.queryForObject(
                "select max(id) from membership where organization_id = ? and role = 'STUDENT'", Long.class, orgId);
        Long id = jdbc.queryForObject(
                "insert into practice_booking (organization_id, room_id, member_id, starts_at, ends_at, usage_date) "
                        + "values (?, ?, ?, '2026-10-08 10:00+09', '2026-10-08 11:00+09', '2026-10-08') returning id",
                Long.class, orgId, roomA, memberId);

        api.call(owner, HttpMethod.POST, path("/bookings/" + id + "/cancel"), "{\"reason\":\"정리\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BOOKING_ENDED"));
    }

    @Test
    void 내_예약_조회_기간은_92일까지() throws Exception {
        api.call(student(), HttpMethod.GET, path("/bookings?from=2026-01-01&to=2026-12-31"), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    }

    @Test
    void 취소_마감이_지나면_본인은_취소할_수_없다() throws Exception {
        api.call(owner, HttpMethod.PUT, path("/policy"), """
                {"hours":{"MONDAY":["09:00","22:00"],"TUESDAY":["09:00","22:00"],"WEDNESDAY":["09:00","22:00"],
                 "THURSDAY":["09:00","22:00"],"FRIDAY":["09:00","22:00"],"SATURDAY":["09:00","22:00"],"SUNDAY":["09:00","22:00"]},
                 "slotMinutes":30,"maxContinuousMinutes":120,"dailyMaxMinutes":180,
                 "open":{"mode":"ROLLING","days":14},"cancelDeadlineMinutes":600}""").andExpect(status().isOk());
        String student = student();
        Number id = ApiClient.read(book(student, roomA, "14:00", "15:00"), "$.id");

        api.call(student, HttpMethod.POST, path("/bookings/" + id + "/cancel"), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CANCEL_DEADLINE_PASSED"));
    }

    // ---------- 도우미 ----------

    private String path(String rest) {
        return "/api/v1/organizations/" + orgId + "/practice" + rest;
    }

    private long room(String name) throws Exception {
        Number id = ApiClient.read(api.call(owner, HttpMethod.POST, path("/rooms"), "{\"name\":\"%s\"}".formatted(name)), "$.id");
        return id.longValue();
    }

    /** 초대 링크로 들어온 새 학생. */
    private String student() throws Exception {
        String token = ApiClient.read(api.call(owner, HttpMethod.POST, "/api/v1/organizations/" + orgId + "/invitations",
                "{\"role\":\"STUDENT\"}"), "$.token");
        String user = api.newUser();
        api.call(user, HttpMethod.POST, "/api/v1/invitations/accept", "{\"token\":\"%s\"}".formatted(token))
                .andExpect(status().isOk());
        return user;
    }

    private ResultActions book(String token, long roomId, String from, String to) throws Exception {
        return book(token, roomId, DAY, from, to);
    }

    private ResultActions book(String token, long roomId, String day, String from, String to) throws Exception {
        return api.call(token, HttpMethod.POST, path("/bookings"),
                "{\"roomId\":%d,\"startsAt\":\"%sT%s:00+09:00\",\"endsAt\":\"%sT%s:00+09:00\"}"
                        .formatted(roomId, day, from, day, to));
    }

    private static int httpStatus(ResultActions result) {
        return result.andReturn().getResponse().getStatus();
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
