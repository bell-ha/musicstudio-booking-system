package com.musicstudio.site.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SiteProfileRepository extends JpaRepository<SiteProfile, Long> {

    /** 공개된 사이트만. 없음과 비공개를 구분하지 않는다 */
    @Query("select p from SiteProfile p where lower(p.slug) = lower(:slug) and p.published = true")
    Optional<SiteProfile> findPublished(String slug);
}
