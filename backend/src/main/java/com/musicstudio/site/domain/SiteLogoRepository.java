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

    /** 교체·삭제는 행을 잠그고 읽는다. 동시에 둘이 바꾸면 한쪽의 파일이 고아로 남거나 낡은 행 저장(500)이 난다 */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from SiteLogo l where l.organizationId = :orgId")
    Optional<SiteLogo> findForUpdate(Long orgId);
}
