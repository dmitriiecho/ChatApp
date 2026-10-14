plugins {
    alias(libs.plugins.chatapp.jvm.library)
    alias(libs.plugins.chatapp.koin)
    // networkTest, TestServer and the API samples for the tests of the modules that call the server.
    `java-test-fixtures`
}

// The folder of the samples the app and the server agree on (server/api-samples), compiled into the test fixtures
// as a constant: the tests read it from wherever Gradle runs them.
val generateApiSamplesDir = tasks.register("generateApiSamplesDir") {
    val dir = rootDir.resolve("server/api-samples").absolutePath
    val output = layout.buildDirectory.dir("generated/apiSamplesDir")
    inputs.property("dir", dir)
    outputs.dir(output)
    doLast {
        val file = output.get().file("krio/systemdesign/chatapp/core/network/ApiSamplesDir.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package krio.systemdesign.chatapp.core.network\n\ninternal const val API_SAMPLES_DIR = \"$dir\"\n",
        )
    }
}

kotlin {
    sourceSets.named("testFixtures") {
        kotlin.srcDir(generateApiSamplesDir)
    }
}

// The tests read server/api-samples (apiSample): without this, an edited sample leaves them UP-TO-DATE and skipped.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.layout.projectDirectory.dir("server/api-samples"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("apiSamples")
}

dependencies {
    api(libs.ktor.client.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testFixturesApi(libs.ktor.client.mock)
    testFixturesApi(libs.kotlinx.coroutines.test)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.assertk)
}
