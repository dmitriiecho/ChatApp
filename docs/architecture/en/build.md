# Build

[Русская версия](../ru/build.md) · [All pages](README.md)

The shared module setup lives in [`build-logic/`](../../../build-logic/), an included build with convention plugins. Each module applies a few of them and declares only its own dependencies.

&nbsp;

## Convention plugins

Each kind of module has its own plugin:

| Plugin | For | Adds |
|---|---|---|
| `chatapp.android.application` | `:app` | the Android setup, targetSdk, signing and R8 in release, the app's languages for the system settings, the [module graph check](modules.md#the-check), dependency analysis, warnings as errors in CI |
| `chatapp.android.library` | every Android library | compileSdk, minSdk, Java, dependency analysis, warnings as errors in CI, the test setup: unit tests on debug only, Robolectric on the app's targetSdk, a failed test's message in the log |
| `chatapp.android.compose` | modules with Compose | the Compose compiler, the BOM, Material 3, Slack's Compose lint rules |
| `chatapp.koin` | modules with Koin definitions | Koin with its compiler plugin, which checks the graph when `:app` compiles |
| `chatapp.android.feature` | every `:feature:<name>:impl` | library + Compose + Koin + serialization, Navigation 3 and lifecycle libraries |
| `chatapp.android.screenshots` | every library module with previews | [screenshot tests](testing.md#screenshots) generated from the previews |
| `chatapp.jvm.library` | `:crossfeature:domain`, `:core:mvi`, `:core:network` | Kotlin JVM without Android, dependency analysis, warnings as errors in CI, a failed test's message in the log |

As a result, a module's build file is short:

```kotlin
// crossfeature/ui/build.gradle.kts
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
}
```

The SDK and Java versions are set in one place, [`AndroidConfig.kt`](../../../build-logic/src/main/kotlin/AndroidConfig.kt):

```kotlin
internal object AndroidConfig {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 26
    const val TARGET_SDK = 37
    val JAVA_VERSION = JavaVersion.VERSION_21
}
```

&nbsp;

## Koin's compiler plugin

Koin definitions are annotations (`@Singleton`, `@Factory`, `@KoinViewModel`, `@KoinWorker`), and its [compiler plugin](https://insert-koin.io/docs/reference/koin-compiler/compile-safety/) turns them into code. When it compiles `:app`, it checks the whole graph: a missing dependency fails the build (`KOIN-D001`, with the file and the parameter that needs it) instead of crashing at run time.

- **Library modules aren't checked on their own**: the graph is complete only in `:app`. The plugin's notes in library modules ("validation skipped") are lowered to info, so CI doesn't treat them as warnings.
- **Kotlin stays at the newest version the plugin is verified with.** On a newer one it warns, and CI fails on warnings, so Kotlin and `koinCompilerPlugin` are bumped together in [`libs.versions.toml`](../../../gradle/libs.versions.toml).
- **Comments in a module with Koin don't mention `@KoinApplication` or `startKoin`**: the plugin finds that text and mistakes the module for the app.

&nbsp;

## Versions and repositories

All versions are in [`gradle/libs.versions.toml`](../../../gradle/libs.versions.toml), and build-logic reads the same catalog. Modules apply plugins without specifying versions.

- **Repositories are declared only in `settings.gradle.kts`**: if a module declares its own, the build fails. Google Maven is used only for Google and AndroidX artifacts.
- **The configuration cache, the build cache and parallel builds are enabled.** The build cache stores task outputs, and CI reuses them from earlier runs too.
- **`contract/` is an included build** of both the app and the server; **`server/`** is a separate build with its own wrapper and version catalog, which the app's settings don't include.

&nbsp;

## Code style

[ktlint](https://github.com/pinterest/ktlint) 1.8 checks the style through Spotless, using the rules in the root [`.editorconfig`](../../../.editorconfig):

```sh
./gradlew spotlessCheck   # check
./gradlew spotlessApply   # fix
```

- **The `android_studio` style**, which matches Android Studio's formatter, so reformatting in the IDE and ktlint agree.
- **120 characters per line.**
- **Trailing commas** in multi-line lists, so adding an item changes only one line in the diff, and lines can be reordered freely.
- **One parameter per line** once there are two or more.

Comments are in English and short, and they explain what the code itself can't: a reason, an order that matters, a workaround. Obvious code has no comments.

&nbsp;

## CI

[`ci.yml`](../../../.github/workflows/ci.yml) runs on every pull request and every push to `main`, unless only the docs changed. Two jobs run in parallel:

| Job | Steps |
|---|---|
| Android | code style (ktlint) → dependencies between modules (`assertModuleGraph`) → unused dependencies (`buildHealth`) → problems in code and resources (Android lint with Slack's Compose rules) → [unit and screenshot tests](testing.md) → a build of the app, which also checks its Koin graph |
| Server | code style (ktlint) → API (tests, including the [API samples](testing.md#contract-with-the-server)) |

- **Every check runs even if an earlier one has failed**, so a single run shows all the problems.
- **Warnings are errors in CI**, both Kotlin's and lint's: CI passes `-PwarningsAsErrors=true`, which is read by the convention plugins ([`WarningsAsErrors.kt`](../../../build-logic/src/main/kotlin/WarningsAsErrors.kt)) and by the server build. A local build only prints the warnings.
- **Unused dependencies** are found by the [Dependency Analysis](https://github.com/autonomousapps/dependency-analysis-gradle-plugin) plugin; its rules are in the root [`build.gradle.kts`](../../../build.gradle.kts).
- **Lint exceptions** are in the root [`lint.xml`](../../../lint.xml): `LocalDarkTheme`, the one CompositionLocal the project creates on purpose (see [Navigation](navigation.md#entries-keep-what-they-captured)), and the "a newer version is available" check, lowered to a hint so that a new library release doesn't fail CI.
- **Failed tests show up at the top of the run's page**, each with its message and line ([action-junit-report](https://github.com/mikepenz/action-junit-report)), and the changed screenshots are attached to the run as `changed-screenshots`.
- **The Android runtime that Robolectric uses is cached** between runs: Robolectric downloads it into `~/.m2`, which Gradle's cache doesn't cover.
- **A job is stopped after 30 minutes** (the server job after 15) instead of hanging for GitHub's default 6 hours.

A new push to a pull request cancels that pull request's run if it is still going, while runs on `main` always finish:

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}
```

[`latest-build.yml`](../../../.github/workflows/latest-build.yml) builds the release APK after CI passes on a push to `main`, and publishes it as the `main-latest` release, which the README links to. The APK needs the server's key: the workflow writes it into `local.properties` from the `CHATAPP_API_KEY` repo secret, and fails if the secret isn't set. Pull requests never reach this workflow, so their code never gets the key.

&nbsp;

## Signing

The debug key is stored in the repo ([`build-logic/debug.keystore`](../../../build-logic/debug.keystore)), so a build from any computer, CI included, has the same signature, and a new APK installs over the previous one.

- **Release builds are signed with the same key**, so they can be installed on a device or an emulator; publishing to a store would need a real key.
- **R8 is enabled for release builds** and shrinks the code and resources.
- **The server's key** comes from `chatapp.apiKey` in `local.properties`, which isn't in git. A build without it works, but the server answers 401.

&nbsp;

## Commands

The main commands, from the project root:

```sh
./gradlew :app:installDebug            # install the app on a connected device
./gradlew assertModuleGraph            # check dependencies between modules
./gradlew buildHealth                  # find unused dependencies
./gradlew lintDebug                    # run Android lint on every module
./gradlew test                         # run the app tests, including screenshots
./gradlew recordRoborazziDebug -Proborazzi.cleanupOldScreenshots=true  # record new screenshots after a UI change
cd server && ./gradlew test            # run the server tests
server/deploy.sh                       # test, build and restart the server on its machine
```

The same checks as CI, with warnings treated as errors:

```sh
./gradlew -PwarningsAsErrors=true spotlessCheck assertModuleGraph buildHealth lintDebug test :app:assembleDebug
cd server && ./gradlew -PwarningsAsErrors=true spotlessCheck test
```

> [!WARNING]
> Never run two Gradle builds in the same checkout at once: they share the `build/` folders and corrupt each other's outputs.
