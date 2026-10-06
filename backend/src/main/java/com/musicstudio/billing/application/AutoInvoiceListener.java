package com.musicstudio.billing.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.musicstudio.academy.application.EnrollmentRegistered;
import com.musicstudio.organization.domain.Module;
import com.musicstudio.organization.domain.OrganizationRepository;

/**
 * 수강을 등록하면 그 금액으로 청구서를 만든다 (FR-PAY-01). 수강 등록과 같은 트랜잭션의 동기 리스너다.
 * 수납을 안 쓰는 기관(BILLING 꺼짐)에는 미납이 쌓이지 않게 만들지 않고, 0원 상품(체험 레슨)은 청구서 금액 CHECK(> 0)에
 * 걸려 수강 등록까지 실패하므로 건너뛴다.
 */
@Component
class AutoInvoiceListener {

    private final BillingService billing;
    private final OrganizationRepository organizations;
    private final Clock clock;

    AutoInvoiceListener(BillingService billing, OrganizationRepository organizations, Clock clock) {
        this.billing = billing;
        this.organizations = organizations;
        this.clock = clock;
    }

    @EventListener
    void on(EnrollmentRegistered e) {
        boolean billingOn = organizations.findById(e.organizationId()).orElseThrow().getModules().contains(Module.BILLING);
        if (billingOn && e.price() > 0) {
            // 지난 날짜로 등록한 수강이 만들자마자 '기한 지남'이 되지 않게 기한은 시작일과 오늘 중 늦은 날 (리뷰 31 1-6)
            LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(organizations.findById(e.organizationId()).orElseThrow().getTimezone())));
            LocalDate due = e.startsOn().isAfter(today) ? e.startsOn() : today;
            billing.createAuto(e.organizationId(), e.enrollmentId(), e.studentId(), e.price(), due, e.title());
        }
    }
}
