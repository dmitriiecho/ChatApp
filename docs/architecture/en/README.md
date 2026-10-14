# Architecture

[Русская версия](../ru/README.md)

ChatApp is a demo chat for Android with its own server: you pick who you are, and get a list of chats with bots that write on their own and answer you. It works offline: the chats open from the cache, a message written without a network is sent when it comes back, and what was missed catches up by itself. A Ktor server in [`server/`](../../../server/README.md) keeps the chats and sends the events.

These pages explain how the app is built and why. Each one covers the rules, the reasons behind them and short excerpts from the project's code.

&nbsp;

## Modules at a glance

The app is organised in four levels, from the bottom up:

```text
app/           app                                the application: Koin, navigation, background work
feature/       login, chats                       one feature each
crossfeature/  domain, data, ui                   chat code used by two or more features
core/          designsystem, compose-utils,       knows nothing about chats
               mvi, network
```

A module never depends on a level above its own, and features never depend on each other. The build enforces both rules. Next to them, `contract/` holds the DTOs the app and the server share, and `server/` is the server.

&nbsp;

## Pages

Each page can be read on its own:

| Page | What it covers |
|---|---|
| [Modules](modules.md) | the levels, where new code goes, what a feature exposes, Koin modules, the module graph check |
| [Navigation](navigation.md) | Navigation 3 keys and entries, the back stack that follows the session, a screen's own ViewModels |
| [Screens](screens.md) | MVI on the project's own core: Store, Executor, Reducer, State, Effects, text fields, lists |
| [Data](data.md) | "the network writes, the screens read", the windows over Room, versions, Room 3, the network |
| [Pagination](pagination.md) | the windows over the cache on both screens: pages up and down, trims, opening at a message, jumps, races |
| [Sync](sync.md) | the events socket, sync by token, pushes, the outbox, read marks, the bots switch |
| [Build](build.md) | convention plugins, Koin's compiler plugin, the version catalog, code style, CI, signing |
| [Testing](testing.md) | how tests are written, where test helpers live, kinds of tests, the contract with the server, screenshots |

&nbsp;

## Stack

The main libraries and tools; all of the app's libraries also work in Kotlin Multiplatform, so the code can move there later:

| Area | Choice |
|---|---|
| Platform | Android (minSdk 26) |
| Language and UI | Kotlin 2.4, Jetpack Compose, Material 3 |
| Structure | MVI on the project's own core (`:core:mvi`) |
| DI | [Koin](https://insert-koin.io) 4.2 with annotations; its compiler plugin checks the graph at build time |
| Navigation | [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) |
| Data | [Room 3](https://developer.android.com/jetpack/androidx/releases/room3) as the cache the screens read, WorkManager for sending |
| Network | Ktor Client (OkHttp engine) with WebSockets, kotlinx.serialization |
| Build | Gradle 9.8, AGP 9.4, JDK 21, convention plugins; targetSdk 37, compileSdk 37 |
| Server | Ktor 3.6 with SQLite |
| Tests | kotlin.test, AssertK, kotlinx-coroutines-test, Turbine, Ktor MockEngine, Robolectric, Roborazzi with ComposablePreviewScanner |
