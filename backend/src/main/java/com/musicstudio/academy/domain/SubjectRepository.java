package com.musicstudio.academy.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findByOrganizationIdOrderByNameAsc(Long organizationId);

    Optional<Subject> findByIdAndOrganizationId(Long id, Long organizationId);
}
