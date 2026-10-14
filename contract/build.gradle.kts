import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// The coordinates the app and the server depend on: krio.systemdesign.chatapp:contract.
group = "krio.systemdesign.chatapp"

// Same Java as the app (JAVA_VERSION in build-logic/src/main/kotlin/AndroidConfig.kt) and the server.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // As in the app and the server: CI passes -PwarningsAsErrors=true, then warnings fail the build.
        allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false))
    }
}

dependencies {
    // api: the DTOs are @Serializable, and both sides read and write them with their own Json.
    api(libs.kotlinx.serialization.core)
}
