import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.koin.compiler.plugin.KoinGradleExtension

// Koin with its compiler plugin: definitions are declared with annotations (@Singleton, @Factory, @KoinViewModel,
// @Module), and the plugin checks the whole graph when it compiles the app's @KoinApplication: a missing
// dependency fails the build instead of crashing at run time. Apply after the Android or Kotlin JVM plugin.
class KoinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugins.koin.compiler.get().pluginId)

            extensions.configure<KoinGradleExtension> {
                // The plugin's notes ("validation skipped — no Koin entry point" in every library) are warnings by
                // default, which CI turns into errors. Real problems in the graph stay errors.
                logSeverity.set("info")
            }

            dependencies {
                "implementation"(platform(libs.koin.bom))
                "implementation"(libs.koin.core)
                "implementation"(libs.koin.annotations)
            }
        }
    }
}
