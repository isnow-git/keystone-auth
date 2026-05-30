plugins {
  alias(libs.plugins.pitest)
}

dependencies {
  testImplementation(rootProject.libs.junit.jupiter)
  testImplementation(rootProject.libs.assertj.core)
}

tasks.named<JacocoReport>("jacocoTestReport") {
  reports {
    xml.required.set(true)
    html.required.set(true)
  }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
  violationRules {
    rule {
      limit {
        counter = "LINE"
        value = "COVEREDRATIO"
        minimum = "0.80".toBigDecimal()
      }
      limit {
        counter = "BRANCH"
        value = "COVEREDRATIO"
        minimum = "0.70".toBigDecimal()
      }
    }
  }
}

tasks.named("check") {
  dependsOn("jacocoTestCoverageVerification")
}

pitest {
  junit5PluginVersion.set(rootProject.libs.versions.pitest.junit5)
  targetClasses.set(listOf("com.keystone.auth.domain.*"))
  targetTests.set(listOf("com.keystone.auth.domain.*"))
  threads.set(4)
  outputFormats.set(listOf("HTML", "XML"))
  timestampedReports.set(false)
  mutators.set(listOf("STRONGER"))
  // Baseline only — not gated yet. Track the mutation score over time.
}
