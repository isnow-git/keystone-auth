plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.spring.security.crypto)

    implementation(libs.jooq)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    implementation(libs.nimbus.jose.jwt)
    implementation(libs.bucket4j.core)
    implementation(libs.logstash.logback.encoder)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.security.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.assertj.core)
}
