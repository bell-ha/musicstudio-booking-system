package com.musicstudio.organization.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    Optional<Membership> findByOrganizationIdAndUserId(Long organizationId, Long userId);

    Optional<Membership> findByIdAndOrganizationId(Long id, Long organizationId);

    @Query("""
            select count(m) from Membership m
            where m.organizationId = :organizationId and m.id <> :excludedId
              and m.role = com.musicstudio.organization.domain.MembershipRole.OWNER
              and m.status = com.musicstudio.organization.domain.MembershipStatus.ACTIVE""")
    long countOtherActiveOwners(Long organizationId, Long excludedId);

    /** 내 기관 목록 (UC-09). 거절·비활성 멤버십은 보이지 않는다. */
    @Query("""
            select o as organization, m as membership
            from Membership m join Organization o on o.id = m.organizationId
            where m.userId = :userId
              and m.status in (com.musicstudio.organization.domain.MembershipStatus.ACTIVE,
                               com.musicstudio.organization.domain.MembershipStatus.PENDING)
            order by o.name""")
    List<MyOrganization> findMyOrganizations(Long userId);

    /** 멤버 목록 (UC-07, UC-08). 이름과 이메일은 계정에서 읽는다. */
    @Query("""
            select m as membership, u.name as name, u.email as email
            from Membership m join com.musicstudio.account.domain.UserAccount u on u.id = m.userId
            where m.organizationId = :organizationId and m.status = :status
            order by m.createdAt""")
    List<MemberRow> findMembers(Long organizationId, MembershipStatus status);

    @Query("""
            select m as membership, u.name as name, u.email as email
            from Membership m join com.musicstudio.account.domain.UserAccount u on u.id = m.userId
            where m.id = :id""")
    MemberRow findMember(Long id);

    interface MyOrganization {
        Organization getOrganization();

        Membership getMembership();
    }

    interface MemberRow {
        Membership getMembership();

        String getName();

        String getEmail();
    }
}
