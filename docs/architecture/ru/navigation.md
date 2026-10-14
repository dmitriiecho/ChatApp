# Навигация

[English version](../en/navigation.md) · [Все страницы](README.md)

Приложение использует [Navigation 3](https://developer.android.com/guide/navigation/navigation-3). Стек экранов в ней — обычный список ключей, которым владеет приложение и который оно меняет само; `NavDisplay` показывает экран последнего ключа.

&nbsp;

## Ключи

У каждого экрана есть ключ: `@Serializable`-класс, реализующий `NavKey`, с аргументами экрана в полях. Фича держит свои ключи в одном объекте, и публичен только ключ первого экрана:

```kotlin
object ChatsRoutes {
    // The feature's first screen: the app opens the feature with it.
    @Serializable
    data object ChatList : NavKey

    @Serializable
    internal data class Chat(val chatId: String) : NavKey
}
```

Стек экранов переживает смерть процесса, поэтому сохраняется через kotlinx.serialization. Фича перечисляет свои ключи в `SerializersModule`, а приложение собирает их в одну конфигурацию:

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

Без этой конфигурации `rememberNavBackStack` ищет ключи через рефлексию, а это работает только на Android; с ней код остаётся готовым к Kotlin Multiplatform.

&nbsp;

## Записи

Фича превращает свои ключи в экраны в расширении `EntryProviderScope`, а приложение вызывает его внутри `NavDisplay`:

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

- **Переходы между экранами одной фичи** идут через `Navigator` из `:core:compose-utils` — интерфейс из двух методов (`navigate(key)`, `goBack()`). Приложение реализует его как `AppNavigator` над своим стеком.
- **Пути между фичами — колбэки в `:app`**: фичи не знают друг о друге.
- **То, что принадлежит приложению, передаётся внутрь**: тема живёт в `MainActivity`, поэтому список чатов получает только `onToggleTheme`.

&nbsp;

## Стек экранов следует за сессией

После входа никто не вызывает «перейти к списку чатов». Приложение следит за сессией, и стек пересобирается, когда она меняется:

```kotlin
LaunchedEffect(isSignedIn) {
    val isOnSignIn = backStack.lastOrNull() == LoginRoutes.Login
    if (isSignedIn == isOnSignIn) {
        backStack.clear()
        backStack.add(startKey(isSignedIn))   // ChatsRoutes.ChatList or LoginRoutes.Login
    }
}
```

- **Вход и выход заменяют стек целиком**, поэтому «Назад» никогда не ведёт на другую сторону.
- **У экрана входа вообще нет колбэков**: он вызывает `login(user)`, сессия меняется, и приложение открывает список чатов. Выход из списка работает так же, в обратную сторону.
- **Пока сессия читается** из Room, приложение не показывает ничего, чтобы на мгновение не мелькнул экран входа.

&nbsp;

## У каждого экрана свои ViewModel

`NavDisplay` получает два декоратора записей, в таком порядке:

```kotlin
entryDecorators = listOf(
    // Must come first: the other decorators save state through it.
    rememberSaveableStateHolderNavEntryDecorator(),
    // Each screen gets its own ViewModels (koinViewModel()), cleared when the screen leaves the back stack.
    rememberViewModelStoreNavEntryDecorator(),
),
```

Поэтому у двух чатов, открытых один поверх другого, два `ChatViewModel`, а закрытие чата очищает его ViewModel и Store внутри. Аргументы экрана попадают из ключа во ViewModel через Koin:

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

## Навигация идёт через Effect

Нажатие на чат не переходит на экран прямо из UI: это Intent, а Executor отвечает Effect'ом, который экран превращает в колбэк:

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

Руководство Android советует переходить прямо из UI. Здесь каждое действие пользователя проходит через Store, поэтому его лог (`adb logcat -s Store`) показывает всю историю, а тест ViewModel проверяет навигацию как ещё один Effect. Channel для этого безопасен: Store отправляет, а `ObserveEffects` получает на `Main.immediate` (см. [Экраны](screens.md#effect)).

&nbsp;

## Записи помнят то, что захватили

Navigation 3 хранит содержимое записи, пока не изменится стек. Значение, захваченное при создании записи, остаётся прежним, даже когда приложение потом передаёт новое, поэтому есть два правила:

> [!IMPORTANT]
> Колбэк, который может измениться, читается через `rememberUpdatedState`, а значение, которое меняется, пока экран показан (тема), приходит из CompositionLocal (`LocalDarkTheme`), а не параметром.

```kotlin
// AppNavHost: entries call the latest callback through this state.
val currentOnToggleTheme by rememberUpdatedState(onToggleTheme)
// ...
chatsEntries(navigator, onToggleTheme = { currentOnToggleTheme() })
```

Кнопка темы в списке чатов берёт иконку из `LocalDarkTheme.current`. Будь тема параметром, внутри записи она осталась бы с первым значением, и иконка перестала бы следовать за темой.

&nbsp;

## Без анимаций переходов

По умолчанию `NavDisplay` анимирует каждое изменение стека плавным перетеканием на 700 мс. Для чата это казалось медленным, поэтому все три перехода выключены:

```kotlin
transitionSpec = { noTransition },
popTransitionSpec = { noTransition },
predictivePopTransitionSpec = { noTransition },
```

Перетекание ещё и прятало первые кадры чата, пока его сообщения не пришли из Room; без него чат может на мгновение показаться пустым (на эмуляторе меньше 0,3 с).
