package com.musicstudio.academy.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByOrganizationIdOrderByNameAsc(Long organizationId);

    Optional<Product> findByIdAndOrganizationId(Long id, Long organizationId);
}
