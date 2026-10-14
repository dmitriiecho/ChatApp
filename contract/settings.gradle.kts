// The API formats the app and the server share. A separate build, so the server, which is a separate build too,
// can include it without the app: the app's settings.gradle.kts and server/settings.gradle.kts both include it.
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
    // The app's version catalog, as in build-logic: the same Kotlin and kotlinx.serialization as the app.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "contract"
