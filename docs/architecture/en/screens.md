# Screens

[Русская версия](../ru/screens.md) · [All pages](README.md)

Every screen runs on MVI, on a small core of the project's own in `:core:mvi`. Data goes one way, in a loop: the UI sends an Intent, the Executor does the work and reports Mutations, the Reducer turns each into a new State, and the UI draws it. One-off events, like navigation and snackbars, go from the Executor to the UI as Effects.

```text
UI ──Intent──▶ Store ──▶ Executor ──Mutation──▶ Reducer ──▶ State ──▶ UI
                            └────────────Effect─────────────────────▶ UI
```

&nbsp;

## The parts

Each screen has the same set of types. Here is the chat list as an example (`feature/chats/impl/.../presentation/chatlist/`):

| Type | Is | Rule |
|---|---|---|
| `ChatListState` | what the screen shows | made only by the Reducer; async parts are `Loading` / `Loaded`, never `null` for loading |
| `ChatListIntent` | what the user did | named after the fact (`ChatClicked`, `BotsToggled`), not as a command |
| `ChatListMutation` | what happened | goes only to the Reducer; the UI never sees it |
| `ChatListEffect` | a one-off event | goes through the Store's `Channel` to `ObserveEffects` |
| `ChatListReducer` | `internal object : Reducer` | pure: no coroutines, no I/O |
| `ChatListExecutor` | the screen's logic | calls use cases; observes data only while the screen is visible |
| `ChatListViewModel` | holds the Store | outlives a rotation; exposes `state`, `effects` and `onIntent` |

The State, Intents, Mutations and Effects sit together in one `ChatListContract.kt`; the Reducer, the Executor and the ViewModel have a file each.

&nbsp;

## Why an own core

MVIKotlin and Orbit are the well-known MVI libraries for Kotlin. The core here is about 110 lines of code and borrows MVIKotlin's names (Store, Executor, Reducer, the Bootstrapper as `onStart`), but stays small enough to read in one sitting and to change when a screen needs something new. It is pure Kotlin, with no Android, so the screens' logic is ready for Kotlin Multiplatform.

| Class | Does |
|---|---|
| `Store` | the loop: `accept(intent)` in, `state` and `effects` out |
| `Executor` | the screen's logic: `onStart`, `whileSubscribed`, `execute(intent)` |
| `Reducer` | `reduce(state, mutation)`: the only place a new State is made |
| `StoreAccess` | what an Executor may do in a call: read the State, `dispatch` a Mutation, `publish` an Effect |
| `StoreObserver` | sees every Intent, Mutation and Effect and changes nothing: for logs |
| `StoreFactory` | makes every Store of the app; in a debuggable build each Store logs under its name |

&nbsp;

## The Store

The State is a `StateFlow` that only the Reducer changes; an Effect goes into a buffered `Channel`:

```kotlin
override fun dispatch(mutation: Mutation) {
    // updateAndGet calls the Reducer again if another Mutation got in between: safe, as it is pure.
    val newState = _state.updateAndGet { state -> reducer.reduce(state, mutation) }
    observers.forEach { it.onMutation(mutation, newState) }
}

fun accept(intent: Intent) {
    observers.forEach { it.onIntent(intent, _state.value) }
    // Each Intent gets its own coroutine, so a slow one doesn't hold the next.
    scope.launch { executor.execute(intent, access) }
}
```

- **The Store runs in `viewModelScope`**, on the main thread. A check of the State in the Executor, like "a request is already on its way", then holds until its next suspension, and a quick double tap is ignored without a lock.
- **An Effect waits in the Channel** when the screen isn't collecting: during a rotation, or while the app is in the background. A `SharedFlow` would drop it.
- **The Executor gets the Store only for the length of a call** (`StoreAccess`), so it can't change the State from a stale coroutine after the call is over.

&nbsp;

## The Executor

An Executor has three entry points:

```kotlin
interface Executor<Intent, State, Mutation, Effect> {
    suspend fun onStart(store: StoreAccess<State, Mutation, Effect>) {}
    suspend fun whileSubscribed(store: StoreAccess<State, Mutation, Effect>) {}
    suspend fun execute(intent: Intent, store: StoreAccess<State, Mutation, Effect>)
}
```

| Entry point | Runs | For |
|---|---|---|
| `onStart` | once, when the Store is made; not again after a rotation | one-off work of the start: opening a chat at its first unread message, loading the users of the sign-in |
| `whileSubscribed` | while the screen collects the State; cancelled 5 s after it stops | observing data, so nothing is observed in the background |
| `execute` | for each Intent, in its own coroutine | the user's actions |

The 5 seconds are the same rule as `stateIn(WhileSubscribed(5_000))`: a rotation resubscribes within the timeout, so the observation doesn't restart. The chat list observes its window and the session this way:

```kotlin
override suspend fun whileSubscribed(store: ChatListStore) = coroutineScope {
    launch {
        paginator.observe().collect { (window, slice) ->
            store.dispatch(ChatListMutation.SliceChanged(slice, hasAbove = window.offset > 0))
        }
    }
    launch { observeBots().collect { store.dispatch(ChatListMutation.BotsChanged(it)) } }
    observeSession().collect { store.dispatch(ChatListMutation.UserChanged(it?.userName)) }
}
```

An Executor must not throw: an uncaught exception in `viewModelScope` crashes the app. The network fails only as `IOException` (see [Data](data.md#network)), which the Executor turns into an Effect such as a snackbar.

&nbsp;

## The ViewModel

The ViewModel only holds the Store. It builds the Executor itself from its own dependencies, which also carries the screen's arguments:

```kotlin
@KoinViewModel
internal class ChatViewModel(
    @InjectedParam chatId: String,
    savedStateHandle: SavedStateHandle,
    storeFactory: StoreFactory,
    // the use cases ...
) : ViewModel() {

    private val store = storeFactory.create(
        name = "Chat",
        // The typed text survives process death.
        initialState = ChatState(input = savedStateHandle.savedTextField(INPUT_KEY)),
        reducer = ChatReducer,
        executor = ChatExecutor(conversationId = chatId, /* ... */),
        scope = viewModelScope,
    )

    val state: StateFlow<ChatState> = store.state
    val effects: Flow<ChatEffect> = store.effects
    fun onIntent(intent: ChatIntent) = store.accept(intent)
}
```

The Executor isn't in Koin: Koin would bind both screens' Executors to the one `Executor` key.

`StoreFactory` comes from `:app`. In a debuggable build every Store logs its Intents, Mutations and Effects to Logcat:

```sh
adb logcat -s Store
```

```text
D Store   : Chat: intent JumpSomewhereClicked
D Store   : Chat: effect ScrollToSeq(seq=1193)
D Store   : Chat: mutation PageChanged(page=Page(messages=[...], hasOlder=true, hasNewer=true)) -> state ChatState(...)
```

&nbsp;

## The State

Types that exist only as part of the State are nested inside it, and loading is a state, not a flag or `null`:

```kotlin
internal data class ChatListState(
    val chats: Chats = Chats.Loading,
    val hasAbove: Boolean = false,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val userName: String? = null,
    val bots: Bots = Bots.Unknown,
) {
    sealed interface Chats {
        // Until the list's first sync: an empty list then means "not loaded yet", not "no chats".
        data object Loading : Chats
        data class Loaded(val items: List<Item>) : Chats
    }
    // ...
}
```

The Reducer is a plain `when` over the Mutations. It has no coroutines, so a test calls it directly:

```kotlin
internal object ChatListReducer : Reducer<ChatListState, ChatListMutation> {
    override fun reduce(state: ChatListState, mutation: ChatListMutation): ChatListState = when (mutation) {
        is ChatListMutation.LoadingMoreChanged -> state.copy(isLoadingMore = mutation.isLoading)
        is ChatListMutation.UserChanged -> state.copy(userName = mutation.name)
        // ...
    }
}
```

&nbsp;

## Text fields

A text field is the one exception to "the State is made only by the Reducer". The field's `TextFieldState` is created in the ViewModel and put into the State, and the field edits it directly as the user types, as Compose recommends:

```kotlin
// The typed text survives process death.
initialState = ChatState(input = savedStateHandle.savedTextField(INPUT_KEY)),
```

- **There is no Intent per keystroke.** Typing never waits for the Store and can't drop characters.
- **Values derived from the text are getters** on the State: `val canSend get() = input.text.isNotBlank()`.
- **The Executor reads the text when the user sends** and clears the field first, so a second tap finds it empty and can't send the message twice.

&nbsp;

## Effects

A screen handles Effects with [`ObserveEffects`](../../../core/compose-utils/src/main/kotlin/krio/systemdesign/chatapp/core/composeutils/effects/ObserveEffects.kt):

```kotlin
ObserveEffects(viewModel.effects) { effect ->
    when (effect) {
        is ChatListEffect.OpenChat -> navigate { onOpenChat(effect.chatId) }
        ChatListEffect.ShowError -> showSnackbar(snackbarHostState, errorMessage)
        ChatListEffect.ShowBotsError -> showSnackbar(snackbarHostState, botsErrorMessage)
    }
}
```

- **Effects are collected while the screen is visible** (`repeatOnLifecycle(STARTED)`), on `Main.immediate`: the screen handles an Effect right inside the Store's `send`. With a pause in between, a screen stopping at that moment could take the Effect from the channel and lose it.
- **`navigate { }` runs only while the screen is on top**, so after a quick double tap the second navigation is dropped.
- **`showSnackbar` runs in its own coroutine**: a snackbar suspends until it is dismissed and would otherwise hold up the next Effect.

&nbsp;

## Two screen overloads

A screen has two functions with the same name. The first gets the ViewModel and collects the State and the Effects; the second only draws:

```kotlin
@Composable
internal fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = koinViewModel { parametersOf(chatId) },
)

@Composable
internal fun ChatScreen(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    modifier: Modifier = Modifier,
    // ...
)
```

The second one doesn't depend on Koin, so it is previewed in any State. `onIntent` stays in the screen's file; sections in `components/` (`MessageBubble`, `MessageInput`, `BotsBar`) get specific callbacks instead, so they know nothing about the screen's Intents and are previewed on their own.

&nbsp;

## Lists that grow at an edge

A `LazyColumn` keeps its first visible item in place by key. When a chat rises to the top of the list, or a new message comes at the bottom of a chat, the new item lands just past the screen's edge, and a user at that edge wouldn't see it. So when the user is at the edge, the screen asks the list to stay there:

```kotlin
// FollowBottom in ChatScreen.kt; FollowTop in ChatListScreen.kt is the same at the top
SideEffect {
    val isAtBottom = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    if (bottomKey != shownBottomKey[0] && isAtBottom) listState.requestScrollToItem(0)
    shownBottomKey[0] = bottomKey
}
```

`SideEffect` runs before the list measures the change, while it is still where the user left it. A user reading higher up keeps their place. In a chat this is only for the head: a page loaded below a window up in the history stays below, where the user scrolls to it (see [Pagination](pagination.md#new-messages-at-the-bottom)).

&nbsp;

## Components and previews

A screen is assembled from ready-made components: every styled element comes from `:core:designsystem` or `:crossfeature:ui` (see [Modules](modules.md#where-new-code-goes)). The top bar of every screen is `AppTopBar`, and the bars at the bottom are `AppBottomBar`, so the screens share one frame.

Previews live next to the code they show, are private, and come in light and dark themes. Every preview is also a [screenshot test](testing.md#screenshots).
