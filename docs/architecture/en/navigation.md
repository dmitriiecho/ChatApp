# Navigation

[Русская версия](../ru/navigation.md) · [All pages](README.md)

The app uses [Navigation 3](https://developer.android.com/guide/navigation/navigation-3). Its back stack is a plain list of keys that the app owns and changes itself; `NavDisplay` shows the last key's screen.

&nbsp;

## Keys

Every screen has a key: a `@Serializable` class implementing `NavKey`, with the screen's arguments as fields. A feature keeps its keys in one object, and only the first screen's key is public:

```kotlin
object ChatsRoutes {
    // The feature's first screen: the app opens the feature with it.
    @Serializable
    data object ChatList : NavKey

    @Serializable
    internal data class Chat(val chatId: String) : NavKey
}
```

The back stack survives process death, so it is saved with kotlinx.serialization. A feature lists its keys in a `SerializersModule`, and the app joins them into one configuration:

```kotlin
val chatsNavKeys = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(ChatsRoutes.ChatList::class)
        subclass(ChatsRoutes.Chat::class)
    }
}

// app: AppNavHost.kt
private val navKeysConfiguration = SavedStateConfiguration {
    serializersModule = chatsNavKeys + loginNavKeys
}
```

Without that configuration, `rememberNavBackStack` finds the keys by reflection, which works only on Android; with it the code stays ready for Kotlin Multiplatform.

&nbsp;

## Entries

A feature turns its keys into screens in an extension on `EntryProviderScope`, and the app calls it inside `NavDisplay`:

```kotlin
fun EntryProviderScope<NavKey>.chatsEntries(
    navigator: Navigator,
    onToggleTheme: () -> Unit,
) {
    entry<ChatsRoutes.ChatList> {
        ChatListScreen(
            onOpenChat = { chatId -> navigator.navigate(ChatsRoutes.Chat(chatId)) },
            onToggleTheme = onToggleTheme,
        )
    }
    entry<ChatsRoutes.Chat> { key ->
        ChatScreen(chatId = key.chatId, onBack = navigator::goBack)
    }
}
```

- **Moving between a feature's own screens** goes through `Navigator` from `:core:compose-utils`, a two-method interface (`navigate(key)`, `goBack()`). The app implements it as `AppNavigator` over its back stack.
- **Paths between features are callbacks in `:app`**, since features don't know each other.
- **What belongs to the app is passed in**: the theme lives in `MainActivity`, so the chat list only gets `onToggleTheme`.

&nbsp;

## The back stack follows the session

There is no "go to the chat list" call after signing in. The app watches the session, and the back stack is rebuilt whenever it changes:

```kotlin
LaunchedEffect(isSignedIn) {
    val isOnSignIn = backStack.lastOrNull() == LoginRoutes.Login
    if (isSignedIn == isOnSignIn) {
        backStack.clear()
        backStack.add(startKey(isSignedIn))   // ChatsRoutes.ChatList or LoginRoutes.Login
    }
}
```

- **A sign-in or a sign-out replaces the stack whole**, so Back never leads to the other side.
- **The sign-in screen has no callbacks at all**: it calls `login(user)`, the session changes, and the app opens the chat list. Signing out from the list works the same way in reverse.
- **While the session loads** from Room, the app shows nothing rather than flashing the sign-in.

&nbsp;

## Each screen has its own ViewModels

`NavDisplay` gets two entry decorators, in this order:

```kotlin
entryDecorators = listOf(
    // Must come first: the other decorators save state through it.
    rememberSaveableStateHolderNavEntryDecorator(),
    // Each screen gets its own ViewModels (koinViewModel()), cleared when the screen leaves the back stack.
    rememberViewModelStoreNavEntryDecorator(),
),
```

So two chats opened one over the other have two `ChatViewModel`s, and closing a chat clears its ViewModel and the Store inside. A screen's arguments go from its key to its ViewModel through Koin:

```kotlin
// ChatScreen.kt
viewModel: ChatViewModel = koinViewModel { parametersOf(chatId) },

// ChatViewModel.kt
@KoinViewModel
internal class ChatViewModel(
    @InjectedParam chatId: String,
    savedStateHandle: SavedStateHandle,
    // ...
) : ViewModel()
```

&nbsp;

## Navigation goes through an Effect

A tap on a chat doesn't navigate from the UI: it is an Intent, and the Executor answers with an Effect that the screen turns into the callback:

```kotlin
// ChatListExecutor
is ChatListIntent.ChatClicked -> store.publish(ChatListEffect.OpenChat(intent.chatId))

// ChatListScreen
ObserveEffects(viewModel.effects) { effect ->
    when (effect) {
        is ChatListEffect.OpenChat -> navigate { onOpenChat(effect.chatId) }
        // ...
    }
}
```

Android's guide suggests navigating from the UI instead. Here every user action passes the Store, so its log (`adb logcat -s Store`) shows the whole story, and a test of the ViewModel checks navigation as one more Effect. The Channel is safe for this because the Store sends and `ObserveEffects` receives on `Main.immediate` (see [Screens](screens.md#effects)).

&nbsp;

## Entries keep what they captured

Navigation 3 keeps an entry's content until the back stack changes. A value captured when the entry was created stays the same even when the app later passes a new one, so two rules follow:

> [!IMPORTANT]
> A callback that can change is read through `rememberUpdatedState`, and a value that changes while the screen is shown (the theme) comes from a CompositionLocal (`LocalDarkTheme`), not a parameter.

```kotlin
// AppNavHost: entries call the latest callback through this state.
val currentOnToggleTheme by rememberUpdatedState(onToggleTheme)
// ...
chatsEntries(navigator, onToggleTheme = { currentOnToggleTheme() })
```

The theme button in the chat list reads `LocalDarkTheme.current` for its icon. Passed down as a parameter, the theme would keep its first value inside the entry, and the icon would stop following the theme.

&nbsp;

## No transitions

`NavDisplay` animates every change of the back stack by default, with a 700 ms crossfade. That felt slow for a chat, so all three transitions are off:

```kotlin
transitionSpec = { noTransition },
popTransitionSpec = { noTransition },
predictivePopTransitionSpec = { noTransition },
```

The crossfade also hid the first frames of a chat before its messages came from Room; without it, a chat may show empty for a moment (under 0.3 s on the emulator).
