// A separate build: the app's settings.gradle.kts doesn't include it.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "server"

// The API formats shared with the app (../contract): the server and the app use the same DTOs.
includeBuild("../contract")
