plugins {
    alias(libs.plugins.chatapp.jvm.library)
}

dependencies {
    // StateFlow and Flow are part of the Store's API.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.assertk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
