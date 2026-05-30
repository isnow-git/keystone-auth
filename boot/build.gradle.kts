plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":infrastructure"))
    implementation(project(":application"))
    implementation(project(":domain"))

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
