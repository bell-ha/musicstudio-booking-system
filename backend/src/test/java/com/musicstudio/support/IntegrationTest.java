package com.musicstudio.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 실제 PostgreSQL(Testcontainers)에 붙는 통합 테스트.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-0123456789abcdefghijklmnop")
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
