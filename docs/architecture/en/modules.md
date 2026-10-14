# Modules

[Русская версия](../ru/modules.md) · [All pages](README.md)

The folders are levels, from the bottom up: `core/` → `crossfeature/` → `feature/` → `app/`. A module never depends on a level above its own, and features never depend on each other.

&nbsp;

## Levels

Each level has its own modules and its own purpose:

| Level | Modules | What it holds |
|---|---|---|
| `core/` | `designsystem`, `compose-utils`, `mvi`, `network` | code that would fit any app: theme, icons, generic components, Compose glue, the MVI core, the HTTP client |
| `crossfeature/` | `domain`, `data`, `ui` | chat code that two or more features use: models, their storage and sync, chat components |
| `feature/` | `login`, `chats` | one feature each; its screens live in `impl` |
| `app/` | `:app` | the application: it starts Koin, holds the navigation host and runs the background work; nothing depends on it |

The levels are folders, not just dependency rules: a glance at the tree shows which code knows about chats. There are no catch-all modules like `:core:common` that every feature ends up editing.

Next to the app's modules there are three separate Gradle builds:

| Build | Holds |
|---|---|
| [`build-logic/`](../../../build-logic/) | the convention plugins every module applies (see [Build](build.md)) |
| [`contract/`](../../../contract/) | the DTOs the app and the server share: requests, responses, events, headers |
| [`server/`](../../../server/README.md) | the Ktor server; the app's `settings.gradle.kts` doesn't include it |

`contract/` is an included build of both the app and the server, so the two sides compile against the same classes.

&nbsp;

## Where new code goes

Code stays in the feature that uses it. When a second feature needs it, it moves to `crossfeature/`:

- **Used by one feature** — stays in that feature.
- **Needed by a second feature** — moves to `crossfeature/`: models and repository interfaces to `:crossfeature:domain`, their implementations to `:crossfeature:data`, chat components (`ChatAvatar`) to `:crossfeature:ui`.
- **Knows nothing about chats** — goes to `core/`: anything visual to `:core:designsystem`, glue that draws nothing to `:core:compose-utils`.

Styled components follow the same rule:

| The component | Module |
|---|---|
| would fit any other app unchanged | `:core:designsystem` |
| knows chat words (chat, message, contact) and is used by two or more features | `:crossfeature:ui` |
| knows chat words and is used by only one feature | `:feature:<name>:ui` |

Features don't style Material components themselves. The unread counter is an example: Material's `Badge` is red by default, which reads as an error, so the accent-coloured `CountBadge` lives in `:core:designsystem`, and the chat list only places it.

&nbsp;

## A feature

A feature's screens, ViewModels, its own use cases and its navigation live in `impl`:

```kotlin
// feature/chats/impl/build.gradle.kts
plugins {
    alias(libs.plugins.chatapp.android.feature)
    alias(libs.plugins.chatapp.android.screenshots)
}

dependencies {
    implementation(project(":crossfeature:domain"))
    implementation(project(":crossfeature:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:compose-utils"))
    implementation(project(":core:mvi"))
    // ...
}
```

- **A `ui` module appears when a feature has chat components of its own** that no other feature uses. Neither feature has one yet: their components are sections of a screen and live in its `components/` folder.
- **There is no `api` module.** Features never depend on each other, so only the app needs a feature's API, and it connects the features through callbacks (see [Navigation](navigation.md)).
- **Only `:app` depends on `:crossfeature:data`.** Features see repository interfaces from `:crossfeature:domain`; Koin provides the implementations.

&nbsp;

## What a feature exposes

In an `impl` module, only what the app wires up is public, and all of it lives in `presentation/navigation`, plus the module's Koin module in `di/`. Everything else — screens, ViewModels, Executors, use cases — is `internal`:

```kotlin
object ChatsRoutes {
    @Serializable data object ChatList : NavKey                        // the app opens the feature with it
    @Serializable internal data class Chat(val chatId: String) : NavKey
}

fun EntryProviderScope<NavKey>.chatsEntries(navigator: Navigator, onToggleTheme: () -> Unit) { /* ... */ }

val chatsNavKeys = SerializersModule { /* ... */ }                     // for saving the back stack
```

&nbsp;

## Koin modules

Every module with definitions has exactly one public Koin module, in its `di/` package. It scans its own package for classes annotated with `@Singleton`, `@Factory` or `@KoinViewModel`:

```kotlin
@Module
@Configuration
@ComponentScan("krio.systemdesign.chatapp.feature.chats.impl")
class ChatsModule
```

- **`:app` depends directly on every module with definitions**, `:core:network` included. `@KoinApplication` on `ChatApp` picks up every `@Configuration` module on `:app`'s classpath, and the compiler plugin checks the whole graph there.
- **An implementation binds to its interfaces on its own** and stays `internal`: `MessagesRepositoryImpl` is injected wherever a `MessagesRepository` is asked for.
- **`:app`'s own module scans only its subpackages** (`network`, `session`, `work`). Its root package contains every other module's package, and the scan reaches into dependencies, so it would declare their definitions a second time.

&nbsp;

## Packages

A library's package mirrors its module path, and the app's package is its applicationId:

| Module | Package |
|---|---|
| `:crossfeature:ui` | `krio.systemdesign.chatapp.crossfeature.ui` |
| `:feature:chats:impl` | `krio.systemdesign.chatapp.feature.chats.impl` |
| `:core:compose-utils` | `krio.systemdesign.chatapp.core.composeutils` (the hyphen is dropped) |
| `:app` | `krio.systemdesign.chatapp` |

&nbsp;

## The check

`./gradlew assertModuleGraph` checks every dependency between modules against an allowlist in [`ModuleGraphRules.kt`](../../../build-logic/src/main/kotlin/ModuleGraphRules.kt), and CI runs it on every pull request. Each rule is a single regex matched against the "from -> to" string:

```kotlin
allowed = arrayOf(
    """:core:.* -> :core:.*""",
    """:crossfeature:(domain|ui) -> :core:.*""",
    """:crossfeature:data -> :(core:.*|crossfeature:domain)""",
    """:feature:\w+:ui -> :(core:designsystem|crossfeature:ui)""",
    """:feature:\w+:impl -> :(core:.*|crossfeature:(domain|ui))""",
    """:feature:(\w+):impl -> :feature:\1:ui""",   // only its own ui
    """:app -> :(core|crossfeature|feature):.*""",
)
```

Any dependency that no rule allows fails the build:

```text
[':feature:chats:impl' -> ':crossfeature:data'] not allowed by any of [...]
```

Adding a new kind of dependency between modules is a deliberate decision: it needs a new rule in that file, and the rule shows up in the diff.
