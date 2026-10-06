package com.musicstudio.organization.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private OrganizationType type;

    private String timezone;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] modules;

    private String joinCode;

    private boolean joinCodeEnabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<JoinField> joinForm = new ArrayList<>();

    private Instant createdAt;

    protected Organization() {
    }

    public Organization(String name, OrganizationType type, String timezone, Set<Module> modules, String joinCode) {
        this.name = name;
        this.type = type;
        this.timezone = timezone;
        this.modules = modules.stream().map(Enum::name).sorted().toArray(String[]::new);
        this.joinCode = joinCode;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public OrganizationType getType() {
        return type;
    }

    public String getTimezone() {
        return timezone;
    }

    public Set<Module> getModules() {
        return Arrays.stream(modules).map(Module::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Module.class)));
    }

    public String getJoinCode() {
        return joinCode;
    }

    public boolean isJoinCodeEnabled() {
        return joinCodeEnabled;
    }

    public List<JoinField> getJoinForm() {
        return joinForm;
    }

    public void changeJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }

    public void configureJoinCode(Boolean enabled, List<JoinField> joinForm) {
        if (enabled != null) {
            this.joinCodeEnabled = enabled;
        }
        if (joinForm != null) {
            this.joinForm = new ArrayList<>(joinForm);
        }
    }
}
