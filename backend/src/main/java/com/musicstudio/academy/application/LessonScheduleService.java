package com.musicstudio.academy.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.academy.domain.Enrollment;
import com.musicstudio.academy.domain.EnrollmentRepository;
import com.musicstudio.academy.domain.EnrollmentStatus;
import com.musicstudio.academy.domain.LessonSession;
import com.musicstudio.academy.domain.LessonSessionRepository;
import com.musicstudio.academy.domain.LessonSessionRepository.SessionRow;
import com.musicstudio.academy.domain.LessonSlot;
import com.musicstudio.academy.domain.LessonSlotRepository;
import com.musicstudio.academy.domain.Product;
import com.musicstudio.academy.domain.ProductKind;
import com.musicstudio.academy.domain.ProductRepository;
import com.musicstudio.academy.domain.SessionKind;
import com.musicstudio.academy.domain.SessionPlanner;
import com.musicstudio.academy.domain.SessionPlanner.Planned;
import com.musicstudio.academy.domain.SessionPlanner.Slot;
import com.musicstudio.academy.domain.SessionStatus;
import com.musicstudio.academy.domain.Student;
import com.musicstudio.academy.domain.StudentRepository;
import com.musicstudio.common.error.ApiException;
import com.musicstudio.organization.domain.OrganizationRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * 레슨 일정·출결 (UC-49~53). 설계는 두 세션 합본(비공개 계획 문서 27).
 *
 * <p>규칙 몇 개로 모든 동작을 설명한다.
 * <ul>
 *   <li>회차는 고정 일정에서 수강 끝까지 미리 만든다. 다시 만들 때 지우는 것은 "출결 안 한, 지금 이후에 시작하는" 회차뿐이다.
 *       지난 회차와 출결한 회차는 일정을 바꾸거나 수강을 멈춰도 그대로다.</li>
 *   <li>횟수권 불변식: 차감(출석·결석) + SCHEDULED(지난 미처리·보강 포함) + 부족분 = 총 회차, 부족분 ≥ 0.
 *       {@link #fill}이 모자라면 맨 뒤에 붙이고 남으면 맨 뒤의 미래 정규 회차부터 지운다.
 *       부족분은 자동으로 이어 붙일 자리를 26주 안에서 못 찾은 수이고, 저장하지 않고 매번 센다.</li>
 *   <li>겹침: 원장이 시각을 정한 경로(일정 저장·재개·연장·강사 변경·보강)는 409와 날짜 목록,
 *       출결·휴강 뒤 자동 이어 붙이기는 겹치는 슬롯을 건너뛴다(출결 저장이 남의 일정 때문에 실패하면 안 된다).</li>
 *   <li>잠금: 모든 경로가 {@link LessonLocks}(수강 → 강사 → 원생, id 오름차순)를 잡은 뒤 다시 읽는다.
 *       DB의 EXCLUDE 두 개는 마지막 방어선이다.</li>
 * </ul>
 */
@Service
public class LessonScheduleService {

    static final int MAX_SLOTS = 3;
    /** 자동 이어 붙이기가 자리를 찾는 범위 */
    static final int SEARCH_WEEKS = 26;
    /** 횟수권 일정을 처음 정할 때 회차를 만드는 범위(주 1회 횟수권 150회까지) */
    static final int COUNT_HORIZON_YEARS = 3;
    static final int MAX_LIST_DAYS = 42;
    static final int UNMARKED_DAYS = 30;

    private static final Set<SessionStatus> MARKS = EnumSet.of(SessionStatus.ATTENDED, SessionStatus.ABSENT,
            SessionStatus.EXCUSED);

    private enum Mode { STRICT, AUTO }

    private final EnrollmentRepository enrollments;
    private final ProductRepository products;
    private final StudentRepository students;
    private final LessonSlotRepository slots;
    private final LessonSessionRepository sessions;
    private final OrganizationRepository organizations;
    private final LessonLocks locks;
    private final EntityManager entityManager;
    private final Clock clock;

    LessonScheduleService(EnrollmentRepository enrollments, ProductRepository products, StudentRepository students,
                          LessonSlotRepository slots, LessonSessionRepository sessions,
                          OrganizationRepository organizations, LessonLocks locks, EntityManager entityManager,
                          Clock clock) {
        this.enrollments = enrollments;
        this.products = products;
        this.students = students;
        this.slots = slots;
        this.sessions = sessions;
        this.organizations = organizations;
        this.locks = locks;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    // ---------- UC-49 고정 일정 ----------

    @Transactional
    public Filled setSchedule(long orgId, long enrollmentId, long expectedVersion, LocalDate from, List<Slot> wanted) {
        Enrollment e = enrollment(orgId, enrollmentId);
        lock(e, List.of());
        if (e.getVersion() != expectedVersion) {
            throw ApiException.conflict("CONFLICTING_UPDATE", "다른 관리자가 먼저 바꿨습니다. 다시 불러와 주세요");
        }
        if (!e.isOpen()) {
            throw ApiException.conflict("INVALID_TRANSITION", "끝난 수강은 일정을 정할 수 없습니다");
        }
        Product p = product(e);
        validateSlots(wanted, p.getLessonMinutes());

        slots.deleteByEnrollment(e.getId());
        slots.saveAll(wanted.stream().map(s -> new LessonSlot(e.getId(), s.day(), s.time())).toList());
        LocalDate start = max(from == null ? today(orgId) : from, e.getStartsOn());
        deleteFuture(e, start, false);
        Filled filled = fill(e, p, Mode.STRICT, start);
        entityManager.lock(e, LockModeType.OPTIMISTIC_FORCE_INCREMENT); // 일정도 수강의 일부라 낡은 화면을 막는다
        return filled;
    }

    // ---------- 수강 동작과 회차 (EnrollmentService.act가 같은 트랜잭션에서 부른다) ----------

    /** 상태를 바꾸기 전에 잡는다. 강사 변경이면 새 강사도 함께(수강 → 강사들 → 원생). 잡은 뒤 다시 읽는다 */
    void lockBeforeAction(Enrollment e, Long newTeacher) {
        lock(e, newTeacher == null ? List.of() : List.of(newTeacher));
    }

    void afterAction(Enrollment e, String action, Long oldTeacher) {
        Product p = product(e);
        LocalDate today = today(e.getOrganizationId());
        switch (action) {
            case "pause", "end", "refund" -> deleteFuture(e, LocalDate.MIN, true);
            case "resume", "extend" -> fill(e, p, Mode.STRICT, today);
            case "change-teacher" -> reassign(e, oldTeacher);
            default -> {
            }
        }
    }

    /** 앞으로의 SCHEDULED 회차만 새 강사로. 새 강사와 겹치면 수강 변경 전체를 되돌린다(409와 날짜 목록) */
    private void reassign(Enrollment e, Long oldTeacher) {
        if (Objects.equals(oldTeacher, e.getTeacherMembershipId())) {
            return;
        }
        Instant now = clock.instant();
        List<LessonSession> future = sessions.findByEnrollmentIdOrderByStartsAtAsc(e.getId()).stream()
                .filter(s -> s.getStatus() == SessionStatus.SCHEDULED && !s.getStartsAt().isBefore(now)).toList();
        List<LessonSession> busy = sessions.findOccupyingTeacher(e.getTeacherMembershipId(), now).stream()
                .filter(b -> !b.getEnrollmentId().equals(e.getId())).toList();
        List<Conflict> conflicts = new ArrayList<>();
        for (LessonSession s : future) {
            busy.stream().filter(b -> b.overlaps(s.getStartsAt(), s.getEndsAt())).findFirst()
                    .ifPresent(b -> conflicts.add(Conflict.of(s.getLocalDate(), s.getStartsAt(), s.getEndsAt(), "TEACHER")));
            s.assignTeacher(e.getTeacherMembershipId());
        }
        if (!conflicts.isEmpty()) {
            throw scheduleConflict(conflicts);
        }
        flush();
    }

    // ---------- UC-51 출결 ----------

    @Transactional
    public Marked mark(long orgId, long sessionId, long requester, boolean manager, SessionStatus status, String note) {
        if (!MARKS.contains(status)) {
            throw ApiException.invalid("INVALID_STATUS", "출석, 결석, 사전 결석 중에서 골라 주세요");
        }
        LessonSession s = session(orgId, sessionId);
        Enrollment e = enrollment(orgId, s.getEnrollmentId());
        lock(e, List.of(s.getTeacherMembershipId()));
        entityManager.refresh(s);
        if (!manager && !s.getTeacherMembershipId().equals(requester)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "NOT_ASSIGNED", "맡은 회차만 출결을 남길 수 있습니다");
        }
        if (s.getStatus() == SessionStatus.CANCELED) {
            throw ApiException.conflict("SESSION_CANCELED", "휴강한 회차입니다");
        }
        Instant now = clock.instant();
        if (status != SessionStatus.EXCUSED && now.isBefore(s.getStartsAt())) {
            throw ApiException.policyViolation("TOO_EARLY", "출석·결석은 레슨이 시작한 뒤에 남길 수 있어요. 미리 연락받았다면 사전 결석으로 남겨 주세요");
        }
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        if (s.getStatus() != status || !Objects.equals(s.getNote(), cleanNote)) { // 같은 값이면 그대로 (멱등)
            s.mark(status, cleanNote, requester, now);
            flush();
            if (product(e).getKind() == ProductKind.COUNT) {
                fill(e, product(e), Mode.AUTO, today(orgId));
            }
        }
        return new Marked(s, summary(e).shortfall());
    }

    // ---------- UC-52 휴강·보강 ----------

    @Transactional
    public Marked cancel(long orgId, long sessionId, long requester, String reason) {
        LessonSession s = session(orgId, sessionId);
        Enrollment e = enrollment(orgId, s.getEnrollmentId());
        lock(e, List.of(s.getTeacherMembershipId()));
        entityManager.refresh(s);
        if (s.getStatus() != SessionStatus.SCHEDULED) {
            throw ApiException.conflict("ALREADY_MARKED", "출결을 남긴 회차는 휴강할 수 없습니다");
        }
        s.mark(SessionStatus.CANCELED, reason.strip(), requester, clock.instant());
        flush();
        if (product(e).getKind() == ProductKind.COUNT) {
            fill(e, product(e), Mode.AUTO, today(orgId));
        }
        return new Marked(s, summary(e).shortfall());
    }

    /** 그날 SCHEDULED를 모두 휴강하고, 횟수권은 수강마다 뒤에 이어 붙인다. 잠금은 관련 수강·강사·원생 모두를 한 번에 */
    @Transactional
    public DayCanceled cancelDay(long orgId, LocalDate date, long requester, String reason) {
        List<LessonSession> day = sessions.findByOrganizationIdAndLocalDateAndStatus(orgId, date, SessionStatus.SCHEDULED);
        if (day.isEmpty()) {
            return new DayCanceled(0, 0);
        }
        List<Enrollment> affected = enrollments.findAllById(day.stream().map(LessonSession::getEnrollmentId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Set<Long> teachers = new HashSet<>();
        affected.forEach(e -> teachers.add(e.getTeacherMembershipId()));
        day.forEach(s -> teachers.add(s.getTeacherMembershipId()));
        locks.lock(affected.stream().map(Enrollment::getId).toList(), teachers,
                affected.stream().map(Enrollment::getStudentId).toList());
        affected.forEach(entityManager::refresh);
        // 잠그기 전에 읽은 목록은 낡았을 수 있다
        day = sessions.findByOrganizationIdAndLocalDateAndStatus(orgId, date, SessionStatus.SCHEDULED);
        Instant now = clock.instant();
        day.forEach(s -> s.mark(SessionStatus.CANCELED, reason.strip(), requester, now));
        flush();
        int appended = 0;
        LocalDate today = today(orgId);
        for (Enrollment e : affected) {
            Product p = product(e);
            if (p.getKind() == ProductKind.COUNT) {
                appended += fill(e, p, Mode.AUTO, today).created();
            }
        }
        return new DayCanceled(day.size(), appended);
    }

    @Transactional
    public LessonSession makeup(long orgId, long enrollmentId, Instant startsAt) {
        Enrollment e = enrollment(orgId, enrollmentId);
        lock(e, List.of());
        if (e.getStatus() != EnrollmentStatus.ACTIVE) {
            throw ApiException.conflict("INVALID_TRANSITION", "진행 중인 수강에만 보강을 넣을 수 있습니다");
        }
        Product p = product(e);
        Instant now = clock.instant();
        if (startsAt.isBefore(now)) {
            throw ApiException.policyViolation("IN_THE_PAST", "지난 시각에는 보강을 넣을 수 없습니다");
        }
        ZoneId zone = zone(orgId);
        LocalTime localStart = startsAt.atZone(zone).toLocalTime();
        if (SessionPlanner.crossesMidnight(localStart, p.getLessonMinutes())) {
            throw ApiException.policyViolation("CROSSES_MIDNIGHT", "레슨은 자정을 넘을 수 없습니다");
        }
        if (p.getKind() == ProductKind.COUNT) {
            List<LessonSession> mine = sessions.findByEnrollmentIdOrderByStartsAtAsc(e.getId());
            boolean futureRegular = mine.stream().anyMatch(s -> isFutureRegular(s, now));
            if (summary(e, p, mine).shortfall() == 0 && !futureRegular) {
                throw ApiException.policyViolation("NO_REMAINING", "남은 회차가 없습니다. 연장한 뒤에 넣어 주세요");
            }
        }
        Instant endsAt = startsAt.plus(Duration.ofMinutes(p.getLessonMinutes()));
        List<LessonSession> busy = sessions.findOccupying(List.of(e.getTeacherMembershipId()), List.of(e.getStudentId()), now);
        Optional<LessonSession> clash = busy.stream().filter(b -> b.overlaps(startsAt, endsAt)).findFirst();
        LocalDate localDate = startsAt.atZone(zone).toLocalDate();
        if (clash.isPresent()) {
            throw scheduleConflict(List.of(Conflict.of(localDate, startsAt, endsAt, who(clash.get(), e))));
        }
        LessonSession made = sessions.save(new LessonSession(e, SessionKind.MAKEUP, startsAt, endsAt, localDate));
        flush();
        if (p.getKind() == ProductKind.COUNT) {
            fill(e, p, Mode.AUTO, today(orgId)); // 총 회차를 넘으면 맨 뒤 정규 회차가 빠진다
        }
        return made;
    }

    // ---------- UC-50, 53 조회 ----------

    @Transactional(readOnly = true)
    public List<SessionRow> list(long orgId, LocalDate from, LocalDate to, Long teacherId, boolean unmarked) {
        if (unmarked) {
            Instant now = clock.instant();
            return sessions.findUnmarked(orgId, now, now.minus(UNMARKED_DAYS, ChronoUnit.DAYS), teacherId);
        }
        requireRange(from, to);
        return sessions.findRows(orgId, from, to, teacherId, null);
    }

    @Transactional(readOnly = true)
    public List<SessionRow> mine(long orgId, long membershipId, LocalDate from, LocalDate to) {
        requireRange(from, to);
        Student student = students.findByOrganizationIdAndMembershipId(orgId, membershipId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "NOT_LINKED",
                        "학원에서 계정을 연결하면 일정이 보여요"));
        return sessions.findRows(orgId, from, to, null, student.getId());
    }

    /** 원생 상세의 수강마다: 고정 일정, 남은 회차(횟수권), 부족분, 다음·마지막 회차 */
    @Transactional(readOnly = true)
    public Map<Long, Summary> summaries(Collection<Long> enrollmentIds) {
        if (enrollmentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<LessonSlot>> slotsBy = slots.findByEnrollmentIdIn(enrollmentIds).stream()
                .collect(Collectors.groupingBy(LessonSlot::getEnrollmentId));
        Map<Long, List<LessonSession>> sessionsBy = sessions.findByEnrollmentIdIn(enrollmentIds).stream()
                .collect(Collectors.groupingBy(LessonSession::getEnrollmentId));
        Map<Long, Summary> out = new HashMap<>();
        for (Enrollment e : enrollments.findAllById(enrollmentIds)) {
            Summary s = summary(e, product(e), sessionsBy.getOrDefault(e.getId(), List.of()));
            List<Slot> mine = slotsBy.getOrDefault(e.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(LessonSlot::getDayOfWeek).thenComparing(LessonSlot::getStartTime))
                    .map(x -> new Slot(x.getDayOfWeek(), x.getStartTime())).toList();
            out.put(e.getId(), new Summary(mine, s.remaining(), s.shortfall(), s.next(), s.last()));
        }
        return out;
    }

    // ---------- 회차 맞추기 ----------

    /**
     * 기간권: 기간 안의 빈 슬롯을 채운다. 횟수권: 불변식(차감 + SCHEDULED + 부족분 = 총 회차)을 맞춘다.
     * STRICT는 겹치면 409, AUTO는 겹치는 슬롯을 건너뛰고 26주 안에서 다음 자리를 찾는다.
     */
    private Filled fill(Enrollment e, Product p, Mode mode, LocalDate fromDate) {
        if (e.getStatus() != EnrollmentStatus.ACTIVE) {
            return Filled.NONE;
        }
        List<Slot> pattern = slots.findByEnrollmentIdOrderByDayOfWeekAscStartTimeAsc(e.getId()).stream()
                .map(x -> new Slot(x.getDayOfWeek(), x.getStartTime())).toList();
        Instant now = clock.instant();
        List<LessonSession> mine = sessions.findByEnrollmentIdOrderByStartsAtAsc(e.getId());
        LocalDate start = max(fromDate, e.getStartsOn());
        ZoneId zone = zone(e.getOrganizationId());

        int need;
        List<Planned> candidates;
        if (p.getKind() == ProductKind.PERIOD) {
            if (pattern.isEmpty()) {
                return Filled.NONE;
            }
            need = Integer.MAX_VALUE;
            candidates = SessionPlanner.between(pattern, zone, p.getLessonMinutes(), start, e.getEndsOn());
        } else {
            need = e.getTotalSessions() - (int) count(mine, SessionStatus::deducts)
                    - (int) count(mine, st -> st == SessionStatus.SCHEDULED);
            if (need < 0) {
                trimTail(mine, -need, now);
                return Filled.NONE;
            }
            if (need == 0 || pattern.isEmpty()) {
                return Filled.NONE;
            }
            // 자동 이어 붙이기는 맨 뒤 정규 회차 다음부터 26주, 처음 정할 때는 시작일부터 넉넉히
            LocalDate tail = mode == Mode.AUTO
                    ? max(start, mine.stream().filter(s -> s.getKind() == SessionKind.REGULAR)
                            .map(LessonSession::getLocalDate).max(LocalDate::compareTo)
                            .map(d -> d.plusDays(1)).orElse(start))
                    : start;
            LocalDate until = mode == Mode.AUTO ? tail.plusWeeks(SEARCH_WEEKS) : tail.plusYears(COUNT_HORIZON_YEARS);
            candidates = SessionPlanner.between(pattern, zone, p.getLessonMinutes(), tail, until);
        }

        Set<Instant> taken = mine.stream().map(LessonSession::getStartsAt).collect(Collectors.toSet());
        List<LessonSession> busy = sessions.findOccupying(List.of(e.getTeacherMembershipId()), List.of(e.getStudentId()), now);
        List<LessonSession> created = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();
        for (Planned c : candidates) {
            if (created.size() >= need) {
                break;
            }
            if (c.startsAt().isBefore(now) || taken.contains(c.startsAt())) {
                continue;
            }
            Optional<LessonSession> clash = busy.stream().filter(b -> b.overlaps(c.startsAt(), c.endsAt())).findFirst();
            if (clash.isPresent()) {
                if (mode == Mode.STRICT) {
                    conflicts.add(Conflict.of(c.localDate(), c.startsAt(), c.endsAt(), who(clash.get(), e)));
                }
                continue; // AUTO: 건너뛰고 다음 슬롯
            }
            created.add(new LessonSession(e, SessionKind.REGULAR, c.startsAt(), c.endsAt(), c.localDate()));
        }
        if (!conflicts.isEmpty()) {
            throw scheduleConflict(conflicts);
        }
        sessions.saveAll(created);
        flush();
        return created.isEmpty() ? Filled.NONE
                : new Filled(created.size(), created.getFirst().getLocalDate(), created.getLast().getLocalDate());
    }

    /** 남는 만큼 맨 뒤의 미래 정규 회차부터 지운다. 관리자가 넣은 보강은 지우지 않는다 */
    private void trimTail(List<LessonSession> mine, int count, Instant now) {
        List<LessonSession> tail = mine.stream().filter(s -> isFutureRegular(s, now))
                .sorted(Comparator.comparing(LessonSession::getStartsAt).reversed()).limit(count).toList();
        sessions.deleteAll(tail);
        flush();
    }

    /** 지금 이후에 시작하는, 출결 안 한 회차를 지운다(진행 중인 회차와 지난 회차는 남긴다) */
    private void deleteFuture(Enrollment e, LocalDate fromDate, boolean includeMakeups) {
        Instant now = clock.instant();
        List<LessonSession> doomed = sessions.findByEnrollmentIdOrderByStartsAtAsc(e.getId()).stream()
                .filter(s -> s.getStatus() == SessionStatus.SCHEDULED && !s.getStartsAt().isBefore(now)
                        && !s.getLocalDate().isBefore(fromDate)
                        && (includeMakeups || s.getKind() == SessionKind.REGULAR))
                .toList();
        sessions.deleteAll(doomed);
        flush();
    }

    private Summary summary(Enrollment e) {
        return summary(e, product(e), sessions.findByEnrollmentIdOrderByStartsAtAsc(e.getId()));
    }

    private Summary summary(Enrollment e, Product p, List<LessonSession> mine) {
        Instant now = clock.instant();
        Optional<LocalDate> next = mine.stream()
                .filter(s -> s.getStatus() == SessionStatus.SCHEDULED && !s.getStartsAt().isBefore(now))
                .map(LessonSession::getLocalDate).min(LocalDate::compareTo);
        Optional<LocalDate> last = mine.stream().filter(s -> s.getStatus().occupies())
                .map(LessonSession::getLocalDate).max(LocalDate::compareTo);
        if (p.getKind() != ProductKind.COUNT) {
            return new Summary(List.of(), null, 0, next.orElse(null), last.orElse(null));
        }
        int deducted = (int) count(mine, SessionStatus::deducts);
        int scheduled = (int) count(mine, st -> st == SessionStatus.SCHEDULED);
        return new Summary(List.of(), e.getTotalSessions() - deducted,
                Math.max(0, e.getTotalSessions() - deducted - scheduled), next.orElse(null), last.orElse(null));
    }

    // ---------- 도움 ----------

    /** 수강 → 강사(지금 강사 + extra) → 원생 순서로 잡고 다시 읽는다 */
    private void lock(Enrollment e, List<Long> extraTeachers) {
        List<Long> teachers = new ArrayList<>(extraTeachers);
        teachers.add(e.getTeacherMembershipId());
        locks.lock(List.of(e.getId()), teachers, List.of(e.getStudentId()));
        entityManager.refresh(e);
    }

    private static void validateSlots(List<Slot> wanted, int minutes) {
        if (wanted == null || wanted.isEmpty() || wanted.size() > MAX_SLOTS) {
            throw ApiException.invalid("INVALID_SLOTS", "고정 일정은 주 1~3회로 정해 주세요");
        }
        for (Slot s : wanted) {
            if (s.day() == null || s.time() == null || s.time().getMinute() % 5 != 0 || s.time().getSecond() != 0) {
                throw ApiException.invalid("INVALID_SLOTS", "요일과 시각(5분 단위)을 확인해 주세요");
            }
            if (SessionPlanner.crossesMidnight(s.time(), minutes)) {
                throw ApiException.policyViolation("CROSSES_MIDNIGHT", "레슨은 자정을 넘을 수 없습니다");
            }
        }
        // 같은 요일의 두 슬롯이 겹치면 같은 원생이 자기와 겹친다
        for (int i = 0; i < wanted.size(); i++) {
            for (int j = i + 1; j < wanted.size(); j++) {
                Slot a = wanted.get(i);
                Slot b = wanted.get(j);
                if (a.day() == b.day() && Math.abs(Duration.between(a.time(), b.time()).toMinutes()) < minutes) {
                    throw ApiException.invalid("INVALID_SLOTS", "같은 요일의 두 레슨 시간이 겹칩니다");
                }
            }
        }
    }

    private static boolean isFutureRegular(LessonSession s, Instant now) {
        return s.getStatus() == SessionStatus.SCHEDULED && s.getKind() == SessionKind.REGULAR
                && !s.getStartsAt().isBefore(now);
    }

    private static String who(LessonSession clash, Enrollment e) {
        return clash.getTeacherMembershipId().equals(e.getTeacherMembershipId()) ? "TEACHER" : "STUDENT";
    }

    private static long count(List<LessonSession> list, java.util.function.Predicate<SessionStatus> p) {
        return list.stream().filter(s -> p.test(s.getStatus())).count();
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_LIST_DAYS) {
            throw ApiException.invalid("INVALID_RANGE", "기간은 " + MAX_LIST_DAYS + "일 이하로 정해 주세요");
        }
    }

    /** EXCLUDE가 막은 겹침(잠금을 빠뜨린 경로가 있을 때만 생긴다)도 같은 409로 */
    private void flush() {
        try {
            entityManager.flush();
        } catch (DataIntegrityViolationException | jakarta.persistence.PersistenceException ex) {
            String message = String.valueOf(ex.getMessage()) + " " + String.valueOf(ex.getCause());
            if (message.contains("ex_session_teacher") || message.contains("ex_session_student")) {
                throw scheduleConflict(List.of());
            }
            throw ex;
        }
    }

    private static ApiException scheduleConflict(List<Conflict> conflicts) {
        return ApiException.conflict("SCHEDULE_CONFLICT", "강사나 원생의 다른 레슨과 시간이 겹칩니다")
                .with("conflicts", conflicts.stream().limit(20).toList())
                .with("conflictCount", conflicts.size());
    }

    private Enrollment enrollment(long orgId, long id) {
        return enrollments.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> ApiException.notFound("수강을 찾을 수 없습니다"));
    }

    private LessonSession session(long orgId, long id) {
        return sessions.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> ApiException.notFound("회차를 찾을 수 없습니다"));
    }

    private Product product(Enrollment e) {
        return products.findById(e.getProductId()).orElseThrow();
    }

    private ZoneId zone(long orgId) {
        return ZoneId.of(organizations.findById(orgId).orElseThrow().getTimezone());
    }

    LocalDate today(long orgId) {
        return LocalDate.now(clock.withZone(zone(orgId)));
    }

    public record Filled(int created, LocalDate firstDate, LocalDate lastDate) {
        static final Filled NONE = new Filled(0, null, null);
    }

    public record Marked(LessonSession session, int shortfall) {
    }

    public record DayCanceled(int canceled, int appended) {
    }

    /** remaining: 횟수권의 남은 회차(총 − 차감), 기간권은 null. shortfall: 자동으로 못 붙인 회차 */
    public record Summary(List<Slot> slots, Integer remaining, int shortfall, LocalDate next, LocalDate last) {
    }

    public record Conflict(LocalDate date, Instant startsAt, Instant endsAt, String with) {
        static Conflict of(LocalDate date, Instant startsAt, Instant endsAt, String with) {
            return new Conflict(date, startsAt, endsAt, with);
        }
    }
}
