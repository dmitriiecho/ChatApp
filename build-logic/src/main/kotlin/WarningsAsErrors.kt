import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

// CI passes -PwarningsAsErrors=true: there Kotlin and lint warnings fail the build, so they don't pile up.
// Locally they are only printed, so a half-done change still builds.
internal val Project.warningsAsErrors: Boolean
    get() = providers.gradleProperty("warningsAsErrors").orNull.toBoolean()

internal fun Project.configureKotlinWarnings() {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions.allWarningsAsErrors.set(warningsAsErrors)
    }
}
