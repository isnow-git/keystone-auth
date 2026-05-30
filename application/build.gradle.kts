plugins {
  alias(libs.plugins.pitest)
}

dependencies {
  implementation(project(":domain"))

  // SLF4J facade only; runtime binding (logback) comes from :infrastructure / :boot.
  implementation(rootProject.libs.slf4j.api)

  testImplementation(rootProject.libs.junit.jupiter)
  testImplementation(rootProject.libs.assertj.core)
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
  violationRules {
    rule {
      limit {
        counter = "LINE"
        value = "COVEREDRATIO"
        minimum = "0.80".toBigDecimal()
      }
    }
  }
}

tasks.named("check") {
  dependsOn("jacocoTestCoverageVerification")
}

pitest {
  junit5PluginVersion.set(rootProject.libs.versions.pitest.junit5)
  targetClasses.set(listOf("com.keystone.auth.application.*"))
  targetTests.set(listOf("com.keystone.auth.application.*"))
  threads.set(4)
  outputFormats.set(listOf("HTML", "XML"))
  timestampedReports.set(false)
  mutators.set(listOf("STRONGER"))
}
