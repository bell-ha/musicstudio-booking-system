package com.musicstudio.organization.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.musicstudio.organization.domain.MembershipRole;

/**
 * 기관 범위 API(/api/v1/organizations/{orgId}/**)를 부를 수 있는 역할.
 * 비워 두면 활성 멤버 누구나. MANAGER를 허용하면 OWNER도 허용된다.
 * 이 어노테이션이 없는 기관 범위 핸들러는 OrgAccessInterceptor가 거부한다(기본 거부).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OrgRole {

    MembershipRole[] value() default {};
}
