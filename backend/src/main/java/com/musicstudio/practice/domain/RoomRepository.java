package com.musicstudio.practice.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByOrganizationIdOrderByNameAsc(Long organizationId);

    List<Room> findByFloorId(Long floorId);

    List<Room> findByIdInAndOrganizationId(Collection<Long> ids, Long organizationId);

    Optional<Room> findByIdAndOrganizationId(Long id, Long organizationId);
}
