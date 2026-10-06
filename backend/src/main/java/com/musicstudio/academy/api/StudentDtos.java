package com.musicstudio.academy.api;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import com.musicstudio.academy.application.LessonScheduleService;
import com.musicstudio.academy.application.StudentService;
import com.musicstudio.academy.domain.Enrollment;
import com.musicstudio.academy.domain.EnrollmentRepository.EnrollmentRow;
import com.musicstudio.academy.domain.EnrollmentStatus;
import com.musicstudio.academy.domain.LessonRecord;
import com.musicstudio.academy.domain.LessonRecordRepository.RecordRow;
import com.musicstudio.academy.domain.ProductKind;
import com.musicstudio.academy.domain.Student;
import com.musicstudio.academy.domain.StudentRepository.StudentRow;
import com.musicstudio.common.crypto.EncryptedStringConverter;

/**
 * 원생 응답. 연락처 원문은 관리자의 상세와 학생 본인에게만 준다. 그 밖에는 가린 값이다.
 * 금액과 관리 메모는 관리자에게만 준다.
 */
final class StudentDtos {

    private StudentDtos() {
    }

    record Summary(Long id, String name, Short birthYear, String phone, boolean active, boolean linked,
                   List<EnrollmentChip> enrollments) {
        static Summary of(StudentService.Listed l) {
            Student s = l.student();
            return new Summary(s.getId(), s.getName(), s.getBirthYear(), EncryptedStringConverter.mask(s.getPhone()),
                    s.isActive(), s.getMembershipId() != null, l.enrollments().stream().map(EnrollmentChip::of).toList());
        }
    }

    record EnrollmentChip(Long id, Long subjectId, String subjectName, String productName, Long teacherMembershipId,
                          String teacherName, EnrollmentStatus status, LocalDate endsOn, Short totalSessions) {
        static EnrollmentChip of(StudentRow r) {
            Enrollment e = r.getEnrollment();
            return new EnrollmentChip(e.getId(), r.getSubjectId(), r.getSubjectName(), r.getProductName(),
                    e.getTeacherMembershipId(), r.getTeacherName(), e.getStatus(), e.getEndsOn(), e.getTotalSessions());
        }
    }

    record Info(Long id, String name, Short birthYear, String phone, String guardianName, String guardianPhone,
                String memo, boolean active, Long membershipId) {
        static Info of(Student s, boolean raw) {
            return new Info(s.getId(), s.getName(), s.getBirthYear(),
                    raw ? s.getPhone() : EncryptedStringConverter.mask(s.getPhone()), s.getGuardianName(),
                    raw ? s.getGuardianPhone() : EncryptedStringConverter.mask(s.getGuardianPhone()),
                    raw ? s.getMemo() : null, s.isActive(), s.getMembershipId());
        }
    }

    /**
     * 수강과 레슨 일정 요약(UC-49): 고정 일정, 횟수권 남은 회차(총 − 출석·결석), 자동으로 못 이어 붙인 회차(shortfall),
     * 다음·마지막 회차 날짜.
     */
    record EnrollmentDetail(Long id, String productName, String subjectName, ProductKind kind, Long teacherMembershipId,
                            String teacherName, EnrollmentStatus status, Long price, LocalDate startsOn,
                            LocalDate endsOn, Short totalSessions, LocalDate pausedAt, long version,
                            List<SlotInfo> schedule, Integer remainingSessions, int shortfall,
                            LocalDate nextLessonDate, LocalDate lastLessonDate) {
        static EnrollmentDetail of(EnrollmentRow r, boolean showPrice, Map<Long, LessonScheduleService.Summary> lessons) {
            Enrollment e = r.getEnrollment();
            LessonScheduleService.Summary l = lessons.getOrDefault(e.getId(),
                    new LessonScheduleService.Summary(List.of(), null, 0, null, null));
            return new EnrollmentDetail(e.getId(), r.getProductName(), r.getSubjectName(), r.getKind(),
                    e.getTeacherMembershipId(), r.getTeacherName(), e.getStatus(), showPrice ? e.getPrice() : null,
                    e.getStartsOn(), e.getEndsOn(), e.getTotalSessions(), e.getPausedAt(), e.getVersion(),
                    l.slots().stream().map(x -> new SlotInfo(x.day(), x.time())).toList(), l.remaining(), l.shortfall(),
                    l.next(), l.last());
        }
    }

    record SlotInfo(DayOfWeek dayOfWeek, LocalTime startTime) {
    }

    record RecordInfo(Long id, Long enrollmentId, Long authorMembershipId, String authorName, LocalDate lessonDate,
                      String progress, String homework, String memo, boolean visibleToStudent, boolean mine) {
        static RecordInfo of(LessonRecord r, String authorName, long requester) {
            return new RecordInfo(r.getId(), r.getEnrollmentId(), r.getAuthorMembershipId(), authorName,
                    r.getLessonDate(), r.getProgress(), r.getHomework(), r.getMemo(), r.isVisibleToStudent(),
                    r.getAuthorMembershipId() == requester);
        }

        static RecordInfo of(RecordRow row, long requester) {
            return of(row.getRecord(), row.getAuthorName(), requester);
        }
    }

    record Detail(Info student, List<EnrollmentDetail> enrollments, List<RecordInfo> records) {
        static Detail of(StudentService.Detail d, boolean manager, long requester,
                         Map<Long, LessonScheduleService.Summary> lessons) {
            return new Detail(Info.of(d.student(), manager),
                    d.enrollments().stream().map(r -> EnrollmentDetail.of(r, manager, lessons)).toList(),
                    d.records().stream().map(r -> RecordInfo.of(r, requester)).toList());
        }

        /** 학생 본인: 자기 연락처는 원문, 금액과 관리 메모는 없음. */
        static Detail forStudent(StudentService.Detail d, long requester, Map<Long, LessonScheduleService.Summary> lessons) {
            Info full = Info.of(d.student(), true);
            Info own = new Info(full.id(), full.name(), full.birthYear(), full.phone(), full.guardianName(),
                    full.guardianPhone(), null, full.active(), full.membershipId());
            return new Detail(own, d.enrollments().stream().map(r -> EnrollmentDetail.of(r, false, lessons)).toList(),
                    d.records().stream().map(r -> RecordInfo.of(r, requester)).toList());
        }
    }

    record EnrollmentInfo(Long id, Long studentId, Long productId, Long teacherMembershipId, EnrollmentStatus status,
                          long price, LocalDate startsOn, LocalDate endsOn, Short totalSessions, LocalDate pausedAt,
                          long version) {
        static EnrollmentInfo of(Enrollment e) {
            return new EnrollmentInfo(e.getId(), e.getStudentId(), e.getProductId(), e.getTeacherMembershipId(),
                    e.getStatus(), e.getPrice(), e.getStartsOn(), e.getEndsOn(), e.getTotalSessions(), e.getPausedAt(),
                    e.getVersion());
        }
    }
}
