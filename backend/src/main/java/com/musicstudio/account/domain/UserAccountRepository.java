package com.musicstudio.account.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);
}
