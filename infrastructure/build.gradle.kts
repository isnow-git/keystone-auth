import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.Property

plugins {
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.jooq.codegen)
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
  // Argon2PasswordEncoder delegates to BouncyCastle.
  runtimeOnly(libs.bouncycastle)

  implementation(libs.jooq)
  implementation(libs.flyway.core)
  implementation(libs.flyway.postgresql)
  runtimeOnly(libs.postgresql)

  implementation(libs.nimbus.jose.jwt)
  implementation(libs.bucket4j.core)
  implementation(libs.logstash.logback.encoder)

  // jOOQ codegen runs against the Flyway DDL (no live database required).
  jooqGenerator(libs.jooq.meta.extensions)

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

jooq {
  version.set("3.19.15")
  edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)
  configurations {
    create("main") {
      generateSchemaSourceOnCompilation.set(true)
      jooqConfiguration.apply {
        logging = Logging.WARN
        generator.apply {
          name = "org.jooq.codegen.JavaGenerator"
          database.apply {
            name = "org.jooq.meta.extensions.ddl.DDLDatabase"
            properties.addAll(
              listOf(
                Property().withKey("scripts")
                  .withValue("src/main/resources/db/migration/*.sql"),
                Property().withKey("sort").withValue("flyway"),
                Property().withKey("defaultNameCase").withValue("lower"),
                Property().withKey("unqualifiedSchema").withValue("none"),
              ),
            )
            forcedTypes.addAll(
              listOf(
                ForcedType()
                  .withName("INSTANT")
                  .withIncludeTypes("TIMESTAMP\\ WITH\\ TIME\\ ZONE|TIMESTAMPTZ"),
                ForcedType()
                  .withUserType("java.util.UUID")
                  .withIncludeTypes("UUID"),
              ),
            )
          }
          generate.apply {
            isDeprecated = false
            isRecords = true
            isImmutablePojos = false
            isFluentSetters = false
            isJavaTimeTypes = true
          }
          target.apply {
            packageName = "com.keystone.auth.infrastructure.persistence.jooq.generated"
            directory = "build/generated-src/jooq/main"
          }
        }
      }
    }
  }
}

// Coverage gate on infrastructure: aggregate (not per-class) because adapters are exercised
// only by integration tests that require Docker. Config classes and jOOQ-generated code are
// excluded — neither is meaningful business logic and both inflate the denominator.
tasks.named<JacocoReport>("jacocoTestReport") {
  classDirectories.setFrom(
    files(
      classDirectories.files.map {
        fileTree(it) {
          exclude(
            "com/keystone/auth/infrastructure/persistence/jooq/generated/**",
            "com/keystone/auth/infrastructure/configuration/**",
          )
        }
      },
    ),
  )
}

// No coverage gate on :infrastructure. The adapters are exercised exclusively by Testcontainers
// integration tests that are skipped when Docker is unavailable (local dev). Coverage signal lives
// on :domain and :application — that's where the rules to verify are.
