package com.musicstudio.organization.domain;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    /**
     * 아직 쓰지 않았고 만료되지 않은 초대를 사용 처리한다. 동시에 수락하면 한 요청만 1을 받는다.
     */
    @Modifying
    @Query("""
            update Invitation i set i.usedAt = :now
            where i.tokenHash = :tokenHash and i.usedAt is null and i.expiresAt > :now""")
    int markUsed(String tokenHash, Instant now);

    Optional<Invitation> findByTokenHash(String tokenHash);
}
