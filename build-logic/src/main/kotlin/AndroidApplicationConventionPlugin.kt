import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

// The app module: the library setup plus targetSdk, and the module rules for its graph.
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugins.android.application.get().pluginId)
            pluginManager.apply(libs.plugins.dependency.analysis.get().pluginId)
            configureModuleGraphRules()

            extensions.configure<ApplicationExtension> {
                configureAndroid(this)
                defaultConfig.targetSdk = AndroidConfig.TARGET_SDK
                lint.warningsAsErrors = warningsAsErrors
                // Lists the app's languages (values-ru…) for Android 13+, so its language can be picked in the system
                // settings. The language of the plain values folder is in src/main/res/resources.properties.
                androidResources.generateLocaleConfig = true

                buildTypes.getByName("release") {
                    // A demo app: release is signed with the debug key so it installs on an emulator.
                    // Publishing to a store would need a real key.
                    signingConfig = signingConfigs.getByName("debug")
                    // R8: shrinks code and resources. The libraries bring their own keep rules; the app's own go
                    // to src/main/keepRules/*.keep.
                    optimization {
                        enable = true
                    }
                }
            }
            alignCoreKtx()
            configureKotlinWarnings()
            configureTestLogging()
        }
    }
}
