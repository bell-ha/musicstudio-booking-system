package com.musicstudio.billing.domain;

/** 저장하지 않고 금액에서 계산한다. 기한 지남(overdue)은 따로 표시한다 */
public enum InvoiceState {
    UNPAID, PARTIAL, PAID, VOID;

    public static InvoiceState of(long amount, long paid, boolean voided) {
        if (voided) {
            return VOID;
        }
        return paid == 0 ? UNPAID : paid < amount ? PARTIAL : PAID;
    }
}
