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

    /** 소문자로 저장한다. 대소문자만 다른 이메일은 같은 계정이다. 소셜 계정은 없을 수 있다. */
    private String email;

    private String passwordHash;

    private String name;

    private String googleSubject;

    private Instant createdAt;

    protected UserAccount() {
    }

    public UserAccount(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.createdAt = Instant.now();
    }

    /** 구글 로그인으로 처음 들어온 사람. 비밀번호가 없다. 이메일은 구글이 검증한 경우에만 받는다. */
    public static UserAccount google(String subject, String email, String name) {
        UserAccount account = new UserAccount(email, null, name);
        account.googleSubject = subject;
        return account;
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
