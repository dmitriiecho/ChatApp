plugins {
    alias(libs.plugins.chatapp.android.library)
    alias(libs.plugins.chatapp.koin)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room3)
}

android {
    namespace = "krio.systemdesign.chatapp.crossfeature.data"
}

// The schema of each database version, committed: Room checks migrations against it.
room3 {
    schemaDirectory("$projectDir/schemas")
}

// The tests read server/api-samples (apiSample): without this, an edited sample leaves them UP-TO-DATE and skipped.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.layout.projectDirectory.dir("server/api-samples"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("apiSamples")
}

dependencies {
    implementation(project(":crossfeature:domain"))
    implementation(project(":core:network"))
    implementation(libs.chatapp.contract)
    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)

    testImplementation(testFixtures(project(":core:network")))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.assertk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
