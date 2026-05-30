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
}

springBoot {
  mainClass.set("com.keystone.auth.KeystoneAuthApplication")
  buildInfo()
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  archiveFileName.set("keystone-auth.jar")
}
