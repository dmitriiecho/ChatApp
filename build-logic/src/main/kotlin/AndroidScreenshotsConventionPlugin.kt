import com.android.build.api.dsl.CommonExtension
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

// Screenshot tests generated from a module's @Preview functions: Roborazzi draws each preview on Robolectric
// and compares it with the image saved in the module's screenshots/ folder. Apply after library or application
// and compose. ./gradlew test compares (roborazzi.test.verify in gradle.properties); recordRoborazziDebug saves
// new images (CLAUDE.md, "Screenshots").
class AndroidScreenshotsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugins.roborazzi.get().pluginId)

            val android = extensions.getByType(CommonExtension::class.java)
            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("screenshots"))
                @OptIn(ExperimentalRoborazziApi::class)
                generateComposePreviewRobolectricTests {
                    enable.set(true)
                    // The module's own previews; the namespace is set in its build file, after this plugin.
                    packages.set(provider { listOf(checkNotNull(android.namespace)) })
                    // Slack's Compose lint rules make previews private.
                    includePrivatePreviews.set(true)
                    // Roborazzi's tester with the clock stopped, so endless animations don't hang the tests.
                    testerQualifiedClassName.set(
                        "krio.systemdesign.chatapp.core.composeutils.PausedClockPreviewTester",
                    )
                    // Hands packages and includePrivatePreviews above to that tester.
                    useScanOptionParametersInTester.set(true)
                }
            }
            android.testOptions.unitTests.all {
                // Draws the way a device's GPU does, with shadows and rounded clips.
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                // Times on screen are in the device's zone: one zone for every machine, so a screenshot recorded here
                // matches on CI.
                it.systemProperty("user.timezone", "UTC")
            }

            dependencies {
                "testImplementation"(libs.roborazzi.compose.preview.scanner.support)
                "testImplementation"(libs.composable.preview.scanner)
                // The generated tests create the tester at run time, by its name.
                "testRuntimeOnly"(testFixtures(project(":core:compose-utils")))
                "testImplementation"(libs.robolectric)
                "testImplementation"(libs.junit)
                "testImplementation"(platform(libs.androidx.compose.bom))
                "testImplementation"(libs.androidx.compose.ui.test.junit4)
                // The activity the tests draw previews in.
                "debugRuntimeOnly"(libs.androidx.compose.ui.test.manifest)
            }
        }
    }
}
