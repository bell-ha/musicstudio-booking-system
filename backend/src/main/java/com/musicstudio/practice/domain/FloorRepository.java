package com.musicstudio.practice.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorRepository extends JpaRepository<Floor, Long> {

    List<Floor> findByOrganizationIdOrderBySortOrderAscIdAsc(Long organizationId);

    Optional<Floor> findByIdAndOrganizationId(Long id, Long organizationId);
}
