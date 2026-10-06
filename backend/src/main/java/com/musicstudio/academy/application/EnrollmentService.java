package com.musicstudio.academy.application;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.academy.domain.Enrollment;
import com.musicstudio.academy.domain.EnrollmentRepository;
import com.musicstudio.academy.domain.EnrollmentStatus;
import com.musicstudio.academy.domain.LessonRecord;
import com.musicstudio.academy.domain.LessonRecordRepository;
import com.musicstudio.academy.domain.Product;
import com.musicstudio.academy.domain.ProductRepository;
import com.musicstudio.academy.domain.Student;
import com.musicstudio.academy.domain.StudentRepository;
import com.musicstudio.common.error.ApiException;
import com.musicstudio.organization.domain.Membership;
import com.musicstudio.organization.domain.MembershipRepository;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.organization.domain.MembershipStatus;

import jakarta.persistence.EntityManager;

/** 수강 등록과 변경 (UC-42, 43), 레슨 기록 (UC-46). */
@Service
public class EnrollmentService {

    /** 1인 학원은 원장이 직접 가르치므로 관리자·소유자도 강사가 될 수 있다. */
    private static final Set<MembershipRole> TEACHING = EnumSet.of(MembershipRole.TEACHER, MembershipRole.MANAGER,
            MembershipRole.OWNER);

    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final ProductRepository products;
    private final LessonRecordRepository records;
    private final MembershipRepository memberships;
    private final StudentService studentService;
    private final EntityManager entityManager;

    EnrollmentService(EnrollmentRepository enrollments, StudentRepository students, ProductRepository products,
                      LessonRecordRepository records, MembershipRepository memberships, StudentService studentService,
                      EntityManager entityManager) {
        this.enrollments = enrollments;
        this.students = students;
        this.products = products;
        this.records = records;
        this.memberships = memberships;
        this.studentService = studentService;
        this.entityManager = entityManager;
    }

    @Transactional
    public Enrollment enroll(long orgId, long studentId, long productId, long teacherMembershipId, LocalDate startsOn) {
        Student student = students.findByIdAndOrganizationId(studentId, orgId)
                .orElseThrow(() -> ApiException.notFound("원생을 찾을 수 없습니다"));
        if (!student.isActive()) {
            throw StudentService.inactiveStudent();
        }
        Product product = products.findByIdAndOrganizationId(productId, orgId)
                .orElseThrow(() -> ApiException.notFound("상품을 찾을 수 없습니다"));
        requireTeacher(orgId, teacherMembershipId);
        return enrollments.save(new Enrollment(orgId, studentId, product, teacherMembershipId, startsOn));
    }

    /** extend, change-teacher, pause, resume, end, refund. 허용되지 않는 전이는 409. */
    @Transactional
    public Enrollment act(long orgId, long enrollmentId, String action, Integer months, Integer sessions,
                          Long teacherMembershipId) {
        Enrollment e = enrollments.findByIdAndOrganizationId(enrollmentId, orgId)
                .orElseThrow(() -> ApiException.notFound("수강을 찾을 수 없습니다"));
        LocalDate today = studentService.today(orgId);
        boolean ok = switch (action) {
            case "pause" -> e.pause(today);
            case "resume" -> e.resume(today);
            case "end" -> e.finish(EnrollmentStatus.ENDED);
            case "refund" -> e.finish(EnrollmentStatus.REFUNDED);
            case "extend" -> {
                if (e.isOpen() && !e.extend(months, sessions)) {
                    throw ApiException.invalid("INVALID_EXTENSION", "기간권은 개월 수, 횟수권은 횟수로 연장합니다");
                }
                yield e.isOpen();
            }
            case "change-teacher" -> {
                if (teacherMembershipId == null) {
                    throw ApiException.invalid("TEACHER_REQUIRED", "강사를 골라 주세요");
                }
                requireTeacher(orgId, teacherMembershipId);
                yield e.changeTeacher(teacherMembershipId);
            }
            default -> throw ApiException.notFound("알 수 없는 동작입니다");
        };
        if (!ok) {
            throw ApiException.conflict("INVALID_TRANSITION", "지금 상태에서는 할 수 없습니다");
        }
        try {
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            throw ApiException.conflict("CONFLICTING_UPDATE", "다른 관리자가 먼저 바꿨습니다. 다시 불러와 주세요");
        }
        return e;
    }

    // ---------- 레슨 기록 ----------

    /** 그 수강의 지금 담당 강사이고 수강이 진행 중일 때만 쓴다. */
    @Transactional
    public LessonRecord writeRecord(long orgId, long authorMembershipId, long enrollmentId, RecordInput in) {
        Enrollment e = enrollments.findByIdAndOrganizationId(enrollmentId, orgId)
                .orElseThrow(() -> ApiException.notFound("수강을 찾을 수 없습니다"));
        if (!e.isOpen() || e.getTeacherMembershipId() != authorMembershipId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "NOT_ASSIGNED", "담당하는 수강에만 기록을 쓸 수 있습니다");
        }
        LessonRecord record = new LessonRecord(orgId, enrollmentId, authorMembershipId);
        record.write(in.lessonDate(), in.progress(), in.homework(), in.memo(), in.visibleToStudent());
        return records.save(record);
    }

    /** 쓴 사람만 고친다. 담당이 바뀐 뒤에도 쓴 사람은 고칠 수 있다. */
    @Transactional
    public LessonRecord editRecord(long orgId, long requesterMembershipId, long recordId, RecordInput in) {
        LessonRecord record = ownRecord(orgId, requesterMembershipId, recordId);
        record.write(in.lessonDate(), in.progress(), in.homework(), in.memo(), in.visibleToStudent());
        return record;
    }

    @Transactional
    public void deleteRecord(long orgId, long requesterMembershipId, long recordId) {
        records.delete(ownRecord(orgId, requesterMembershipId, recordId));
    }

    private LessonRecord ownRecord(long orgId, long requesterMembershipId, long recordId) {
        LessonRecord record = records.findByIdAndOrganizationId(recordId, orgId)
                .orElseThrow(() -> ApiException.notFound("기록을 찾을 수 없습니다"));
        if (record.getAuthorMembershipId() != requesterMembershipId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "NOT_AUTHOR", "쓴 사람만 고칠 수 있습니다");
        }
        return record;
    }

    private void requireTeacher(long orgId, long membershipId) {
        Membership m = memberships.findByIdAndOrganizationId(membershipId, orgId)
                .filter(x -> TEACHING.contains(x.getRole()))
                .orElseThrow(() -> ApiException.policyViolation("INVALID_TEACHER", "강사로 고를 수 없는 멤버입니다"));
        if (m.getStatus() != MembershipStatus.ACTIVE) {
            throw ApiException.policyViolation("TEACHER_INACTIVE", "비활성 멤버는 강사로 고를 수 없습니다");
        }
    }

    public record RecordInput(LocalDate lessonDate, String progress, String homework, String memo,
                              boolean visibleToStudent) {
    }
}
