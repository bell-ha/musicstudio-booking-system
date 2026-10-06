package com.musicstudio.organization.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    /** 내 기관 목록 (UC-09). 거절·비활성 멤버십은 보이지 않는다. */
    @Query("""
            select o as organization, m as membership
            from Membership m join Organization o on o.id = m.organizationId
            where m.userId = :userId
              and m.status in (com.musicstudio.organization.domain.MembershipStatus.ACTIVE,
                               com.musicstudio.organization.domain.MembershipStatus.PENDING)
            order by o.name""")
    List<MyOrganization> findMyOrganizations(Long userId);

    interface MyOrganization {
        Organization getOrganization();

        Membership getMembership();
    }
}
