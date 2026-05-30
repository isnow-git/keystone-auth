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
