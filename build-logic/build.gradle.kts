import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `kotlin-dsl`
}

dependencies {
    // Only AGP and Kotlin types for the android and kotlin blocks; the root build.gradle.kts applies the plugins
    // themselves.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    // The same for the module rules: only the types of their extension.
    compileOnly(libs.module.graph.assertion.gradlePlugin)
    // And Koin's compiler plugin settings (KoinConventionPlugin).
    compileOnly(libs.koin.compiler.gradlePlugin)
    // And the screenshot tests' settings (AndroidScreenshotsConventionPlugin).
    compileOnly(libs.roborazzi.gradlePlugin)
    // Lets convention plugins use the type-safe libs accessors (gradle/gradle#15383).
    implementation(files(LibrariesForLibs::class.java.protectionDomain.codeSource.location))
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.chatapp.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.chatapp.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.chatapp.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("koin") {
            id = libs.plugins.chatapp.koin.get().pluginId
            implementationClass = "KoinConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.chatapp.android.feature.get().pluginId
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidScreenshots") {
            id = libs.plugins.chatapp.android.screenshots.get().pluginId
            implementationClass = "AndroidScreenshotsConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.chatapp.jvm.library.get().pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
