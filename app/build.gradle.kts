import java.util.Properties

plugins {
    alias(libs.plugins.chatapp.android.application)
    alias(libs.plugins.chatapp.android.compose)
    alias(libs.plugins.chatapp.koin)
}

// chatapp.apiKey from local.properties, empty when there is none.
val apiKey: Provider<String> = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText
    .map { text -> Properties().apply { load(text.reader()) }.getProperty("chatapp.apiKey").orEmpty() }
    .orElse("")

android {
    namespace = "krio.systemdesign.chatapp"

    defaultConfig {
        applicationId = "krio.systemdesign.chatapp"
        versionCode = 1
        versionName = "1.0"
        // The server's key (~/server/chatapp/server.env on its machine), from local.properties: not in git.
        // Without it the app builds, as on CI, and the server answers 401.
        buildConfigField("String", "API_KEY", "\"${apiKey.get()}\"")
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":feature:chats:impl"))
    implementation(project(":feature:login:impl"))
    implementation(project(":crossfeature:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:compose-utils"))
    implementation(project(":core:mvi"))
    // What the data layer needs from the app (ApiKey, SessionWork, OutboxScheduler) and what the app runs from it.
    implementation(project(":crossfeature:data"))

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
