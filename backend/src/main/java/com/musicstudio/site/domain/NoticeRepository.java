package com.musicstudio.site.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** 고정 먼저, 그다음 최신순 */
    List<Notice> findTop50ByOrganizationIdAndVisibilityInOrderByPinnedDescCreatedAtDesc(
            Long organizationId, Collection<NoticeVisibility> visibilities);

    List<Notice> findTop10ByOrganizationIdAndVisibilityOrderByPinnedDescCreatedAtDesc(
            Long organizationId, NoticeVisibility visibility);

    Optional<Notice> findByIdAndOrganizationId(Long id, Long organizationId);
}
