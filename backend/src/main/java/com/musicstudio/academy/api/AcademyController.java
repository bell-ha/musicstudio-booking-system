package com.musicstudio.academy.api;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.academy.application.CatalogService;
import com.musicstudio.academy.application.EnrollmentService;
import com.musicstudio.academy.application.EnrollmentService.RecordInput;
import com.musicstudio.academy.application.LessonScheduleService;
import com.musicstudio.academy.application.StudentService;
import com.musicstudio.academy.application.StudentService.Filter;
import com.musicstudio.academy.application.StudentService.StudentInput;
import com.musicstudio.academy.domain.Enrollment;
import com.musicstudio.academy.domain.LessonRecord;
import com.musicstudio.academy.domain.LessonSession;
import com.musicstudio.academy.domain.LessonSessionRepository;
import com.musicstudio.academy.domain.Product;
import com.musicstudio.academy.domain.ProductKind;
import com.musicstudio.academy.domain.SessionKind;
import com.musicstudio.academy.domain.SessionPlanner;
import com.musicstudio.academy.domain.SessionStatus;
import com.musicstudio.academy.domain.Subject;
import com.musicstudio.organization.api.CurrentMember;
import com.musicstudio.organization.api.OrgRole;
import com.musicstudio.organization.domain.MembershipRole;
import com.musicstudio.organization.domain.MembershipStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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
    private final LessonScheduleService lessons;

    AcademyController(CatalogService catalog, StudentService students, EnrollmentService enrollments,
                      LessonScheduleService lessons) {
        this.catalog = catalog;
        this.students = students;
        this.enrollments = enrollments;
        this.lessons = lessons;
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
        StudentService.Detail d = students.mine(me.organizationId(), me.membershipId());
        return StudentDtos.Detail.forStudent(d, me.membershipId(), lessons.summaries(enrollmentIds(d)));
    }

    @GetMapping("/students/{studentId}")
    @OrgRole({MembershipRole.MANAGER, MembershipRole.TEACHER})
    StudentDtos.Detail detail(CurrentMember me, @PathVariable long studentId) {
        boolean manager = isManager(me);
        StudentService.Detail d = students.detail(me.organizationId(), studentId, me.membershipId(), manager);
        return StudentDtos.Detail.of(d, manager, me.membershipId(), lessons.summaries(enrollmentIds(d)));
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
                                   @Valid @RequestBody ActionRequest req) {
        return StudentDtos.EnrollmentInfo.of(enrollments.act(me.organizationId(), enrollmentId, action,
                req.version(), req.months(), req.sessions(), req.teacherMembershipId()));
    }

    // ---------- 레슨 기록 (담당 여부는 서비스가 검사한다) ----------

    @PostMapping("/lesson-records")
    @ResponseStatus(HttpStatus.CREATED)
    @OrgRole({MembershipRole.TEACHER, MembershipRole.MANAGER})
    StudentDtos.RecordInfo writeRecord(CurrentMember me, @Valid @RequestBody NewRecordRequest req) {
        LessonRecord r = enrollments.writeRecord(me.organizationId(), me.membershipId(), req.enrollmentId(),
                req.record().toInput());
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

    // ---------- 레슨 일정·출결 (49~55) ----------

    @PutMapping("/enrollments/{enrollmentId}/schedule")
    @OrgRole(MembershipRole.MANAGER)
    LessonScheduleService.Filled schedule(CurrentMember me, @PathVariable long enrollmentId,
                                          @Valid @RequestBody ScheduleRequest req) {
        return lessons.setSchedule(me.organizationId(), enrollmentId, req.version(), req.from(),
                req.slots().stream().map(x -> new SessionPlanner.Slot(x.dayOfWeek(), x.startTime())).toList());
    }

    /** 강사는 자기 회차만. 관리자는 기관 전체, teacherId로 거르거나 mine으로 자기 것만 */
    @GetMapping("/sessions")
    @OrgRole({MembershipRole.MANAGER, MembershipRole.TEACHER})
    List<SessionResponse> sessions(CurrentMember me, @RequestParam(required = false) LocalDate from,
                                   @RequestParam(required = false) LocalDate to,
                                   @RequestParam(required = false) Long teacherId,
                                   @RequestParam(defaultValue = "false") boolean mine,
                                   @RequestParam(defaultValue = "false") boolean unmarked) {
        Long teacher = !isManager(me) || mine ? Long.valueOf(me.membershipId()) : teacherId;
        return lessons.list(me.organizationId(), from, to, teacher, unmarked).stream()
                .map(r -> SessionResponse.of(r, me.membershipId(), true)).toList();
    }

    @GetMapping("/students/me/sessions")
    @OrgRole(MembershipRole.STUDENT)
    List<SessionResponse> mySessions(CurrentMember me, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return lessons.mine(me.organizationId(), me.membershipId(), from, to).stream()
                .map(r -> SessionResponse.of(r, me.membershipId(), false)).toList();
    }

    @PutMapping("/sessions/{sessionId}/attendance")
    @OrgRole({MembershipRole.MANAGER, MembershipRole.TEACHER})
    AttendanceResponse attendance(CurrentMember me, @PathVariable long sessionId,
                                  @Valid @RequestBody AttendanceRequest req) {
        LessonScheduleService.Marked m = lessons.mark(me.organizationId(), sessionId, me.membershipId(), isManager(me),
                req.status(), req.note());
        return new AttendanceResponse(m.session().getId(), m.session().getStatus(), m.session().getNote(), m.shortfall());
    }

    @PostMapping("/sessions/{sessionId}/cancel")
    @OrgRole(MembershipRole.MANAGER)
    AttendanceResponse cancelSession(CurrentMember me, @PathVariable long sessionId, @Valid @RequestBody CancelRequest req) {
        LessonScheduleService.Marked m = lessons.cancel(me.organizationId(), sessionId, me.membershipId(), req.reason());
        return new AttendanceResponse(m.session().getId(), m.session().getStatus(), m.session().getNote(), m.shortfall());
    }

    @PostMapping("/sessions/cancel-day")
    @OrgRole(MembershipRole.MANAGER)
    LessonScheduleService.DayCanceled cancelDay(CurrentMember me, @Valid @RequestBody CancelDayRequest req) {
        return lessons.cancelDay(me.organizationId(), req.date(), me.membershipId(), req.reason());
    }

    @PostMapping("/enrollments/{enrollmentId}/makeups")
    @ResponseStatus(HttpStatus.CREATED)
    @OrgRole(MembershipRole.MANAGER)
    MakeupResponse makeup(CurrentMember me, @PathVariable long enrollmentId, @Valid @RequestBody MakeupRequest req) {
        LessonSession s = lessons.makeup(me.organizationId(), enrollmentId, req.startsAt());
        return new MakeupResponse(s.getId(), s.getStartsAt(), s.getEndsAt(), s.getLocalDate());
    }

    private static List<Long> enrollmentIds(StudentService.Detail d) {
        return d.enrollments().stream().map(r -> r.getEnrollment().getId()).toList();
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

    record StudentRequest(@NotBlank @Size(max = 50) String name, @Min(1900) @Max(2100) Integer birthYear, String phone, String guardianName,
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

    /** version: 화면이 읽어 간 수강 버전. 그사이 다른 관리자가 바꿨으면 409 (이중 연장 방지). */
    record ActionRequest(@NotNull Long version, Integer months, Integer sessions, Long teacherMembershipId) {
    }

    /** 34번: 어느 수강의 기록인지가 필요하다. */
    record NewRecordRequest(@NotNull Long enrollmentId, @NotNull LocalDate lessonDate,
                            @Size(max = 2000) String progress, @Size(max = 2000) String homework,
                            @Size(max = 2000) String memo, boolean visibleToStudent) {
        RecordRequest record() {
            return new RecordRequest(lessonDate, progress, homework, memo, visibleToStudent);
        }
    }

    record RecordRequest(@NotNull LocalDate lessonDate, @Size(max = 2000) String progress,
                         @Size(max = 2000) String homework, @Size(max = 2000) String memo, boolean visibleToStudent) {
        RecordInput toInput() {
            return new RecordInput(lessonDate, progress, homework, memo, visibleToStudent);
        }
    }

    record ScheduleRequest(@NotNull Long version, LocalDate from, @NotNull @Size(min = 1, max = 3) List<@Valid SlotRequest> slots) {
    }

    record SlotRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime) {
    }

    record AttendanceRequest(@NotNull SessionStatus status, @Size(max = 500) String note) {
    }

    record CancelRequest(@NotBlank @Size(max = 500) String reason) {
    }

    record CancelDayRequest(@NotNull LocalDate date, @NotBlank @Size(max = 500) String reason) {
    }

    record MakeupRequest(@NotNull Instant startsAt) {
    }

    record AttendanceResponse(Long id, SessionStatus status, String note, int shortfall) {
    }

    record MakeupResponse(Long id, Instant startsAt, Instant endsAt, LocalDate localDate) {
    }

    /**
     * 회차. 학생에게는 휴강 사유만 준다(결석 메모는 강사용). teacherActive: 비활성 강사의 회차는 화면이 경고한다.
     * mine: 요청한 사람이 이 회차의 강사인가(출결 버튼을 보일지).
     */
    record SessionResponse(Long id, Long enrollmentId, Long studentId, String studentName, String subjectName,
                           Long teacherMembershipId, String teacherName, boolean teacherActive, SessionKind kind,
                           SessionStatus status, String note, Instant startsAt, Instant endsAt, LocalDate localDate,
                           boolean mine) {
        static SessionResponse of(LessonSessionRepository.SessionRow r, long requester, boolean staff) {
            LessonSession s = r.getSession();
            String note = staff || s.getStatus() == SessionStatus.CANCELED ? s.getNote() : null;
            return new SessionResponse(s.getId(), s.getEnrollmentId(), s.getStudentId(), r.getStudentName(),
                    r.getSubjectName(), s.getTeacherMembershipId(), r.getTeacherName(),
                    r.getTeacherStatus() == MembershipStatus.ACTIVE, s.getKind(), s.getStatus(), note, s.getStartsAt(),
                    s.getEndsAt(), s.getLocalDate(), s.getTeacherMembershipId() == requester);
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
