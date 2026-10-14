plugins {
    alias(libs.plugins.chatapp.android.library)
    alias(libs.plugins.chatapp.android.compose)
    alias(libs.plugins.chatapp.android.screenshots)
}

android {
    namespace = "krio.systemdesign.chatapp.crossfeature.ui"
}

dependencies {
    implementation(project(":core:designsystem"))

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.assertk)
}
