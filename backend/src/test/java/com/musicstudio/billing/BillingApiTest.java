package com.musicstudio.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.musicstudio.support.ApiClient;
import com.musicstudio.support.IntegrationTest;

/** 수납 (UC-60~64, API 56~63): 자동 청구서, 금액 정합성, 동시 입금, 멱등, 취소, 권한, 불변식. */
@IntegrationTest
@AutoConfigureMockMvc
class BillingApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    ApiClient api;
    String owner;
    long orgId;
    long product;
    long student;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        owner = api.newUser();
        orgId = api.newOrganization(owner);
        api.call(owner, HttpMethod.PATCH, "/api/v1/organizations/" + orgId,
                "{\"modules\":[\"PRACTICE_ROOM\",\"ACADEMY\",\"BILLING\"]}").andExpect(status().isOk());
        long subject = id(academy("/subjects", "{\"name\":\"피아노\"}"));
        product = id(academy("/products", """
                {"subjectId":%d,"name":"3개월","kind":"PERIOD","periodMonths":3,"lessonMinutes":50,"price":300000}"""
                .formatted(subject)));
        student = id(academy("/students", "{\"name\":\"원생\"}"));
    }

    // ---------- 청구서 (56, 자동) ----------

    @Test
    void 수강을_등록하면_그_금액으로_청구서가_하나_생긴다() throws Exception {
        long enrollment = enroll(product);
        api.call(owner, HttpMethod.GET, billing("/invoices"), null)
                .andExpect(jsonPath("$.invoices", hasSize(1)))
                .andExpect(jsonPath("$.invoices[0].enrollmentId").value(enrollment))
                .andExpect(jsonPath("$.invoices[0].title").value("피아노 · 3개월"))
                .andExpect(jsonPath("$.invoices[0].amount").value(300000))
                .andExpect(jsonPath("$.invoices[0].state").value("UNPAID"))
                .andExpect(jsonPath("$.outstanding").value(300000));
    }

    @Test
    void 수납을_끈_기관과_0원_상품은_청구서_없이_수강이_등록된다() throws Exception {
        long subject = jdbc.queryForObject("select id from subject where organization_id = ?", Long.class, orgId);
        long free = id(academy("/products", """
                {"subjectId":%d,"name":"체험","kind":"COUNT","sessionCount":1,"lessonMinutes":30,"price":0}""".formatted(subject)));
        enroll(free);
        assertThat(count("select count(*) from invoice where organization_id = ?", orgId)).isZero();

        api.call(owner, HttpMethod.PATCH, "/api/v1/organizations/" + orgId, "{\"modules\":[\"PRACTICE_ROOM\",\"ACADEMY\"]}")
                .andExpect(status().isOk());
        enroll(product);
        assertThat(count("select count(*) from invoice where organization_id = ?", orgId)).isZero();
        api.call(owner, HttpMethod.GET, billing("/invoices"), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MODULE_DISABLED"));
    }

    @Test
    void 청구_결제는_학원_관리_없이_켤_수_없다() throws Exception {
        api.call(owner, HttpMethod.PATCH, "/api/v1/organizations/" + orgId, "{\"modules\":[\"BILLING\"]}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("BILLING_NEEDS_ACADEMY"));
        // 만들 때도 같은 규칙 (리뷰 31 1-1), DB CHECK가 마지막으로 막는다
        api.call(owner, HttpMethod.POST, "/api/v1/organizations",
                        "{\"name\":\"학교\",\"type\":\"SCHOOL\",\"timezone\":\"Asia/Seoul\",\"modules\":[\"BILLING\"]}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("BILLING_NEEDS_ACADEMY"));
        assertThatThrownBy(() -> jdbc.update("update organization set modules = '{BILLING}' where id = ?", orgId))
                .hasMessageContaining("ck_organization_billing_needs_academy");
    }

    @Test
    void 지난_날짜로_등록한_수강의_자동_청구서는_기한이_오늘이다() throws Exception {
        long enrollment = id(academy("/enrollments", """
                {"studentId":%d,"productId":%d,"teacherMembershipId":%d,"startsOn":"2020-01-01"}"""
                .formatted(student, product, ownerMembership())));
        api.call(owner, HttpMethod.GET, billing("/invoices"), null)
                .andExpect(jsonPath("$.invoices[0].enrollmentId").value(enrollment))
                .andExpect(jsonPath("$.invoices[0].overdue").value(false));
    }

    // ---------- 입금·환불 (60) ----------

    @Test
    void 부분_납부와_완납과_초과와_환불() throws Exception {
        long invoice = invoice(100000);
        api.call(owner, HttpMethod.GET, billing("/invoices/" + invoice), null)
                .andExpect(jsonPath("$.invoice.enrollmentId").doesNotExist()); // 직접 만든 청구서는 수강 없음(0이 아니라 null)
        pay(invoice, "PAYMENT", 40000, UUID.randomUUID()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoice.state").value("PARTIAL")).andExpect(jsonPath("$.invoice.paid").value(40000));
        pay(invoice, "PAYMENT", 60001, UUID.randomUUID())
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("OVERPAID"))
                .andExpect(jsonPath("$.balance").value(60000));
        pay(invoice, "PAYMENT", 60000, UUID.randomUUID()).andExpect(jsonPath("$.invoice.state").value("PAID"));
        pay(invoice, "REFUND", 100001, UUID.randomUUID()).andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_PAID"));
        pay(invoice, "REFUND", 30000, UUID.randomUUID()).andExpect(jsonPath("$.invoice.state").value("PARTIAL"))
                .andExpect(jsonPath("$.invoice.balance").value(30000));
        assertThat(count("select count(*) from payment where invoice_id = ?", invoice)).isEqualTo(3); // 실패한 것은 남지 않는다
    }

    /** 남은 금액이 10만 원인 청구서에 10만 원 입금 20건 동시: 하나만 된다 (판단과 쓰기가 한 문장) */
    @Test
    void 동시에_입금해도_초과_납부가_생기지_않는다() throws Exception {
        long invoice = invoice(100000);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> code(pay(invoice, "PAYMENT", 100000, UUID.randomUUID())));
        }
        List<Integer> codes = concurrently(tasks);
        assertThat(codes.stream().filter(c -> c == 201)).hasSize(1);
        assertThat(codes.stream().filter(c -> c == 422)).hasSize(19);
        assertThat(paid(invoice)).isEqualTo(100000);
        assertThat(count("select count(*) from payment where invoice_id = ?", invoice)).isOne();
    }

    @Test
    void DB가_초과_납부를_마지막으로_막는다() {
        long invoice;
        try {
            invoice = invoice(100000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertThatThrownBy(() -> jdbc.update("update invoice set paid_amount = amount + 1 where id = ?", invoice))
                .hasMessageContaining("ck_invoice_paid");
    }

    // ---------- 멱등 ----------

    @Test
    void 같은_요청_번호는_한_번만_기록한다() throws Exception {
        long invoice = invoice(100000);
        UUID key = UUID.randomUUID();
        Number first = ApiClient.read(pay(invoice, "PAYMENT", 30000, key).andExpect(status().isCreated()), "$.payment.id");
        Number again = ApiClient.read(pay(invoice, "PAYMENT", 30000, key).andExpect(status().isOk()), "$.payment.id");
        assertThat(again.longValue()).isEqualTo(first.longValue());
        pay(invoice, "PAYMENT", 31000, key)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(paid(invoice)).isEqualTo(30000);
    }

    @Test
    void 같은_요청_번호_10건이_동시에_와도_한_번만_반영된다() throws Exception {
        long invoice = invoice(100000);
        UUID key = UUID.randomUUID();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> code(pay(invoice, "PAYMENT", 30000, key)));
        }
        List<Integer> codes = concurrently(tasks);
        assertThat(codes).containsOnly(200, 201);
        assertThat(codes.stream().filter(c -> c == 201)).hasSize(1);
        assertThat(count("select count(*) from payment where invoice_id = ?", invoice)).isOne();
        assertThat(paid(invoice)).isEqualTo(30000);
    }

    @Test
    void 실패한_요청의_번호로_금액을_고쳐_다시_보내면_정상_처리된다() throws Exception {
        long invoice = invoice(100000);
        UUID key = UUID.randomUUID();
        pay(invoice, "PAYMENT", 200000, key).andExpect(status().isUnprocessableContent()); // 롤백되어 기록이 없다
        pay(invoice, "PAYMENT", 100000, key).andExpect(status().isCreated());
    }

    // ---------- 취소 (59, 61) ----------

    @Test
    void 잘못_적은_입금은_취소하고_청구서는_납부가_없어야_무효로_한다() throws Exception {
        long invoice = invoice(100000);
        Number payment = ApiClient.read(pay(invoice, "PAYMENT", 50000, UUID.randomUUID()), "$.payment.id");
        post("/invoices/" + invoice + "/void", "{\"reason\":\"잘못 만듦\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HAS_PAYMENTS"));
        Number refund = ApiClient.read(pay(invoice, "REFUND", 20000, UUID.randomUUID()), "$.payment.id");
        post("/payments/" + payment + "/void", "{\"reason\":\"금액 잘못 적음\"}")
                .andExpect(jsonPath("$.code").value("REFUND_FIRST")); // 50,000 − 20,000 = 30,000에서 50,000을 뺄 수 없다
        post("/payments/" + refund + "/void", "{\"reason\":\"착오\"}").andExpect(status().isOk());
        post("/payments/" + payment + "/void", "{\"reason\":\"금액 잘못 적음\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.invoice.paid").value(0));
        post("/payments/" + payment + "/void", "{\"reason\":\"다시\"}").andExpect(jsonPath("$.code").value("ALREADY_VOID"));
        post("/invoices/" + invoice + "/void", "{\"reason\":\"잘못 만듦\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("VOID"));
        pay(invoice, "PAYMENT", 1000, UUID.randomUUID()).andExpect(jsonPath("$.code").value("INVOICE_VOID"));
        api.call(owner, HttpMethod.GET, billing("/invoices/" + invoice), null)
                .andExpect(jsonPath("$.payments", hasSize(2))) // 장부는 지우지 않는다
                .andExpect(jsonPath("$.payments[0].voided").value(true));
    }

    // ---------- 영수증, 권한 (62, 63) ----------

    @Test
    void 강사는_돈을_보지_못하고_학생은_자기_청구서만_본다() throws Exception {
        long invoice = invoice(100000);
        Number payment = ApiClient.read(pay(invoice, "PAYMENT", 100000, UUID.randomUUID()), "$.payment.id");
        api.call(owner, HttpMethod.GET, billing("/payments/" + payment + "/receipt"), null)
                .andExpect(jsonPath("$.number").value("R-" + payment)).andExpect(jsonPath("$.amount").value(100000));

        api.call(member("TEACHER"), HttpMethod.GET, billing("/invoices"), null).andExpect(status().isForbidden());

        String studentToken = member("STUDENT");
        long membership = jdbc.queryForObject(
                "select id from membership where organization_id = ? and role = 'STUDENT'", Long.class, orgId);
        api.call(owner, HttpMethod.PUT, "/api/v1/organizations/" + orgId + "/academy/students/" + student + "/account",
                "{\"membershipId\":" + membership + "}").andExpect(status().isOk());
        long other = id(academy("/students", "{\"name\":\"다른 원생\"}"));
        api.call(owner, HttpMethod.POST, billing("/invoices"),
                "{\"studentId\":%d,\"title\":\"교재비\",\"amount\":20000,\"dueDate\":\"2026-10-31\"}".formatted(other));
        api.call(studentToken, HttpMethod.GET, billing("/me/invoices"), null)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].state").value("PAID"))
                .andExpect(jsonPath("$[0].payments[0].amount").value(100000))
                .andExpect(jsonPath("$[0].payments[0].memo").doesNotExist());
        api.call(studentToken, HttpMethod.GET, billing("/invoices"), null).andExpect(status().isForbidden());
    }

    // ---------- 불변식 ----------

    /** 무작위 동작 200개: 매 단계 paid_amount = Σ(취소 안 된 입금) − Σ(취소 안 된 환불), 0 ≤ paid ≤ amount */
    @Test
    void 무작위_동작_수열에서도_장부와_납부액이_맞는다() throws Exception {
        Random random = new Random(20261006);
        List<Long> invoices = List.of(invoice(100000), invoice(50000), invoice(30000));
        for (int step = 0; step < 200; step++) {
            long invoice = invoices.get(random.nextInt(invoices.size()));
            int op = random.nextInt(4);
            if (op <= 1) {
                code(pay(invoice, op == 0 ? "PAYMENT" : "REFUND", 1000L * (1 + random.nextInt(60)), UUID.randomUUID()));
            } else if (op == 2) {
                List<Long> live = jdbc.queryForList("select id from payment where invoice_id = ? and voided_at is null", Long.class, invoice);
                if (!live.isEmpty()) {
                    code(post("/payments/" + live.get(random.nextInt(live.size())) + "/void", "{\"reason\":\"테스트\"}"));
                }
            } else {
                UUID key = UUID.randomUUID(); // 같은 요청 두 번
                code(pay(invoice, "PAYMENT", 5000, key));
                code(pay(invoice, "PAYMENT", 5000, key));
            }
            for (long id : invoices) {
                Map<String, Object> row = jdbc.queryForMap("""
                        select i.amount, i.paid_amount,
                               coalesce(sum(case when p.kind = 'PAYMENT' then p.amount else -p.amount end)
                                        filter (where p.voided_at is null), 0) as ledger
                        from invoice i left join payment p on p.invoice_id = i.id where i.id = ? group by i.id""", id);
                long paidAmount = ((Number) row.get("paid_amount")).longValue();
                assertThat(paidAmount).as("step %d invoice %d", step, id).isEqualTo(((Number) row.get("ledger")).longValue());
                assertThat(paidAmount).isBetween(0L, ((Number) row.get("amount")).longValue());
            }
        }
    }

    // ---------- 도움 ----------

    private long enroll(long productId) throws Exception {
        return id(academy("/enrollments", """
                {"studentId":%d,"productId":%d,"teacherMembershipId":%d,"startsOn":"2026-10-12"}"""
                .formatted(student, productId, ownerMembership())));
    }

    private long ownerMembership() {
        return jdbc.queryForObject("select id from membership where organization_id = ? and role = 'OWNER'", Long.class, orgId);
    }

    private long invoice(long amount) throws Exception {
        return id(api.call(owner, HttpMethod.POST, billing("/invoices"),
                "{\"studentId\":%d,\"title\":\"수강료\",\"amount\":%d,\"dueDate\":\"2026-10-31\"}".formatted(student, amount))
                .andExpect(status().isCreated()));
    }

    private ResultActions pay(long invoice, String kind, long amount, UUID key) throws Exception {
        return post("/invoices/" + invoice + "/payments", """
                {"requestId":"%s","kind":"%s","method":"TRANSFER","amount":%d,"paidOn":"2026-10-06"}"""
                .formatted(key, kind, amount));
    }

    private ResultActions post(String rest, String body) throws Exception {
        return api.call(owner, HttpMethod.POST, billing(rest), body);
    }

    private ResultActions academy(String rest, String body) throws Exception {
        return api.call(owner, HttpMethod.POST, "/api/v1/organizations/" + orgId + "/academy" + rest, body);
    }

    private String billing(String rest) {
        return "/api/v1/organizations/" + orgId + "/billing" + rest;
    }

    private long paid(long invoice) {
        return jdbc.queryForObject("select paid_amount from invoice where id = ?", Long.class, invoice);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static long id(ResultActions r) throws Exception {
        Number n = ApiClient.read(r, "$.id");
        return n.longValue();
    }

    private static int code(ResultActions r) {
        int c = r.andReturn().getResponse().getStatus();
        assertThat(c).as("500이 아니어야 한다").isLessThan(500);
        return c;
    }

    private String member(String role) throws Exception {
        String token = ApiClient.read(api.call(owner, HttpMethod.POST, "/api/v1/organizations/" + orgId + "/invitations",
                "{\"role\":\"%s\"}".formatted(role)), "$.token");
        String user = api.newUser();
        api.call(user, HttpMethod.POST, "/api/v1/invitations/accept", "{\"token\":\"%s\"}".formatted(token))
                .andExpect(status().isOk());
        return user;
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
