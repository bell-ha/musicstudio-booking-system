package com.musicstudio.academy.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LessonRecordRepository extends JpaRepository<LessonRecord, Long> {

    Optional<LessonRecord> findByIdAndOrganizationId(Long id, Long organizationId);

    /** 원생 상세의 레슨 기록과 쓴 사람 이름. organization·account 엔티티는 JPQL 문자열로 참조한다(허용된 방향). */
    @Query("""
            select r as record, u.name as authorName
            from LessonRecord r
              join com.musicstudio.organization.domain.Membership m on m.id = r.authorMembershipId
              join com.musicstudio.account.domain.UserAccount u on u.id = m.userId
            where r.enrollmentId in :enrollmentIds
            order by r.lessonDate desc, r.id desc""")
    List<RecordRow> findWithAuthor(Collection<Long> enrollmentIds);

    interface RecordRow {
        LessonRecord getRecord();

        String getAuthorName();
    }
}
