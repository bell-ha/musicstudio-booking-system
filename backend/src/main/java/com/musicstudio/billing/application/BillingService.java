package com.musicstudio.billing.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.academy.domain.Enrollment;
import com.musicstudio.academy.domain.EnrollmentRepository;
import com.musicstudio.academy.domain.Student;
import com.musicstudio.academy.domain.StudentRepository;
import com.musicstudio.billing.domain.InvoiceState;
import com.musicstudio.common.error.ApiException;
import com.musicstudio.organization.domain.OrganizationRepository;

/**
 * 수납 (UC-60~64). 설계는 두 세션 합본(비공개 계획 문서 30).
 *
 * <p>금액 정합성은 잠금이 아니라 <b>조건부 UPDATE + CHECK</b>로 지킨다.
 * "남은 금액 이하인가"를 읽고 나서 쓰면 두 요청이 같은 값을 읽고 둘 다 써서 초과 납부가 된다.
 * {@code UPDATE … SET paid_amount = paid_amount + x WHERE … AND paid_amount + x <= amount}는 판단과 쓰기가 한 문장이라
 * PostgreSQL이 그 행을 잠그고 최신 값으로 조건을 다시 본다(READ COMMITTED). 그래도 빠뜨린 경로가 있으면
 * {@code ck_invoice_paid}가 저장을 막는다. 연습실 예약과의 대비: 범위 겹침은 잠금 + EXCLUDE, 합계 한도는 조건부 UPDATE + CHECK.
 *
 * <p>장부(payment)는 네이티브 SQL로만 쓴다. JPA 엔티티를 두지 않아 1차 캐시가 낡을 일이 없다.
 */
@Service
public class BillingService {

    private final JdbcClient jdbc;
    private final StudentRepository students;
    private final EnrollmentRepository enrollments;
    private final OrganizationRepository organizations;
    private final Clock clock;

    BillingService(JdbcClient jdbc, StudentRepository students, EnrollmentRepository enrollments,
                   OrganizationRepository organizations, Clock clock) {
        this.jdbc = jdbc;
        this.students = students;
        this.enrollments = enrollments;
        this.organizations = organizations;
        this.clock = clock;
    }

    // ---------- 청구서 (56, 57, 58, 59) ----------

    @Transactional
    public Invoice create(long orgId, long studentId, Long enrollmentId, String title, long amount, LocalDate dueDate,
                          long byMembershipId) {
        students.findByIdAndOrganizationId(studentId, orgId).orElseThrow(() -> ApiException.notFound("원생을 찾을 수 없습니다"));
        if (enrollmentId != null) {
            Enrollment e = enrollments.findByIdAndOrganizationId(enrollmentId, orgId)
                    .orElseThrow(() -> ApiException.notFound("수강을 찾을 수 없습니다"));
            if (!e.getStudentId().equals(studentId)) {
                throw ApiException.policyViolation("ENROLLMENT_MISMATCH", "그 원생의 수강이 아닙니다");
            }
        }
        long id = jdbc.sql("""
                        insert into invoice (organization_id, student_id, enrollment_id, title, amount, due_date,
                                             created_by_membership_id, created_at)
                        values (:org, :student, :enrollment, :title, :amount, :due, :by, :now) returning id""")
                .param("org", orgId).param("student", studentId).param("enrollment", enrollmentId)
                .param("title", title.strip()).param("amount", amount).param("due", dueDate)
                .param("by", byMembershipId).param("now", java.sql.Timestamp.from(clock.instant()))
                .query(Long.class).single();
        return invoice(orgId, id);
    }

    /** 수강 등록 이벤트에서: BILLING이 켜진 기관, 금액 > 0만. 수강당 자동 청구서는 하나 (부분 UNIQUE) */
    @Transactional
    public void createAuto(long orgId, long enrollmentId, long studentId, long amount, LocalDate dueDate, String title) {
        jdbc.sql("""
                        insert into invoice (organization_id, student_id, enrollment_id, auto, title, amount, due_date, created_at)
                        values (:org, :student, :enrollment, true, :title, :amount, :due, :now)
                        on conflict (enrollment_id) where auto do nothing""")
                .param("org", orgId).param("student", studentId).param("enrollment", enrollmentId)
                .param("title", title.length() > 100 ? title.substring(0, 100) : title).param("amount", amount)
                .param("due", dueDate).param("now", java.sql.Timestamp.from(clock.instant()))
                .update();
    }

    @Transactional(readOnly = true)
    public Listing list(long orgId, String state, Long studentId) {
        LocalDate today = today(orgId);
        List<Invoice> all = jdbc.sql(SELECT_INVOICE + """
                        where i.organization_id = :org and (cast(:student as bigint) is null or i.student_id = :student)
                        order by (i.voided_at is not null), i.due_date, i.id""")
                .param("org", orgId).param("student", studentId)
                .query((rs, n) -> Invoice.from(rs, today)).list();
        // 머리 숫자는 거르기와 상관없이 기관 전체(또는 그 원생) 기준
        long outstanding = all.stream().filter(i -> i.state() != InvoiceState.VOID).mapToLong(Invoice::balance).sum();
        long overdue = all.stream().filter(Invoice::overdue).count();
        List<Invoice> shown = state == null || state.isBlank() ? all : all.stream().filter(i -> switch (state) {
            case "OVERDUE" -> i.overdue();
            case "OPEN" -> i.state() == InvoiceState.UNPAID || i.state() == InvoiceState.PARTIAL;
            default -> i.state().name().equals(state);
        }).toList();
        return new Listing(outstanding, overdue, shown);
    }

    @Transactional(readOnly = true)
    public Detail detail(long orgId, long invoiceId) {
        return new Detail(invoice(orgId, invoiceId), ledger(orgId, invoiceId, true));
    }

    /** 납부가 남아 있으면 무효로 못 한다. 금액을 고치려면 무효로 하고 다시 만든다(수정 API 없음) */
    @Transactional
    public Invoice voidInvoice(long orgId, long invoiceId, String reason) {
        requireReason(reason);
        int changed = jdbc.sql("""
                        update invoice set voided_at = :now, void_reason = :reason
                        where id = :id and organization_id = :org and voided_at is null and paid_amount = 0""")
                .param("now", java.sql.Timestamp.from(clock.instant())).param("reason", reason.strip())
                .param("id", invoiceId).param("org", orgId).update();
        if (changed == 0) {
            Invoice i = invoice(orgId, invoiceId);
            throw i.state() == InvoiceState.VOID
                    ? ApiException.conflict("ALREADY_VOID", "이미 무효로 한 청구서입니다")
                    : ApiException.conflict("HAS_PAYMENTS", "납부 기록이 있는 청구서는 무효로 할 수 없습니다. 입금을 먼저 취소하거나 환불해 주세요");
        }
        return invoice(orgId, invoiceId);
    }

    // ---------- 입금·환불 (60) ----------

    /**
     * 멱등: 장부 INSERT를 맨 앞에서 ON CONFLICT DO NOTHING으로 한다. 같은 키의 두 번째 요청은 유니크 인덱스에서 첫 요청의
     * 커밋(또는 롤백)을 기다렸다가, 커밋이면 0행을 받아 기존 기록을 돌려주고, 롤백이면 자기가 넣고 다시 판정받는다.
     * 예외·rollback-only·새 트랜잭션이 필요 없다. 같은 키에 내용이 다르면(화면 버그) 409로 거절한다.
     */
    @Transactional
    public Recorded record(long orgId, long invoiceId, UUID requestId, String kind, String method, long amount,
                           LocalDate paidOn, String memo, long byMembershipId) {
        invoice(orgId, invoiceId); // 없거나 다른 기관이면 404
        Optional<Long> inserted = jdbc.sql("""
                        insert into payment (organization_id, invoice_id, kind, method, amount, paid_on, memo, request_id,
                                             recorded_by_membership_id, recorded_at)
                        values (:org, :invoice, :kind, :method, :amount, :paidOn, :memo, :rid, :by, :now)
                        on conflict (organization_id, request_id) do nothing returning id""")
                .param("org", orgId).param("invoice", invoiceId).param("kind", kind).param("method", method)
                .param("amount", amount).param("paidOn", paidOn).param("memo", memo == null || memo.isBlank() ? null : memo.strip())
                .param("rid", requestId).param("by", byMembershipId).param("now", java.sql.Timestamp.from(clock.instant()))
                .query(Long.class).optional();
        if (inserted.isEmpty()) {
            Map<String, Object> existing = jdbc.sql("""
                            select id, invoice_id, kind, method, amount, paid_on from payment
                            where organization_id = :org and request_id = :rid""")
                    .param("org", orgId).param("rid", requestId).query().singleRow();
            boolean same = ((Number) existing.get("invoice_id")).longValue() == invoiceId
                    && kind.equals(existing.get("kind")) && method.equals(existing.get("method"))
                    && ((Number) existing.get("amount")).longValue() == amount
                    && Objects.equals(((java.sql.Date) existing.get("paid_on")).toLocalDate(), paidOn);
            if (!same) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "같은 요청 번호로 다른 내용이 왔습니다. 화면을 닫고 다시 열어 주세요");
            }
            return new Recorded(payment(orgId, ((Number) existing.get("id")).longValue()), invoice(orgId, invoiceId), false);
        }
        String condition = "PAYMENT".equals(kind)
                ? "paid_amount = paid_amount + :x where id = :id and organization_id = :org and voided_at is null and paid_amount + :x <= amount"
                : "paid_amount = paid_amount - :x where id = :id and organization_id = :org and voided_at is null and paid_amount - :x >= 0";
        int changed = jdbc.sql("update invoice set " + condition)
                .param("x", amount).param("id", invoiceId).param("org", orgId).update();
        if (changed == 0) {
            throw rejected(invoice(orgId, invoiceId), kind); // 예외로 INSERT까지 롤백된다
        }
        return new Recorded(payment(orgId, inserted.get()), invoice(orgId, invoiceId), true);
    }

    // ---------- 입금 취소 (61) ----------

    /** 잘못 적은 기록을 지우지 않고 취소한다. 반대 방향으로 같은 조건부 UPDATE */
    @Transactional
    public Recorded voidPayment(long orgId, long paymentId, String reason, long byMembershipId) {
        requireReason(reason);
        Optional<Map<String, Object>> row = jdbc.sql("""
                        update payment set voided_at = :now, void_reason = :reason, voided_by_membership_id = :by
                        where id = :id and organization_id = :org and voided_at is null
                        returning invoice_id, kind, amount""")
                .param("now", java.sql.Timestamp.from(clock.instant())).param("reason", reason.strip())
                .param("by", byMembershipId).param("id", paymentId).param("org", orgId).query().listOfRows().stream().findFirst();
        if (row.isEmpty()) {
            payment(orgId, paymentId); // 없으면 404
            throw ApiException.conflict("ALREADY_VOID", "이미 취소한 기록입니다");
        }
        long invoiceId = ((Number) row.get().get("invoice_id")).longValue();
        String kind = (String) row.get().get("kind");
        long amount = ((Number) row.get().get("amount")).longValue();
        String condition = "PAYMENT".equals(kind)
                ? "paid_amount = paid_amount - :x where id = :id and paid_amount - :x >= 0"
                : "paid_amount = paid_amount + :x where id = :id and voided_at is null and paid_amount + :x <= amount";
        if (jdbc.sql("update invoice set " + condition).param("x", amount).param("id", invoiceId).update() == 0) {
            throw "PAYMENT".equals(kind)
                    ? ApiException.policyViolation("REFUND_FIRST", "환불 기록이 남아 있어 이 입금을 취소할 수 없어요. 환불을 먼저 취소해 주세요")
                    : ApiException.policyViolation("OVERPAID", "이 환불을 취소하면 받은 금액이 청구액을 넘거나, 무효로 한 청구서예요");
        }
        return new Recorded(payment(orgId, paymentId), invoice(orgId, invoiceId), true);
    }

    // ---------- 영수증 (62), 학생 (63) ----------

    @Transactional(readOnly = true)
    public Receipt receipt(long orgId, long paymentId) {
        Payment p = payment(orgId, paymentId);
        if (p.voided() || !"PAYMENT".equals(p.kind())) {
            throw ApiException.conflict("NO_RECEIPT", "취소된 기록이나 환불에는 영수증이 없습니다");
        }
        Invoice i = invoice(orgId, p.invoiceId());
        String orgName = organizations.findById(orgId).orElseThrow().getName();
        return new Receipt("R-" + p.id(), orgName, i.studentName(), i.title(), p.amount(), p.method(), p.paidOn());
    }

    @Transactional(readOnly = true)
    public List<Detail> mine(long orgId, long membershipId) {
        Student student = students.findByOrganizationIdAndMembershipId(orgId, membershipId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "NOT_LINKED",
                        "학원에서 계정을 연결하면 청구서가 보여요"));
        return list(orgId, null, student.getId()).invoices().stream()
                .filter(i -> i.state() != InvoiceState.VOID)
                .map(i -> new Detail(i, ledger(orgId, i.id(), false))).toList();
    }

    // ---------- 도움 ----------

    private static final String SELECT_INVOICE = """
            select i.id, i.student_id, s.name as student_name, i.enrollment_id, i.auto, i.title, i.amount, i.paid_amount,
                   i.due_date, i.voided_at, i.void_reason, i.created_at
            from invoice i join student s on s.id = i.student_id
            """;

    private Invoice invoice(long orgId, long id) {
        LocalDate today = today(orgId);
        return jdbc.sql(SELECT_INVOICE + " where i.id = :id and i.organization_id = :org")
                .param("id", id).param("org", orgId).query((rs, n) -> Invoice.from(rs, today)).optional()
                .orElseThrow(() -> ApiException.notFound("청구서를 찾을 수 없습니다"));
    }

    private Payment payment(long orgId, long id) {
        return jdbc.sql(SELECT_PAYMENT + " where p.id = :id and p.organization_id = :org")
                .param("id", id).param("org", orgId).query(Payment::from).optional()
                .orElseThrow(() -> ApiException.notFound("기록을 찾을 수 없습니다"));
    }

    private List<Payment> ledger(long orgId, long invoiceId, boolean includeVoided) {
        return jdbc.sql(SELECT_PAYMENT + " where p.invoice_id = :id and p.organization_id = :org"
                        + (includeVoided ? "" : " and p.voided_at is null") + " order by p.recorded_at, p.id")
                .param("id", invoiceId).param("org", orgId).query(Payment::from).list();
    }

    private static final String SELECT_PAYMENT = """
            select p.id, p.invoice_id, p.kind, p.method, p.amount, p.paid_on, p.memo, p.recorded_at, p.voided_at, p.void_reason,
                   u.name as recorded_by
            from payment p
              join membership m on m.id = p.recorded_by_membership_id
              join user_account u on u.id = m.user_id
            """;

    private static ApiException rejected(Invoice i, String kind) {
        if (i.state() == InvoiceState.VOID) {
            return ApiException.conflict("INVOICE_VOID", "무효로 한 청구서입니다");
        }
        return "PAYMENT".equals(kind)
                ? ApiException.policyViolation("OVERPAID", "남은 금액(" + i.balance() + "원)보다 많이 받을 수 없어요").with("balance", i.balance())
                : ApiException.policyViolation("REFUND_EXCEEDS_PAID", "받은 금액(" + i.paid() + "원)보다 많이 환불할 수 없어요").with("paid", i.paid());
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalid("REASON_REQUIRED", "사유를 적어 주세요");
        }
    }

    private LocalDate today(long orgId) {
        return LocalDate.now(clock.withZone(ZoneId.of(organizations.findById(orgId).orElseThrow().getTimezone())));
    }

    /** balance: 남은 금액(무효면 0) */
    public record Invoice(long id, long studentId, String studentName, Long enrollmentId, boolean auto, String title,
                          long amount, long paid, long balance, LocalDate dueDate, InvoiceState state, boolean overdue,
                          String voidReason, Instant createdAt) {

        static Invoice from(java.sql.ResultSet rs, LocalDate today) throws java.sql.SQLException {
            long amount = rs.getLong("amount");
            long paid = rs.getLong("paid_amount");
            boolean voided = rs.getTimestamp("voided_at") != null;
            InvoiceState state = InvoiceState.of(amount, paid, voided);
            LocalDate due = rs.getDate("due_date").toLocalDate();
            // getLong + wasNull은 인자 평가 순서 때문에 다른 열을 본다(교차 리뷰 32 1-3). 비어 있으면 null
            Long enrollment = rs.getObject("enrollment_id", Long.class);
            return new Invoice(rs.getLong("id"), rs.getLong("student_id"), rs.getString("student_name"),
                    enrollment, rs.getBoolean("auto"), rs.getString("title"), amount, paid,
                    voided ? 0 : amount - paid, due, state,
                    !voided && paid < amount && due.isBefore(today), rs.getString("void_reason"),
                    rs.getTimestamp("created_at").toInstant());
        }
    }

    public record Payment(long id, long invoiceId, String kind, String method, long amount, LocalDate paidOn, String memo,
                          Instant recordedAt, String recordedBy, boolean voided, String voidReason) {

        static Payment from(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
            return new Payment(rs.getLong("id"), rs.getLong("invoice_id"), rs.getString("kind"), rs.getString("method"),
                    rs.getLong("amount"), rs.getDate("paid_on").toLocalDate(), rs.getString("memo"),
                    rs.getTimestamp("recorded_at").toInstant(), rs.getString("recorded_by"),
                    rs.getTimestamp("voided_at") != null, rs.getString("void_reason"));
        }
    }

    public record Listing(long outstanding, long overdueCount, List<Invoice> invoices) {
    }

    public record Detail(Invoice invoice, List<Payment> payments) {
    }

    /** created: 이번 요청이 새로 남겼는가(201), 같은 요청 번호의 기존 기록인가(200) */
    public record Recorded(Payment payment, Invoice invoice, boolean created) {
    }

    public record Receipt(String number, String organizationName, String studentName, String title, long amount,
                          String method, LocalDate paidOn) {
    }
}
