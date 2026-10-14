# ChatApp

A demo chat app for Android, in Kotlin with Jetpack Compose. Built after ShoppingApp's `kmp-ready` branch (same layering, build-logic and checks), with Koin and Navigation 3 in place of Metro and Navigation 2. Only KMP-capable libraries, so the code can move to Kotlin Multiplatform later. It talks to its own Ktor server ([`server/`](server/README.md)), deployed on the project's machine.

| | |
|---|---|
| Stack | Kotlin, Jetpack Compose, Koin (annotations + compiler plugin), Navigation 3, Room 3, Ktor client, WorkManager |
| Build | Gradle 9.8, AGP 9.4, Kotlin 2.4.20, JDK 21, compileSdk 37, minSdk 26 |
| App | `:app`, applicationId `krio.systemdesign.chatapp`, launcher activity `.MainActivity` |

Machine-specific setup (SDK paths, emulator access, the server's key) lives in `CLAUDE.local.md`, if present.

## Modules

Folders are levels, bottom to top: `core/` → `crossfeature/` → `feature/` → `:app`. A module never depends on a level above its own.

| Level | Module | Holds |
|---|---|---|
| `core/`: knows nothing about chats | `:core:designsystem` | theme, icons, generic components (`EmptyState`, `NavigateBackIconButton`); only generic strings ("Back") |
| | `:core:compose-utils` | Compose/ViewModel/navigation glue that draws nothing: `ObserveEffects`, `savedTextField`, `Navigator`; test fixtures (`viewModelTest`, `typeText`, the screenshot tester) |
| | `:core:mvi` | the MVI core every screen runs on: `Store`, `Reducer`, `Executor`, `StoreObserver`, `StoreFactory`; pure Kotlin |
| | `:core:network` | the one `HttpClient` (OkHttp engine, JSON, the events socket), `ServerConfig`, `callApi`, `AuthHeaders`, `NetworkMonitor`; test fixtures (`TestServer`, `networkTest`, `apiSample`) |
| `crossfeature/`: chat code used by two or more features | `:crossfeature:domain` | models, repository interfaces, shared use cases; pure Kotlin |
| | `:crossfeature:data` | their implementations: the Room cache, the server's API, the syncs, the events socket, the outbox; only `:app` depends on it |
| | `:crossfeature:ui` | chat components (`ChatAvatar`) built from `:core:designsystem`; take plain values, never domain models |
| `feature/<name>/` | `:feature:<name>:impl` | screens, ViewModels, the feature's own use cases and navigation |
| | `:feature:<name>:ui` | chat components only this feature uses; created when a feature has such components |
| `app/` | `:app` | `ChatApp` (starts Koin), `MainActivity`, the navigation host (it follows the session), the outbox worker, the events socket's lifecycle, manifest and resources |

Outside the app's modules, two separate Gradle builds:

| Build | Holds |
|---|---|
| [`contract/`](contract/) | the DTOs the app and the server share: requests, responses, events (`RealtimeEventDto`), headers (`ApiHeaders`); an included build of both |
| [`server/`](server/README.md) | the Ktor server with its SQLite database, bots, `api-samples/` and `deploy.sh`; not in the app's `settings.gradle.kts` |

- **The rules are checked** by `./gradlew assertModuleGraph` (a CI step too) against `build-logic/src/main/kotlin/ModuleGraphRules.kt`. A new kind of dependency between modules needs a rule there.
- **An `impl` module shows only what `:app` wires**, all in `presentation/navigation`: the routes, the entries (`chatsEntries`), the keys for saving (`chatsNavKeys`); and its Koin module (`di/`). Everything else is `internal`.

## Where code goes

Code used by one feature stays in that feature; it moves to `crossfeature/` when a second feature needs it. `:core:compose-utils` takes only generic glue: anything visual goes to `:core:designsystem`, anything that knows chats to `crossfeature/`.

| The component | Module |
|---|---|
| would fit any other app unchanged | `:core:designsystem` |
| knows chat words (chat, message, contact), two or more features use it | `:crossfeature:ui` |
| knows chat words, one feature uses it | `:feature:<name>:ui` |

Features don't style Material components themselves. A package mirrors its module path: `:feature:chats:impl` → `krio.systemdesign.chatapp.feature.chats.impl`, `compose-utils` → `composeutils`; `:app` is `krio.systemdesign.chatapp`.

## Conventions

### DI: Koin

[Koin](https://insert-koin.io) with its [compiler plugin](https://insert-koin.io/docs/reference/koin-compiler/compile-safety/) (`chatapp.koin`), declared with annotations.

- **A class to inject** gets `@Singleton` or `@Factory`; a ViewModel gets `@KoinViewModel`. An implementation binds to its interfaces on its own and stays `internal`.
- **Each module with definitions** has one public `@Module @Configuration @ComponentScan("<its package>") class XxxModule` in `di/`. `@KoinApplication` on `ChatApp` picks up every `@Configuration` module on `:app`'s classpath, so a module with definitions is a direct dependency of `:app`.
- **`:app`'s `AppModule` scans only `:app`'s subpackages** (`network`, `session`, `work`): its own package is the root of every module's package, and the scan reaches into dependencies, so it would declare all their definitions again. What the data layer needs from the app (`ApiKey`, `SessionWork`, `OutboxScheduler`, `NetworkMonitor`) comes from there.
- **No cycle through the HTTP client**: it needs the headers (`SessionAuthHeaders` ← `SessionStore`), the sign-out needs the socket (`SessionWork`) and the socket needs the client, so the session in memory (`SessionStore`) is apart from `SessionRepositoryImpl`.
- **The graph is checked when `:app` compiles**: a missing dependency fails the build (`KOIN-D001`, with the file and parameter). Library modules aren't checked on their own. A `@Module` with no definitions at all can't be checked: the plugin then skips what it would have provided.
- **A screen gets its ViewModel** with `koinViewModel()`; arguments from the navigation key go as `koinViewModel { parametersOf(...) }` into `@InjectedParam` constructor parameters. `SavedStateHandle` is injected as is.
- **Kotlin stays at the newest version the plugin is verified with** (`gradle/libs.versions.toml`): on a newer one it warns, and CI fails on warnings. Bump Kotlin together with `koinCompilerPlugin`.
- Don't write `@KoinApplication`, `startKoin` or `koinApplication` in comments of a module with Koin: the plugin finds the text and treats the module as the app.

### Navigation: Navigation 3

- **Keys** are `@Serializable` `NavKey`s in the feature's `XxxRoutes`; the first screen is public, the rest `internal`. The feature lists them in `xxxNavKeys` (a `SerializersModule`), and `:app` adds it to `navKeysConfiguration`, so the back stack survives process death.
- **Entries**: the feature's `EntryProviderScope<NavKey>.xxxEntries(navigator)`; `:app` calls it in `AppNavHost`. Moving between a feature's own screens goes through `Navigator` (`:core:compose-utils`), implemented by `AppNavigator`. Paths between features are callbacks in `:app`.
- **Each entry has its own ViewModels**: `rememberViewModelStoreNavEntryDecorator()` in `AppNavHost`, after `rememberSaveableStateHolderNavEntryDecorator()`.

### Data: the network writes, the screens read

Built after the chat listing the project started from; details in the classes' comments.

- **Room is the only source the screens read.** Pages, syncs, the socket and pushes write into it, and screens watch a window of it: `MessageDao.observeWindow` (seqs `fromSeq..toSeq`, never across a gap) and `ConversationDao.observeLoaded` (offset and count above the loaded boundary). Paginators in the Executors move the windows; Paging 3 isn't used: the chat needs gaps, a growing head and opening at a seq.
- **One exception: the bots switch** (`BotsRepositoryImpl`) is held in memory, not in Room. It is the server's state at this moment, not content to show offline: null until the socket's first connection reads `GET /bots`, then it follows `bots.changed`.
- **Older data never overwrites newer**: every DTO carries `version`, and writes go through `upsertNewer` / `updateExistingNewer`. A new message by the socket is cached only as a continuation of the cache (`seq == maxSeq + 1`).
- **Syncs by token**: `ConversationsSync` on every connection of the socket (`RealtimeHandler.onReconnected`), `MessageChangesSync` when a chat opens and for the ten chats opened last. Changes and the new token go in one transaction; `resetRequired` starts over.
- **The network fails as `IOException`** only: `callApi` (`:core:network`) turns error codes and unreadable answers into one. Features catch `java.io.IOException` (on the JVM it is `kotlinx.io.IOException`).
- **Sending is an outbox**: the message goes to Room first and shows at once; `OutboxSender.drain()` sends it, run by WorkManager (`OutboxWorker` in `:app`, a network required, retried with a pause), also after the app is closed. `clientId` makes a resend harmless.
- **Room 3** (`androidx.room3`, KSP): the schema of each version is committed (`crossfeature/data/schemas`); the database is a cache, so a schema change drops it (`fallbackToDestructiveMigration`), the sign-in too. Room runs on `AndroidSQLiteDriver`, the one Robolectric can run in the DAO tests.
- **The contract is shared**: the DTOs live in `contract/`, and every request and response the app relies on has a sample in `server/api-samples/`. The server's tests check it answers exactly so, the app's tests (`apiSample`, `apiRequest`) read the same files: a format change updates the samples and both sides' tests.

### Screens: MVI

Every screen runs on a `Store` from `:core:mvi`. Data goes one way, in a loop: the UI sends an `Intent`, the `Executor` does the work and reports `Mutation`s, the `Reducer` turns each into a new `State`, the UI draws it. One-off events (navigation, snackbars) go from the `Executor` to the UI as `Effect`s.

```
UI ──Intent──▶ Store ──▶ Executor ──Mutation──▶ Reducer ──▶ State ──▶ UI
                            └────────────Effect─────────────────────▶ UI
```

| Type | Is | Rule |
|---|---|---|
| `XxxState` | what the screen shows | made only by `XxxReducer`; nested types inside (`ChatListState.Chats`); async parts are `Loading` / `Loaded` / `Error`, never null for loading |
| `XxxIntent` | what the user did | named after the fact (`ChatClicked`, `SendClicked`), not as a command |
| `XxxMutation` | what happened | goes only to the Reducer; the UI never sees it |
| `XxxEffect` | a one-off event | goes through the Store's `Channel` to `ObserveEffects` |
| `XxxReducer` | `internal object : Reducer` | pure: no coroutines, no I/O |
| `XxxExecutor` | the screen's logic | calls use cases; observes data in `whileSubscribed`, which runs only while the screen collects the state (stops 5 s after) |
| `XxxViewModel` | holds the Store | `storeFactory.create(...)` in `viewModelScope`; exposes `state`, `effects`, `onIntent` |

- **A screen is two overloads with one name**: the stateful `XxxScreen(callbacks, viewModel = koinViewModel())` collects `state` and `effects`; the `internal` stateless `XxxScreen(state, onIntent, modifier)` holds the Scaffold and is previewed. Sections with their own previews go to `components/` and get specific lambdas, not `onIntent`.
- **The Executor isn't in Koin**: the ViewModel makes it from its own dependencies, which also carries `chatId` from the navigation key. Koin would bind both Executors to the one `Executor` key.
- **`StoreFactory` comes from `:app`** (`AppModule`): in a debuggable build every Store logs its Intents, Mutations and Effects (`adb logcat -s Store`).
- **A text field is the one exception to "State only from the Reducer"**: a `TextFieldState` from the ViewModel (`savedStateHandle.savedTextField(KEY)`) inside the State, edited by the field as the user types, as Compose recommends.
- **Navigation goes through an Effect** (`navigate { }` in `ObserveEffects`), so every user action passes the Store. Android's guide suggests navigating from the UI instead; the Channel is safe here because the Store sends and `ObserveEffects` receives on `Main.immediate`.
- **`onStart` runs once when the Store is made** (MVIKotlin's Bootstrapper), not again after a rotation: opening a chat at its first unread message, loading the sign-in's users.
- **A lazy list keeps its first visible item in place by key**, so items added at its start (a chat that rose to the top, a message at the chat's bottom) land past the screen's edge. When the user is at that end, the screen calls `requestScrollToItem(0)` in a `SideEffect` (`FollowTop`, `FollowBottom`).

### Icons

Every icon is `AppIcons.X`: a Material Symbol as `ImageVector` in `core/designsystem/.../icons/symbols/`, one per file, named as on fonts.google.com/icons. There is no material-icons dependency; a new icon is an SVG converted with the Valkyrie plugin.

### Strings and comments

- Strings live in `src/main/res/values{,-ru}/strings.xml` of the module that shows them, prefixed with the module (`designsystem_`, `chats_`, `chat_`).
- Comments are English and short; none on obvious code, one on every non-obvious line (an order, a workaround, a reason).

### Tests

- JUnit4 with `kotlin.test` annotations, AssertK, Turbine, `kotlinx-coroutines-test`. Test names in backticks describe behaviour.
- ViewModel tests run in `viewModelTest {}` and keep the state collected with `keepCollecting(state)` (`:core:compose-utils` test fixtures): without a collector the Executor's `whileSubscribed` doesn't run.
- Reducers get plain tests, without coroutines (`ChatListReducerTest`).
- A test double used by one module stays in its tests (`TestMessagesRepository`); one used by two or more goes to the owner's `testFixtures`.
- The data layer is tested on an in-memory Room under Robolectric with a `TestServer` (`dataTest {}` in `:crossfeature:data`), with responses from `server/api-samples`.

### build-logic

`build-logic/` is an included build with the convention plugins. compileSdk, minSdk, targetSdk and Java live in `build-logic/src/main/kotlin/AndroidConfig.kt`. Each module declares only its own dependencies.

| Module | Applies |
|---|---|
| `:feature:<name>:impl` | `chatapp.android.feature` (library + compose + serialization + koin) + `screenshots` |
| `:feature:<name>:ui`, `:crossfeature:ui`, `:core:designsystem` | `android.library` + `android.compose` + `screenshots` |
| `:core:compose-utils` | `android.library` + `android.compose` |
| `:crossfeature:domain`, `:core:mvi` | `jvm.library` |
| `:core:network` | `jvm.library` + `koin` + `java-test-fixtures` |
| `:crossfeature:data` | `android.library` + `koin` + `ksp` + `androidx.room3` |
| `:app` | `android.application` + `android.compose` + `koin` |

## Build

| Command | Does |
|---|---|
| `./gradlew :app:assembleDebug` | builds the debug APK: `app/build/outputs/apk/debug/app-debug.apk`; checks the Koin graph |
| `./gradlew :app:installDebug` | builds and installs it on the connected device |
| `./gradlew test` | every test; screenshots compared with `<module>/screenshots/` |
| `cd server && ./gradlew test` | the server's tests, the API samples included |
| `server/deploy.sh` | tests, builds and restarts the server on this machine (see `server/README.md`) |

The app sends the server's key, `chatapp.apiKey` in `local.properties` (not in git). Without it the app builds, as on CI, and the server answers `401`.

The CI checks locally (CI treats Kotlin and lint warnings as errors):

```sh
./gradlew -PwarningsAsErrors=true spotlessCheck assertModuleGraph buildHealth lintDebug test :app:assembleDebug
```

> [!WARNING]
> Never run two Gradle builds in the same checkout at once: they share `build/` dirs and corrupt each other's outputs.

### Screenshots

Every `@Preview` of a module with `chatapp.android.screenshots` is a screenshot test.

1. Check that only the screens meant to change did: `./gradlew test` fails only those previews and leaves `build/outputs/roborazzi/*_compare.png`.
2. Record: `./gradlew recordRoborazziDebug -Proborazzi.cleanupOldScreenshots=true` saves the new screenshots and deletes those of removed previews.
3. Commit the images with the change.

- **Don't record when a change wasn't meant to alter the UI**: a failed screenshot test is then a real problem.
- **Never put the cleanup flag in `gradle.properties`**: a filtered test run would then delete the module's other screenshots.
