# syntax=docker/dockerfile:1.7

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy Gradle wrapper + build scripts first for better layer caching
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY domain/build.gradle.kts ./domain/
COPY application/build.gradle.kts ./application/
COPY infrastructure/build.gradle.kts ./infrastructure/
COPY boot/build.gradle.kts ./boot/

# Prime the dependency cache
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# Copy sources and build the Spring Boot fat jar
COPY domain/src ./domain/src
COPY application/src ./application/src
COPY infrastructure/src ./infrastructure/src
COPY boot/src ./boot/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :boot:bootJar -x test

# ---------- Extract layers ----------
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /extract
COPY --from=build /workspace/boot/build/libs/keystone-auth.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S keystone && adduser -S keystone -G keystone
WORKDIR /app
USER keystone:keystone

COPY --from=extractor /extract/dependencies/ ./
COPY --from=extractor /extract/spring-boot-loader/ ./
COPY --from=extractor /extract/snapshot-dependencies/ ./
COPY --from=extractor /extract/application/ ./

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "org.springframework.boot.loader.launch.JarLauncher"]
