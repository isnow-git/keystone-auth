plugins {
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
}

dependencies {
  implementation(project(":infrastructure"))
  implementation(project(":application"))
  implementation(project(":domain"))

  // Boot is the composition root: it explicitly needs SpringApplication and
  // @SpringBootApplication on its compile classpath (downstream `implementation` deps
  // don't transit to this module).
  implementation(libs.spring.boot.starter)

  testImplementation(libs.spring.boot.starter.test) {
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
  }
  // E2E uses TestRestTemplate, which needs spring-web on the test classpath.
  testImplementation(libs.spring.boot.starter.web)
  testImplementation(libs.spring.boot.testcontainers)
  testImplementation(platform(libs.testcontainers.bom))
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.assertj.core)
  // Used to verify the issued access token against the published JWKS.
  testImplementation(libs.nimbus.jose.jwt)
}

springBoot {
  mainClass.set("com.keystone.auth.KeystoneAuthApplication")
  buildInfo()
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  archiveFileName.set("keystone-auth.jar")
}
