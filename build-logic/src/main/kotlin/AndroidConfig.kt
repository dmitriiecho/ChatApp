import com.android.build.api.dsl.CommonExtension
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the

// Shared by every Android module of the project.
internal object AndroidConfig {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 26
    const val TARGET_SDK = 37

    // The pure Kotlin modules use it too (JvmLibraryConventionPlugin).
    val JAVA_VERSION = JavaVersion.VERSION_21
}

internal fun configureAndroid(android: CommonExtension) {
    android.compileSdk {
        version = release(AndroidConfig.COMPILE_SDK)
    }
    android.defaultConfig.minSdk = AndroidConfig.MIN_SDK
    android.compileOptions.sourceCompatibility = AndroidConfig.JAVA_VERSION
    android.compileOptions.targetCompatibility = AndroidConfig.JAVA_VERSION
}

// androidx.core 1.19 took in core-ktx's classes and left core-ktx empty, but limits core-ktx to its own version
// only at run time. Libraries built against core-ktx 1.18 then put the same classes on the compile classpath
// twice; this limit holds there too.
internal fun Project.alignCoreKtx() {
    dependencies.constraints.add("implementation", libs.androidx.core.ktx)
}

internal val Project.libs: LibrariesForLibs
    get() = the<LibrariesForLibs>()
