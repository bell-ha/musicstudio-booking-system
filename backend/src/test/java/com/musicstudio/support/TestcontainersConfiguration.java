package com.musicstudio.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 PostgreSQL과 파일 저장소(SeaweedFS의 S3 API). 로컬 docker-compose.yml과 같은 이미지를 쓴다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String SEAWEEDFS = "chrislusf/seaweedfs:4.48";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    }

    @Bean
    GenericContainer<?> s3Container() {
        return new GenericContainer<>(DockerImageName.parse(SEAWEEDFS))
                .withCommand("server", "-s3")
                // 서명된 요청을 받으려면 자격 증명이 있어야 한다. 이 환경 변수로 관리자 키 하나가 생긴다
                .withEnv("AWS_ACCESS_KEY_ID", "test")
                .withEnv("AWS_SECRET_ACCESS_KEY", "test-secret")
                .withExposedPorts(8333)
                .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCodeMatching(code -> code < 500)); // 인증이 켜져 있어 익명 요청은 403
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(GenericContainer<?> s3Container) {
        return registry -> {
            registry.add("app.storage.endpoint", () -> "http://" + s3Container.getHost() + ":" + s3Container.getMappedPort(8333));
            registry.add("app.storage.access-key", () -> "test");
            registry.add("app.storage.secret-key", () -> "test-secret");
            registry.add("app.storage.bucket", () -> "musicstudio-test");
            registry.add("app.storage.create-bucket", () -> "true");
        };
    }
}
