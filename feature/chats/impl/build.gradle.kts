plugins {
    alias(libs.plugins.chatapp.android.feature)
    alias(libs.plugins.chatapp.android.screenshots)
}

android {
    namespace = "krio.systemdesign.chatapp.feature.chats.impl"
}

dependencies {
    implementation(project(":crossfeature:domain"))
    implementation(project(":crossfeature:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:compose-utils"))
    implementation(project(":core:mvi"))

    testImplementation(testFixtures(project(":core:compose-utils")))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.assertk)
    testImplementation(libs.turbine)
}
