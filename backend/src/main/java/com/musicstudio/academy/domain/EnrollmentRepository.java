package com.musicstudio.academy.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByIdAndOrganizationId(Long id, Long organizationId);

    /** 원생 상세의 수강 목록과 상품·과목·강사 이름. */
    @Query("""
            select e as enrollment, p.name as productName, p.kind as kind, subj.name as subjectName,
                   u.name as teacherName
            from Enrollment e
              join Product p on p.id = e.productId
              join Subject subj on subj.id = p.subjectId
              join com.musicstudio.organization.domain.Membership m on m.id = e.teacherMembershipId
              join com.musicstudio.account.domain.UserAccount u on u.id = m.userId
            where e.studentId = :studentId
            order by e.startsOn desc, e.id desc""")
    List<EnrollmentRow> findWithNames(Long studentId);

    interface EnrollmentRow {
        Enrollment getEnrollment();

        String getProductName();

        ProductKind getKind();

        String getSubjectName();

        String getTeacherName();
    }
}
