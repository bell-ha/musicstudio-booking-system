package com.musicstudio.academy.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<Student> findByOrganizationIdAndMembershipId(Long organizationId, Long membershipId);

    /**
     * 원생 목록 (UC-44, 45): 원생마다 진행 중인 수강(정지 포함)을 붙인다. 쿼리 1번.
     * 필터는 메모리에서 한다. 기관당 원생이 수백 명이라 동적 SQL을 조립하는 것보다 단순하다.
     */
    @Query("""
            select s as student, e as enrollment, p.subjectId as subjectId, p.name as productName,
                   subj.name as subjectName, u.name as teacherName
            from Student s
              left join Enrollment e on e.studentId = s.id
                   and e.status in (com.musicstudio.academy.domain.EnrollmentStatus.ACTIVE,
                                    com.musicstudio.academy.domain.EnrollmentStatus.PAUSED)
              left join Product p on p.id = e.productId
              left join Subject subj on subj.id = p.subjectId
              left join com.musicstudio.organization.domain.Membership m on m.id = e.teacherMembershipId
              left join com.musicstudio.account.domain.UserAccount u on u.id = m.userId
            where s.organizationId = :organizationId
            order by s.name, s.id""")
    List<StudentRow> findRows(Long organizationId);

    interface StudentRow {
        Student getStudent();

        Enrollment getEnrollment();

        Long getSubjectId();

        String getProductName();

        String getSubjectName();

        String getTeacherName();
    }
}
