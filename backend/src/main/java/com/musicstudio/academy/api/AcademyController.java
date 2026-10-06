package com.musicstudio.academy.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.academy.application.CatalogService;
import com.musicstudio.academy.application.EnrollmentService;
import com.musicstudio.academy.application.EnrollmentService.RecordInput;
import com.musicstudio.academy.application.StudentService;
import com.musicstudio.academy.application.StudentService.Filter;
import com.musicstudio.academy.application.StudentService.StudentInput;
import com.musicstudio.academy.domain.Enrollment;
import com.musicstudio.academy.domain.LessonRecord;
import com.musicstudio.academy.domain.Product;
import com.musicstudio.academy.domain.ProductKind;
import com.musicstudio.academy.domain.Subject;
import com.musicstudio.organization.api.CurrentMember;
import com.musicstudio.organization.api.OrgRole;
import com.musicstudio.organization.domain.MembershipRole;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 학원 관리 (API 24~36). 경로가 /academy라 학원 관리 모듈을 끈 기관에서는 막힌다. */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}/academy")
class AcademyController {

    private final CatalogService catalog;
    private final StudentService students;
    private final EnrollmentService enrollments;

    AcademyController(CatalogService catalog, StudentService students, EnrollmentService enrollments) {
        this.catalog = catalog;
        this.students = students;
        this.enrollments = enrollments;
    }

    // ---------- 과목·상품 ----------

    @GetMapping("/catalog")
    @OrgRole(MembershipRole.MANAGER)
    CatalogResponse catalog(CurrentMember me) {
        CatalogService.Catalog c = catalog.catalog(me.organizationId());
        return new CatalogResponse(c.subjects().stream().map(SubjectResponse::of).toList(),
                c.products().stream().map(ProductResponse::of).toList());
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @OrgRole(MembershipRole.MANAGER)
    SubjectResponse createSubject(CurrentMember me, @Valid @RequestBody SubjectRequest req) {
        return SubjectResponse.of(catalog.createSubject(me.organizationId(), req.name()));
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @OrgRole(MembershipRole.MANAGER)
    ProductResponse createProduct(CurrentMember me, @Valid @RequestBody ProductRequest req) {
        return ProductResponse.of(catalog.createProduct(me.organizationId(), req.subjectId(), req.name(), req.kind(),
                req.sessionCount(), req.periodMonths(), req.lessonMinutes(), req.price()));
    }

    @PatchMapping("/products/{productId}")
    @OrgRole(MembershipRole.MANAGER)
    ProductResponse updateProduct(CurrentMember me, @PathVariable long productId, @Valid @RequestBody ProductPatch req) {
        return ProductResponse.of(catalog.updateProduct(me.organizationId(), productId, req.name(), req.price()));
    }

    // ---------- 원생 ----------

    /** 관리자는 전체(필터), 강사는 담당 원생만. mine=true면 관리자도 자기가 담당하는 원생만. */
    @GetMapping("/students")
    @OrgRole({MembershipRole.MANAGER, MembershipRole.TEACHER})
    List<StudentDtos.Summary> list(CurrentMember me, String q, Long subjectId, Long teacherId, Boolean active,
                                   Integer expiringWithinDays, Boolean mine) {
        return students.list(me.organizationId(), me.membershipId(), isManager(me),
                        new Filter(q, subjectId, teacherId, active, expiringWithinDays, Boolean.TRUE.equals(mine)))
                .stream().map(StudentDtos.Summary::of).toList();
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    @OrgRole(MembershipRole.MANAGER)
    StudentDtos.Info create(CurrentMember me, @Valid @RequestBody StudentRequest req) {
        return StudentDtos.Info.of(students.save(me.organizationId(), null, req.toInput()), true);
    }

    @PatchMapping("/students/{studentId}")
    @OrgRole(MembershipRole.MANAGER)
    StudentDtos.Info update(CurrentMember me, @PathVariable long studentId, @Valid @RequestBody StudentRequest req) {
        return StudentDtos.Info.of(students.save(me.organizationId(), studentId, req.toInput()), true);
    }

    /** 학생 계정 연결. membershipId가 null이면 해제한다. */
    @PutMapping("/students/{studentId}/account")
    @OrgRole(MembershipRole.MANAGER)
    StudentDtos.Info link(CurrentMember me, @PathVariable long studentId, @RequestBody LinkRequest req) {
        return StudentDtos.Info.of(students.link(me.organizationId(), studentId, req.membershipId()), true);
    }

    @GetMapping("/students/me")
    @OrgRole(MembershipRole.STUDENT)
    StudentDtos.Detail mine(CurrentMember me) {
        return StudentDtos.Detail.forStudent(students.mine(me.organizationId(), me.membershipId()), me.membershipId());
    }

    @GetMapping("/students/{studentId}")
    @OrgRole({MembershipRole.MANAGER, MembershipRole.TEACHER})
    StudentDtos.Detail detail(CurrentMember me, @PathVariable long studentId) {
        boolean manager = isManager(me);
        return StudentDtos.Detail.of(students.detail(me.organizationId(), studentId, me.membershipId(), manager),
                manager, me.membershipId());
    }

    // ---------- 수강 ----------

    @PostMapping("/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @OrgRole(MembershipRole.MANAGER)
    StudentDtos.EnrollmentInfo enroll(CurrentMember me, @Valid @RequestBody EnrollRequest req) {
        Enrollment e = enrollments.enroll(me.organizationId(), req.studentId(), req.productId(),
                req.teacherMembershipId(), req.startsOn());
        return StudentDtos.EnrollmentInfo.of(e);
    }

    @PostMapping("/enrollments/{enrollmentId}/{action}")
    @OrgRole(MembershipRole.MANAGER)
    StudentDtos.EnrollmentInfo act(CurrentMember me, @PathVariable long enrollmentId, @PathVariable String action,
                                   @RequestBody(required = false) ActionRequest req) {
        ActionRequest body = req == null ? new ActionRequest(null, null, null) : req;
        return StudentDtos.EnrollmentInfo.of(enrollments.act(me.organizationId(), enrollmentId, action,
                body.months(), body.sessions(), body.teacherMembershipId()));
    }

    // ---------- 레슨 기록 (담당 여부는 서비스가 검사한다) ----------

    @PostMapping("/lesson-records")
    @ResponseStatus(HttpStatus.CREATED)
    @OrgRole({MembershipRole.TEACHER, MembershipRole.MANAGER})
    StudentDtos.RecordInfo writeRecord(CurrentMember me, @Valid @RequestBody RecordRequest req) {
        LessonRecord r = enrollments.writeRecord(me.organizationId(), me.membershipId(), req.enrollmentId(), req.toInput());
        return StudentDtos.RecordInfo.of(r, null, me.membershipId());
    }

    @PatchMapping("/lesson-records/{recordId}")
    @OrgRole({MembershipRole.TEACHER, MembershipRole.MANAGER})
    StudentDtos.RecordInfo editRecord(CurrentMember me, @PathVariable long recordId, @Valid @RequestBody RecordRequest req) {
        LessonRecord r = enrollments.editRecord(me.organizationId(), me.membershipId(), recordId, req.toInput());
        return StudentDtos.RecordInfo.of(r, null, me.membershipId());
    }

    @DeleteMapping("/lesson-records/{recordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @OrgRole({MembershipRole.TEACHER, MembershipRole.MANAGER})
    void deleteRecord(CurrentMember me, @PathVariable long recordId) {
        enrollments.deleteRecord(me.organizationId(), me.membershipId(), recordId);
    }

    private static boolean isManager(CurrentMember me) {
        return me.role() == MembershipRole.OWNER || me.role() == MembershipRole.MANAGER;
    }

    // ---------- 요청과 응답 ----------

    record SubjectRequest(@NotBlank @Size(max = 50) String name) {
    }

    record ProductRequest(@NotNull Long subjectId, @NotBlank @Size(max = 100) String name, @NotNull ProductKind kind,
                          Integer sessionCount, Integer periodMonths, @NotNull @Min(1) Integer lessonMinutes,
                          @NotNull @Min(0) Long price) {
    }

    record ProductPatch(@Size(min = 1, max = 100) String name, @Min(0) Long price) {
    }

    record StudentRequest(@NotBlank @Size(max = 50) String name, Integer birthYear, String phone, String guardianName,
                          String guardianPhone, @Size(max = 1000) String memo, Boolean active) {
        StudentInput toInput() {
            return new StudentInput(name, birthYear, phone, guardianName, guardianPhone, memo, active);
        }
    }

    record LinkRequest(Long membershipId) {
    }

    record EnrollRequest(@NotNull Long studentId, @NotNull Long productId, @NotNull Long teacherMembershipId,
                         @NotNull LocalDate startsOn) {
    }

    record ActionRequest(Integer months, Integer sessions, Long teacherMembershipId) {
    }

    record RecordRequest(Long enrollmentId, @NotNull LocalDate lessonDate, @Size(max = 2000) String progress,
                         @Size(max = 2000) String homework, @Size(max = 2000) String memo, boolean visibleToStudent) {
        RecordInput toInput() {
            return new RecordInput(lessonDate, progress, homework, memo, visibleToStudent);
        }
    }

    record CatalogResponse(List<SubjectResponse> subjects, List<ProductResponse> products) {
    }

    record SubjectResponse(Long id, String name) {
        static SubjectResponse of(Subject s) {
            return new SubjectResponse(s.getId(), s.getName());
        }
    }

    record ProductResponse(Long id, Long subjectId, String name, ProductKind kind, Short sessionCount,
                           Short periodMonths, int lessonMinutes, long price) {
        static ProductResponse of(Product p) {
            return new ProductResponse(p.getId(), p.getSubjectId(), p.getName(), p.getKind(), p.getSessionCount(),
                    p.getPeriodMonths(), p.getLessonMinutes(), p.getPrice());
        }
    }
}
