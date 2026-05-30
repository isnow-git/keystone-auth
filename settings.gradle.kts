rootProject.name = "keystone-auth"

include(
    "domain",
    "application",
    "infrastructure",
    "boot",
)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
