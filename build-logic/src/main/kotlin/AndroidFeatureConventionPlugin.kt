import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

// A feature's impl module: Compose screens, ViewModels from Koin, kotlinx.serialization navigation keys.
// Each feature declares its own project dependencies (:crossfeature:domain, :core:designsystem…).
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply<AndroidLibraryConventionPlugin>()
            apply<AndroidComposeConventionPlugin>()
            pluginManager.apply(libs.plugins.kotlin.serialization.get().pluginId)
            apply<KoinConventionPlugin>()

            dependencies {
                "implementation"(libs.androidx.navigation3.runtime)
                "implementation"(libs.androidx.lifecycle.runtime.compose)
                "implementation"(libs.androidx.lifecycle.viewmodel.compose)
                "implementation"(libs.kotlinx.serialization.core)
                "implementation"(libs.koin.core.viewmodel)
                "implementation"(libs.koin.compose.viewmodel)
            }
        }
    }
}
