import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// A pure Kotlin module without Android (:crossfeature:domain, :core:mvi, :core:network), on the same Java as the
// Android modules.
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugins.kotlin.jvm.get().pluginId)
            pluginManager.apply(libs.plugins.dependency.analysis.get().pluginId)

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = AndroidConfig.JAVA_VERSION
                targetCompatibility = AndroidConfig.JAVA_VERSION
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions.jvmTarget.set(JvmTarget.fromTarget(AndroidConfig.JAVA_VERSION.toString()))
            }
            configureKotlinWarnings()
            configureTestLogging()
        }
    }
}
