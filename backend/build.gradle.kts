plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.musicstudio"
version = "0.0.1-SNAPSHOT"
description = "Music academy and school platform"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    // 파일 저장소 (ADR 0017): 로컬은 MinIO, 운영은 S3. 같은 S3 API
    implementation(platform("software.amazon.awssdk:bom:2.46.7"))
    implementation("software.amazon.awssdk:s3")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Docker 이미지가 이 이름으로 jar를 찾는다. 버전 문자열이 바뀌어도 깨지지 않게 고정한다.
tasks.bootJar {
    archiveFileName = "app.jar"
}

tasks.test {
    useJUnitPlatform { excludeTags("experiment") }
}

// 동시성 실험 (ADR 0010). 일반 빌드에서는 돌지 않는다. ./gradlew experiment [-PpoolSize=20]
val experiment by tasks.registering(Test::class) {
    description = "예약 겹침 방지 방식을 비교하는 동시성 실험"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("experiment") }
    systemProperty("experiment.poolSize", findProperty("poolSize") ?: "20")
    systemProperty("experiment.out", layout.projectDirectory.dir("../docs/experiments").asFile.path)
    testLogging.showStandardStreams = true
    outputs.upToDateWhen { false }
}

// 로컬 실행 전용 키. 서버에서는 JWT_SECRET, APP_CRYPTO_KEY 환경 변수가 없으면 애플리케이션이 뜨지 않는다.
tasks.bootRun {
    environment("JWT_SECRET", System.getenv("JWT_SECRET") ?: "local-dev-only-secret-change-me-0123456789")
    environment("APP_CRYPTO_KEY", System.getenv("APP_CRYPTO_KEY") ?: "bG9jYWwtZGV2LW9ubHkta2V5LTAxMjM0NTY3ODlhYmM=")
}
