// Plugins are declared here once with their versions; modules apply them without versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.koin.compiler) apply false
    alias(libs.plugins.module.graph.assertion) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room3) apply false
    // Unused and undeclared dependencies: ./gradlew buildHealth. Each module gets it from its convention plugin.
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.spotless)
}

// ./gradlew buildHealth (a CI step) fails on unused dependencies and on module structure advice.
dependencyAnalysis {
    issues {
        all {
            onAny {
                severity("fail")
            }
            // Libraries used through others (single Compose artifacts behind the BOM and the like): declaring each
            // would add dozens of lines to every module for no gain.
            onUsedTransitiveDependencies {
                severity("ignore")
            }
            // api vs implementation: impl modules are wired only by the app, and internal classes look public
            // in bytecode, so the advice doesn't fit.
            onIncorrectConfiguration {
                severity("ignore")
            }
            // Convention plugins add these to every Compose module on purpose; a few modules don't use them.
            // The screenshot tests Roborazzi generates import the preview scanner without using it in bytecode, so
            // it looks unused, but they don't compile without it.
            onUnusedDependencies {
                exclude(
                    libs.androidx.compose.material3,
                    libs.androidx.compose.ui.graphics,
                    libs.androidx.compose.ui.tooling.preview,
                    libs.composable.preview.scanner,
                )
            }
        }
    }
}

// Code style for the whole build (rules in .editorconfig): spotlessCheck checks, spotlessApply fixes.
spotless {
    // Spotless ignores rule switches in .editorconfig, so they go here.
    // Blank lines between all branches of a when once one branch is multiline: noise in short whens.
    val disabledRules = mapOf("ktlint_standard_blank-line-between-when-conditions" to "disabled")
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootDir.resolve(".editorconfig"))
            .editorConfigOverride(disabledRules)
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootDir.resolve(".editorconfig"))
            .editorConfigOverride(disabledRules)
    }
}
