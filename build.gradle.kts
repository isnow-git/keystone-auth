plugins {
    alias(libs.plugins.spotless) apply false
}

allprojects {
    group = "com.keystone.auth"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat("1.24.0")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
            target("src/**/*.java")
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
        options.encoding = "UTF-8"
    }
}
