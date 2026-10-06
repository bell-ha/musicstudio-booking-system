package com.musicstudio.organization.domain;

import java.util.Set;

public enum OrganizationType {
    ACADEMY(Set.of(Module.PRACTICE_ROOM, Module.ACADEMY)),
    SCHOOL(Set.of(Module.PRACTICE_ROOM)),
    OTHER(Set.of(Module.PRACTICE_ROOM));

    /** 기관을 만들 때 모듈을 고르지 않으면 켜는 모듈 (UC-02). */
    private final Set<Module> defaultModules;

    OrganizationType(Set<Module> defaultModules) {
        this.defaultModules = defaultModules;
    }

    public Set<Module> defaultModules() {
        return defaultModules;
    }
}
