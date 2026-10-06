package com.musicstudio.billing.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.billing.application.BillingService;
import com.musicstudio.organization.api.CurrentMember;
import com.musicstudio.organization.api.OrgRole;
import com.musicstudio.organization.domain.MembershipRole;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 수납 (API 56~63). 경로가 /billing이라 BILLING 모듈을 끈 기관에서는 막힌다.
 * 돈은 관리자 이상만 본다. 강사는 메뉴도 API도 없다(403). 학생은 63번으로 자기 것만.
 */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}/billing")
class BillingController {

    private final BillingService billing;

    BillingController(BillingService billing) {
        this.billing = billing;
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @OrgRole(MembershipRole.MANAGER)
    BillingService.Invoice create(CurrentMember me, @Valid @RequestBody InvoiceRequest req) {
        return billing.create(me.organizationId(), req.studentId(), req.enrollmentId(), req.title(), req.amount(),
                req.dueDate(), me.membershipId());
    }

    /** state: UNPAID, PARTIAL, PAID, VOID, OVERDUE(기한 지남), OPEN(미납+부분) */
    @GetMapping("/invoices")
    @OrgRole(MembershipRole.MANAGER)
    BillingService.Listing list(CurrentMember me, @RequestParam(required = false) String state,
                                @RequestParam(required = false) Long studentId) {
        return billing.list(me.organizationId(), state, studentId);
    }

    @GetMapping("/invoices/{invoiceId}")
    @OrgRole(MembershipRole.MANAGER)
    BillingService.Detail detail(CurrentMember me, @PathVariable long invoiceId) {
        return billing.detail(me.organizationId(), invoiceId);
    }

    @PostMapping("/invoices/{invoiceId}/void")
    @OrgRole(MembershipRole.MANAGER)
    BillingService.Invoice voidInvoice(CurrentMember me, @PathVariable long invoiceId, @Valid @RequestBody ReasonRequest req) {
        return billing.voidInvoice(me.organizationId(), invoiceId, req.reason());
    }

    /** 새로 남기면 201, 같은 requestId의 기존 기록이면 200 */
    @PostMapping("/invoices/{invoiceId}/payments")
    @OrgRole(MembershipRole.MANAGER)
    ResponseEntity<BillingService.Recorded> record(CurrentMember me, @PathVariable long invoiceId,
                                                   @Valid @RequestBody PaymentRequest req) {
        BillingService.Recorded r = billing.record(me.organizationId(), invoiceId, req.requestId(), req.kind(), req.method(),
                req.amount(), req.paidOn(), req.memo(), me.membershipId());
        return ResponseEntity.status(r.created() ? HttpStatus.CREATED : HttpStatus.OK).body(r);
    }

    @PostMapping("/payments/{paymentId}/void")
    @OrgRole(MembershipRole.MANAGER)
    BillingService.Recorded voidPayment(CurrentMember me, @PathVariable long paymentId, @Valid @RequestBody ReasonRequest req) {
        return billing.voidPayment(me.organizationId(), paymentId, req.reason(), me.membershipId());
    }

    @GetMapping("/payments/{paymentId}/receipt")
    @OrgRole(MembershipRole.MANAGER)
    BillingService.Receipt receipt(CurrentMember me, @PathVariable long paymentId) {
        return billing.receipt(me.organizationId(), paymentId);
    }

    /** 학생: 연결된 원생의 청구서와 장부. 메모·기록자는 뺀다 */
    @GetMapping("/me/invoices")
    @OrgRole(MembershipRole.STUDENT)
    List<StudentInvoice> mine(CurrentMember me) {
        return billing.mine(me.organizationId(), me.membershipId()).stream().map(StudentInvoice::of).toList();
    }

    record InvoiceRequest(@NotNull Long studentId, Long enrollmentId, @NotBlank @Size(max = 100) String title,
                          @NotNull @Positive @Max(100_000_000) Long amount, @NotNull LocalDate dueDate) {
    }

    record PaymentRequest(@NotNull UUID requestId, @NotNull @Pattern(regexp = "PAYMENT|REFUND") String kind,
                          @NotNull @Pattern(regexp = "CASH|TRANSFER|CARD|OTHER") String method,
                          @NotNull @Positive @Max(100_000_000) Long amount, @NotNull LocalDate paidOn,
                          @Size(max = 200) String memo) {
    }

    record ReasonRequest(@NotBlank @Size(max = 200) String reason) {
    }

    record StudentPayment(String kind, String method, long amount, LocalDate paidOn) {
    }

    record StudentInvoice(long id, String title, long amount, long paid, long balance, LocalDate dueDate, String state,
                          boolean overdue, List<StudentPayment> payments) {
        static StudentInvoice of(BillingService.Detail d) {
            BillingService.Invoice i = d.invoice();
            return new StudentInvoice(i.id(), i.title(), i.amount(), i.paid(), i.balance(), i.dueDate(), i.state().name(),
                    i.overdue(), d.payments().stream()
                    .map(p -> new StudentPayment(p.kind(), p.method(), p.amount(), p.paidOn())).toList());
        }
    }
}
