package com.musicstudio.academy.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** 과목 (피아노, 보컬, 기타, 작곡 등). */
@Entity
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long organizationId;

    private String name;

    protected Subject() {
    }

    public Subject(Long organizationId, String name) {
        this.organizationId = organizationId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
