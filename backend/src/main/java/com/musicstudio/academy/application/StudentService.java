package com.musicstudio.academy.application;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.academy.domain.EnrollmentRepository;
import com.musicstudio.academy.domain.EnrollmentRepository.EnrollmentRow;
import com.musicstudio.academy.domain.EnrollmentStatus;
import com.musicstudio.academy.domain.LessonRecordRepository;
import com.musicstudio.academy.domain.LessonRecordRepository.RecordRow;
import com.musicstudio.academy.domain.Student;
import com.musicstudio.academy.domain.StudentRepository;
import com.musicstudio.academy.domain.StudentRepository.StudentRow;
import com.musicstudio.common.error.ApiException;
import com.musicstudio.organization.domain.Membership;
import com.musicstudio.organization.domain.MembershipRepository;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.organization.domain.MembershipStatus;
import com.musicstudio.organization.domain.OrganizationRepository;

/** 원생 등록·수정·목록·상세 (UC-41, 44, 45, 47, 48). */
@Service
public class StudentService {

    private final StudentRepository students;
    private final EnrollmentRepository enrollments;
    private final LessonRecordRepository records;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final Clock clock;

    StudentService(StudentRepository students, EnrollmentRepository enrollments, LessonRecordRepository records,
                   MembershipRepository memberships, OrganizationRepository organizations, Clock clock) {
        this.students = students;
        this.enrollments = enrollments;
        this.records = records;
        this.memberships = memberships;
        this.organizations = organizations;
        this.clock = clock;
    }

    // ---------- 등록과 수정 ----------

    @Transactional
    public Student save(long orgId, Long studentId, StudentInput in) {
        Student student = studentId == null ? new Student(orgId)
                : students.findByIdAndOrganizationId(studentId, orgId).orElseThrow(StudentService::notFound);
        student.update(in.name().trim(), in.birthYear(), phone(in.phone(), "phone"), blankToNull(in.guardianName()),
                phone(in.guardianPhone(), "guardianPhone"), blankToNull(in.memo()), in.active() == null || in.active());
        return students.save(student);
    }

    /** 학생 계정 연결과 해제. 같은 기관의 활성 학생 멤버만, 한 계정은 원생 하나에만 연결된다. */
    @Transactional
    public Student link(long orgId, long studentId, Long membershipId) {
        Student student = students.findByIdAndOrganizationId(studentId, orgId).orElseThrow(StudentService::notFound);
        if (membershipId != null) {
            if (!student.isActive()) {
                throw inactiveStudent();
            }
            Membership m = memberships.findByIdAndOrganizationId(membershipId, orgId)
                    .filter(x -> x.getStatus() == MembershipStatus.ACTIVE && x.getRole() == MembershipRole.STUDENT)
                    .orElseThrow(() -> ApiException.policyViolation("INVALID_ACCOUNT", "이 기관의 활성 학생 계정만 연결할 수 있습니다"));
            student.linkAccount(m.getId());
        } else {
            student.linkAccount(null);
        }
        try {
            return students.saveAndFlush(student);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("ACCOUNT_ALREADY_LINKED", "이미 다른 원생에 연결된 계정입니다");
        }
    }

    // ---------- 목록 ----------

    /**
     * 관리자: 전체(필터). 강사: 내가 담당하는 진행 중 수강이 있는 원생만(mine 강제).
     * expiringWithinDays: 종료일이 오늘 + N일 이내인 진행 중 기간권(이미 지난 것 포함).
     */
    @Transactional(readOnly = true)
    public List<Listed> list(long orgId, long requesterMembershipId, boolean manager, Filter f) {
        LocalDate today = today(orgId);
        boolean mine = !manager || f.mine();
        Map<Long, Listed> byStudent = new LinkedHashMap<>();
        for (StudentRow row : students.findRows(orgId)) {
            Student s = row.getStudent();
            Listed listed = byStudent.computeIfAbsent(s.getId(), id -> new Listed(s, new java.util.ArrayList<>()));
            if (row.getEnrollment() != null) {
                listed.enrollments().add(row);
            }
        }
        return byStudent.values().stream()
                .filter(l -> f.active() == null ? l.student().isActive() : l.student().isActive() == f.active())
                .filter(l -> f.q() == null || f.q().isBlank()
                        || l.student().getName().toLowerCase().contains(f.q().trim().toLowerCase()))
                .filter(l -> !mine || l.enrollments().stream()
                        .anyMatch(r -> r.getEnrollment().getTeacherMembershipId() == requesterMembershipId))
                .filter(l -> f.subjectId() == null || l.enrollments().stream()
                        .anyMatch(r -> f.subjectId().equals(r.getSubjectId())))
                .filter(l -> f.teacherId() == null || l.enrollments().stream()
                        .anyMatch(r -> f.teacherId().equals(r.getEnrollment().getTeacherMembershipId())))
                .filter(l -> f.expiringWithinDays() == null || l.enrollments().stream()
                        .anyMatch(r -> r.getEnrollment().getStatus() == EnrollmentStatus.ACTIVE
                                && r.getEnrollment().getEndsOn() != null
                                && !r.getEnrollment().getEndsOn().isAfter(today.plusDays(f.expiringWithinDays()))))
                .toList();
    }

    // ---------- 상세 ----------

    /**
     * 관리자: 전부, 연락처 원문.
     * 강사: 지금 담당하는 진행 중 수강의 기록 전부(이전 강사 것 포함) + 내가 쓴 기록. 둘 다 없으면 404. 연락처는 가린다.
     */
    @Transactional(readOnly = true)
    public Detail detail(long orgId, long studentId, long requesterMembershipId, boolean manager) {
        Student student = students.findByIdAndOrganizationId(studentId, orgId).orElseThrow(StudentService::notFound);
        List<EnrollmentRow> rows = enrollments.findWithNames(studentId);
        List<RecordRow> allRecords = rows.isEmpty() ? List.of()
                : records.findWithAuthor(rows.stream().map(r -> r.getEnrollment().getId()).toList());
        if (manager) {
            return new Detail(student, rows, allRecords);
        }
        Set<Long> assigned = rows.stream().map(EnrollmentRow::getEnrollment)
                .filter(e -> e.isOpen() && e.getTeacherMembershipId() == requesterMembershipId)
                .map(e -> e.getId()).collect(Collectors.toSet());
        List<RecordRow> visible = allRecords.stream()
                .filter(r -> assigned.contains(r.getRecord().getEnrollmentId())
                        || r.getRecord().getAuthorMembershipId() == requesterMembershipId)
                .toList();
        if (assigned.isEmpty() && visible.isEmpty()) {
            throw notFound();
        }
        return new Detail(student, rows, visible);
    }

    /** 학생 본인 (UC-48): 연결된 원생의 수강과 공개된 기록만. */
    @Transactional(readOnly = true)
    public Detail mine(long orgId, long membershipId) {
        Student student = students.findByOrganizationIdAndMembershipId(orgId, membershipId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "NOT_LINKED",
                        "학원에서 계정을 연결하면 수강 정보가 보여요"));
        List<EnrollmentRow> rows = enrollments.findWithNames(student.getId());
        List<RecordRow> visible = rows.isEmpty() ? List.of()
                : records.findWithAuthor(rows.stream().map(r -> r.getEnrollment().getId()).toList()).stream()
                        .filter(r -> r.getRecord().isVisibleToStudent()).toList();
        return new Detail(student, rows, visible);
    }

    LocalDate today(long orgId) {
        return LocalDate.now(clock.withZone(ZoneId.of(organizations.findById(orgId).orElseThrow().getTimezone())));
    }

    /** 숫자만 남기고 10~11자리만 받는다. 비어 있으면 null. */
    static String phone(String input, String field) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String digits = input.replaceAll("\\D", "");
        if (digits.length() < 10 || digits.length() > 11) {
            throw ApiException.invalidFields("INVALID_PHONE", Map.of(field, "전화번호 10~11자리를 입력해 주세요"));
        }
        return digits;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static ApiException inactiveStudent() {
        return ApiException.policyViolation("STUDENT_INACTIVE", "비활성 원생입니다");
    }

    private static ApiException notFound() {
        return ApiException.notFound("원생을 찾을 수 없습니다");
    }

    public record StudentInput(String name, Integer birthYear, String phone, String guardianName,
                               String guardianPhone, String memo, Boolean active) {
    }

    public record Filter(String q, Long subjectId, Long teacherId, Boolean active, Integer expiringWithinDays,
                         boolean mine) {
    }

    public record Listed(Student student, List<StudentRow> enrollments) {
    }

    public record Detail(Student student, List<EnrollmentRow> enrollments, List<RecordRow> records) {
    }
}
