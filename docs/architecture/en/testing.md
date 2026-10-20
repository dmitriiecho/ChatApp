# Testing

[Русская версия](../ru/testing.md) · [All pages](README.md)

Tests run on the JVM, without an emulator. They cover the MVI core, the screens' logic, the data layer with its syncs, the screens themselves (a screenshot of every preview), the contract with the server, and the server.

&nbsp;

## Running the tests

Each build runs its own tests, and CI runs both sets on every pull request:

```sh
./gradlew test                # the app: every module, once, including screenshots
cd server && ./gradlew test   # the server
./gradlew recordRoborazziDebug -Proborazzi.cleanupOldScreenshots=true  # records new screenshots after a UI change
```

Android modules run their unit tests in the debug variant only (`chatapp.android.library` turns off the release ones, which would just repeat them), so a single `test` covers Android and pure Kotlin modules alike.

&nbsp;

## How a test is written

Here is a short test from the project:

```kotlin
@Test
fun `while the server hasn't answered, the switch shows the user's choice`() {
    val state = ChatListState().reduce(
        ChatListMutation.BotsChanged(true),
        ChatListMutation.BotsRequested(false),
        // Another device's change arriving meanwhile.
        ChatListMutation.BotsChanged(true),
    )

    assertThat((state.bots as ChatListState.Bots.Known).isOn).isEqualTo(false)
}
```

- **One behaviour per test**, and **the name states it**, in backticks: `` `offline the bots switch goes back and a snackbar says why` ``, not `` `bots error` ``.
- **Arrange, act, assert**, separated by blank lines.
- **A test's data lives in the test.** Builders with defaults (`conversation("c", unreadCount = 2)`, `messageDto("c", 3)`) let a test set only the values that matter to it.
- **Working stand-ins instead of mocks.** Repositories are interfaces, so a test uses an in-memory implementation with real behaviour; there is no mocking library.
- **No real time**: coroutine tests run on `runTest` with its virtual clock.
- **Every new test must be seen failing at least once**: break the code it checks, watch the test fail, then restore the code.

&nbsp;

## Names and places

Everything made for tests starts with `Test` or `test`:

| What | Name | Example |
|---|---|---|
| Test class | the class it checks + `Test`, in the same package | `ChatPaginator` → `ChatPaginatorTest` |
| Stand-in implementation | `Test` + what it replaces | `TestMessagesRepository`, `TestServer` |
| Entry function of a test kind | kind + `Test` | `viewModelTest {}`, `dataTest {}`, `networkTest {}`, `serverTest {}` |
| Action a test performs | a verb | `typeText()`, `keepCollecting()` |

A helper used by the tests of **two or more modules** lives in the **test fixtures** of the module that owns what it replaces; a helper used by a single module stays in that module's `src/test`:

| Helper | Module | Source set |
|---|---|---|
| `viewModelTest {}`, `keepCollecting()`, `typeText()`, `PausedClockPreviewTester` | `:core:compose-utils` | `src/testFixtures` |
| `networkTest {}`, `TestServer`, `apiSample()`, `apiRequest()` | `:core:network` | `src/testFixtures` |
| `dataTest {}`, `messageDto()`, `conversationDto()` | `:crossfeature:data` | `src/test` |
| `TestMessagesRepository`, `TestConversationsRepository`, `TestBotsRepository`… | `:feature:chats:impl` | `src/test` |
| `serverTest {}`, `TestData`, `apiSample()`, `sendApiRequest()` | `server/` | `src/test` |

&nbsp;

## Tools

| Tool | For |
|---|---|
| `kotlin.test` | `@Test` and the other annotations; underneath, the runner is JUnit4 in the app (Robolectric works only with it) and JUnit5 on the server |
| [AssertK](https://github.com/assertk-org/assertk) | every check: `assertThat(actual).isEqualTo(expected)`, `isInstanceOf<T>()`, `assertFailure {}` |
| `kotlinx-coroutines-test` | `runTest` and the virtual clock |
| [Turbine](https://github.com/cashapp/turbine) | a screen's one-off Effects: `effects.test { awaitItem() }` |
| Ktor `MockEngine` | answers the network code's requests without a real server (`TestServer`) |
| [Robolectric](https://robolectric.org) | Android classes on the JVM, only for code that can't run without them (Room, screenshots) |
| [Roborazzi](https://github.com/takahirom/roborazzi) | screenshot tests generated from the `@Preview` functions |

&nbsp;

## Kinds of tests

Each kind of test starts with an entry function that sets up what that kind needs.

### The Store

`StoreTest` in `:core:mvi` checks the loop itself on a small counter: Mutations go through the Reducer, an Effect sent while nobody collects waits for the screen, `onStart` runs once, and `whileSubscribed` starts with the first subscriber and stops 5 s after the last:

```kotlin
@Test
fun `a subscriber back within 5 s, as after a rotation, keeps observing`() = runTest {
    val store = store()
    subscribe(store).cancel()
    advanceTimeBy(3_000)

    subscribe(store)
    advanceTimeBy(10_000)
    runCurrent()

    assertThat(executor.isObserving).isTrue()
    assertThat(executor.observeStarts).isEqualTo(1)
}
```

### ViewModel

`viewModelTest {}` replaces `Dispatchers.Main`, where `viewModelScope` runs, with a test dispatcher on `runTest`'s virtual clock. The ViewModel gets its real use cases backed by `Test` repositories, so a test sets up the data and checks what the screen receives:

```kotlin
@Test
fun `offline the bots switch goes back and a snackbar says why`() = viewModelTest {
    bots.isOffline = true
    val viewModel = viewModel()
    keepCollecting(viewModel.state)

    viewModel.effects.test {
        viewModel.onIntent(ChatListIntent.BotsToggled(enabled = false))

        assertThat(awaitItem()).isEqualTo(ChatListEffect.ShowBotsError)
    }
    assertThat(viewModel.state.value.bots).isEqualTo(ChatListState.Bots.Known(isEnabled = true))
}
```

- **The state is collected the whole time, as the screen does**: `keepCollecting(viewModel.state)` right after the ViewModel is created. Without a collector the Executor's `whileSubscribed` doesn't run, and the State keeps its initial value.
- **Typing goes through `typeText()`**: on a device, Compose applies a change to a field on the next frame, and a test has no frames.
- **Time is virtual**: `advanceTimeBy()` moves it past the one-second pause of read marks without waiting.

### Reducer

A Reducer is a pure function, so its test has no coroutines at all: it applies a few Mutations in turn and checks the State (see the example [above](#how-a-test-is-written)).

### Data

`dataTest {}` builds the data layer as the app wires it, on an in-memory Room and a `TestServer`, signed in as Anna. Room needs an Android `Context`, so these test classes run on Robolectric:

```kotlin
@RunWith(RobolectricTestRunner::class)
class RealtimeHandlerTest {

    @Test
    fun `a new message after a gap or in a chat not cached is left for the chat to load`() = dataTest {
        cacheMessages("c", 1L..2L)

        realtime.onEvent(RealtimeEventDto.MessageNew(messageDto("c", 5)))
        realtime.onEvent(RealtimeEventDto.MessageNew(messageDto("other", 1)))

        assertThat(db.messageDao().maxSeq("c")).isEqualTo(2)
        assertThat(db.messageDao().maxSeq("other")).isEqualTo(null)
    }
}
```

- **The DAO tests** check the windows on real SQL: a window never steps over a gap, the head grows by itself, an older version doesn't overwrite a newer one.
- **The sync and outbox tests** queue the server's answers and check what ends up in Room: batches, `resetRequired`, a resend after a failure, the gap loaded before a sent message.
- **The test closes the data layer's scope before the database**: a collector still reading from a closed database would throw.

### Network

`networkTest {}` gives the test a `TestServer` and a client with the app's settings ([`serverApi`](../../../core/network/src/main/kotlin/krio/systemdesign/chatapp/core/network/NetworkJson.kt)), so it reads JSON with the same `networkJson`. `TestServer` answers with responses queued in advance and records the requests; it runs on Ktor's `MockEngine`:

```kotlin
@Test
fun `an error code is an IOException`() = networkTest { server, client ->
    server.enqueue(code = 500)

    assertFailure { client.item() }.isInstanceOf<IOException>()
}
```

### Contract with the server

The DTOs are shared through `contract/`, so the field names can't drift apart, but the exact JSON and the addresses still can. What keeps them in sync is [`server/api-samples/`](../../../server/api-samples/): one JSON file for each request or response the app relies on, and `requests.json` with each request's method and address (`"set-bots": "PUT /bots"`).

| Side | Checks |
|---|---|
| Server | it accepts the request samples at their addresses and answers exactly like the response samples, events included |
| App | it sends requests exactly like the request samples, to their addresses, and reads the response samples |

`apiSample("bots.json")` reads a sample on either side, parsed with that side's own `Json`, and the check compares JSON trees: field order doesn't matter, but a renamed, extra or missing field does. Changing a format or an address on one side fails that side's tests until the sample is updated, and then the other side has to follow.

Both builds declare the samples folder as an input of the tests that read it (the server's, and the app's in `:core:network` and `:crossfeature:data`), so after a sample is edited, Gradle runs them again instead of treating them as up to date.

### Screenshots

Every `@Preview` is a screenshot test, so nobody has to write these tests by hand. `chatapp.android.screenshots` has Roborazzi render each preview on Robolectric and compare it with the image saved in the module's `screenshots/` folder:

| Command | Does |
|---|---|
| `./gradlew test` | compares every preview with its saved image and fails if they differ |
| `./gradlew recordRoborazziDebug -Proborazzi.cleanupOldScreenshots=true` | renders the previews and saves them as the new images; deletes the images of previews that no longer exist |

These tests don't tell you whether a screen looks right; they catch changes nobody asked for. That's why images are recorded only by a person, on purpose, after a UI change:

1. Change the UI.
2. Run `./gradlew test`. Only the previews you meant to change should fail, and each one's `build/outputs/roborazzi/…_compare.png` shows the old image, the difference in red and the new one.
3. Record the images.
4. Commit the images together with the code.

> [!IMPORTANT]
> If you didn't mean to change how the app looks, don't record. A failed screenshot test has then caught a real problem: padding that shifted during a refactor, a library update that changed the buttons, a shared component that affected another screen. CI never records images.

- **The clock is paused**: `PausedClockPreviewTester` renders every preview at its first frame, so an endless animation (a spinner) doesn't hang the test.
- **Times are in UTC** in every screenshot test, so an image recorded on one machine matches on CI.
- **Old images are deleted only when recording**, with that flag: Roborazzi deletes every image the run didn't render, so if the flag were always on, running a single test would delete all the module's other images.
- **Private previews are included too**: Slack's lint rules require previews to be private, and the plugin picks them up.

### Server

`serverTest {}` starts the server in memory with Ktor's `testApplication`, on an in-memory SQLite with the test's own data (`TestData`: users and chats put straight into the tables), a stopped clock, and a client that sends every request as Anna with the test key. A request from the samples is sent with `sendApiRequest("set-bots")`, which fails on any non-success status. The bots get their own tests with a fixed random seed and no pauses. More on the server's API is in [`server/README.md`](../../../server/README.md).
