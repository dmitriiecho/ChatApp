pluginManagement {
    // Convention plugins (chatapp.*) that set up the modules.
    includeBuild("build-logic")

    repositories {
        // Only Android and Google artifacts come from Google Maven, the rest from Maven Central.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads the JDK for the Gradle daemon (gradle/gradle-daemon-jvm.properties) if it isn't installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    // Repositories are declared only here: a module that declares its own fails the build.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ChatApp"

// The API formats shared with the server (contract/), a separate build so the server can include it too.
includeBuild("contract")

include(":app")

include(":core:designsystem")
include(":core:compose-utils")
include(":core:mvi")
include(":core:network")

include(":crossfeature:domain")
include(":crossfeature:data")
include(":crossfeature:ui")

include(":feature:chats:impl")
include(":feature:login:impl")
