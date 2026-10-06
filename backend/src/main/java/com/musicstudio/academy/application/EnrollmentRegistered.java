package com.musicstudio.academy.application;

import java.time.LocalDate;

/**
 * 수강을 등록했다. 같은 트랜잭션 안에서 동기로 발행한다(받는 쪽이 실패하면 등록도 되돌아간다).
 * academy는 누가 받는지 모른다. 지금은 수납(billing)이 자동 청구서를 만든다.
 * AFTER_COMMIT으로 바꾸면 Outbox 없이 유실될 수 있어서 동기로 둔다.
 */
public record EnrollmentRegistered(long organizationId, long enrollmentId, long studentId, long price,
                                   LocalDate startsOn, String title) {
}
