import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

// Compose and its core libraries. Apply after library or application: it needs the android block.
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugins.kotlin.compose.get().pluginId)

            extensions.configure<CommonExtension> {
                buildFeatures.compose = true
            }

            dependencies {
                "implementation"(platform(libs.androidx.compose.bom))
                "implementation"(libs.androidx.compose.material3)
                // asProvider(): ui and ui-tooling are also prefixes of other aliases (ui-graphics, ui-tooling-preview).
                "implementation"(libs.androidx.compose.ui.asProvider())
                "implementation"(libs.androidx.compose.ui.graphics)
                "implementation"(libs.androidx.compose.ui.tooling.preview)
                "debugImplementation"(libs.androidx.compose.ui.tooling.asProvider())
                "lintChecks"(libs.compose.lint.checks)
            }
        }
    }
}
