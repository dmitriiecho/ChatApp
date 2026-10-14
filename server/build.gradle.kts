plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    alias(libs.plugins.spotless)
}

// Same Java as the app (JAVA_VERSION in build-logic/src/main/kotlin/AndroidConfig.kt).
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        // As in the app (build-logic, WarningsAsErrors.kt): CI passes -PwarningsAsErrors=true, then warnings fail
        // the build; locally they are only printed.
        allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false))
    }
}

application {
    mainClass.set("krio.systemdesign.chatapp.server.ApplicationKt")
}

dependencies {
    implementation(libs.chatapp.contract)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.body.limit)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqlite.jdbc)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.assertk)
    testImplementation(libs.turbine)
}

tasks.test {
    useJUnitPlatform()
    // Tests read api-samples/: without this, an edited sample leaves the tests UP-TO-DATE and skipped.
    inputs.dir("api-samples")
    // As in the app (build-logic, TestLogging.kt): a failed test prints its message and the line it failed at.
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        stackTraceFilters(org.gradle.api.tasks.testing.logging.TestStackTraceFilter.ENTRY_POINT)
    }
}

// Same code style as the app: rules in the repo root .editorconfig, rule switches as in its build.gradle.kts.
spotless {
    val disabledRules = mapOf("ktlint_standard_blank-line-between-when-conditions" to "disabled")
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootDir.resolve("../.editorconfig"))
            .editorConfigOverride(disabledRules)
    }
    kotlinGradle {
        target("*.kts")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootDir.resolve("../.editorconfig"))
            .editorConfigOverride(disabledRules)
    }
}
