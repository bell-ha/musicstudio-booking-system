package com.musicstudio.billing.application;

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

    AutoInvoiceListener(BillingService billing, OrganizationRepository organizations) {
        this.billing = billing;
        this.organizations = organizations;
    }

    @EventListener
    void on(EnrollmentRegistered e) {
        boolean billingOn = organizations.findById(e.organizationId()).orElseThrow().getModules().contains(Module.BILLING);
        if (billingOn && e.price() > 0) {
            billing.createAuto(e.organizationId(), e.enrollmentId(), e.studentId(), e.price(), e.startsOn(), e.title());
        }
    }
}
