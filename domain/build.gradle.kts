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
