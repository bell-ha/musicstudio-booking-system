package com.musicstudio.account.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    /** 소문자로 저장한다. 대소문자만 다른 이메일은 같은 계정이다. */
    private String email;

    private String passwordHash;

    private String name;

    private Instant createdAt;

    protected UserAccount() {
    }

    public UserAccount(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }
}
