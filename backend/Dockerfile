# 빌드 단계: JDK로 실행 가능한 jar를 만든다. 테스트는 CI에서 돌리므로 여기서는 건너뛴다.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar -x test

# 실행 단계: Alpine 기반 JRE만 담아 이미지를 작게 한다(amd64·arm64 모두 있음, EC2 t4g는 arm64).
# root가 아닌 사용자로 돈다.
FROM eclipse-temurin:25-jre-alpine
RUN adduser -S -u 10001 app
USER app
COPY --from=build /src/build/libs/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
