package com.musicstudio.site.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SiteLogoRepository extends JpaRepository<SiteLogo, Long> {

    /** 키만. 앱 바·홈을 그릴 때마다 이미지 바이트를 읽지 않는다 */
    @Query("select l.logoKey from SiteLogo l where l.organizationId = :orgId")
    Optional<UUID> findKey(Long orgId);

    Optional<SiteLogo> findByLogoKey(UUID logoKey);
}
