package com.musicstudio.academy.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LessonSessionRepository extends JpaRepository<LessonSession, Long> {

    Optional<LessonSession> findByIdAndOrganizationId(Long id, Long organizationId);

    List<LessonSession> findByEnrollmentIdOrderByStartsAtAsc(Long enrollmentId);

    List<LessonSession> findByEnrollmentIdIn(Collection<Long> enrollmentIds);

    /** 겹침 검사용: 이 강사나 이 원생이 시간을 차지하는 회차 중 since 이후에 끝나는 것 */
    @Query("""
            select s from LessonSession s
            where (s.teacherMembershipId in :teachers or s.studentId in :students)
              and s.status in (com.musicstudio.academy.domain.SessionStatus.SCHEDULED,
                               com.musicstudio.academy.domain.SessionStatus.ATTENDED,
                               com.musicstudio.academy.domain.SessionStatus.ABSENT)
              and s.endsAt > :since""")
    List<LessonSession> findOccupying(Collection<Long> teachers, Collection<Long> students, Instant since);

    /** 강사 변경 검사용: 이 강사가 시간을 차지하는 회차 */
    @Query("""
            select s from LessonSession s
            where s.teacherMembershipId = :teacher
              and s.status in (com.musicstudio.academy.domain.SessionStatus.SCHEDULED,
                               com.musicstudio.academy.domain.SessionStatus.ATTENDED,
                               com.musicstudio.academy.domain.SessionStatus.ABSENT)
              and s.endsAt > :since""")
    List<LessonSession> findOccupyingTeacher(Long teacher, Instant since);

    List<LessonSession> findByOrganizationIdAndLocalDateAndStatus(Long organizationId, LocalDate localDate,
                                                                  SessionStatus status);

    /** 일정 화면: 회차와 원생·과목·강사 이름 */
    @Query("""
            select s as session, st.name as studentName, subj.name as subjectName, u.name as teacherName,
                   e.status as enrollmentStatus, m.status as teacherStatus
            from LessonSession s
              join Enrollment e on e.id = s.enrollmentId
              join Student st on st.id = s.studentId
              join Product p on p.id = e.productId
              join Subject subj on subj.id = p.subjectId
              join com.musicstudio.organization.domain.Membership m on m.id = s.teacherMembershipId
              join com.musicstudio.account.domain.UserAccount u on u.id = m.userId
            where s.organizationId = :orgId
              and s.localDate between :from and :to
              and (:teacherId is null or s.teacherMembershipId = :teacherId)
              and (:studentId is null or s.studentId = :studentId)
            order by s.startsAt, s.id""")
    List<SessionRow> findRows(Long orgId, LocalDate from, LocalDate to, Long teacherId, Long studentId);

    /** 출결을 안 남긴 지난 회차(최근 since 이후) */
    @Query("""
            select s as session, st.name as studentName, subj.name as subjectName, u.name as teacherName,
                   e.status as enrollmentStatus, m.status as teacherStatus
            from LessonSession s
              join Enrollment e on e.id = s.enrollmentId
              join Student st on st.id = s.studentId
              join Product p on p.id = e.productId
              join Subject subj on subj.id = p.subjectId
              join com.musicstudio.organization.domain.Membership m on m.id = s.teacherMembershipId
              join com.musicstudio.account.domain.UserAccount u on u.id = m.userId
            where s.organizationId = :orgId
              and s.status = com.musicstudio.academy.domain.SessionStatus.SCHEDULED
              and s.endsAt < :now and s.startsAt >= :since
              and (:teacherId is null or s.teacherMembershipId = :teacherId)
            order by s.startsAt, s.id""")
    List<SessionRow> findUnmarked(Long orgId, Instant now, Instant since, Long teacherId);

    interface SessionRow {
        LessonSession getSession();

        String getStudentName();

        String getSubjectName();

        String getTeacherName();

        EnrollmentStatus getEnrollmentStatus();

        com.musicstudio.organization.domain.MembershipStatus getTeacherStatus();
    }
}
