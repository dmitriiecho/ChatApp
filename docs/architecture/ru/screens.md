# Экраны

[English version](../en/screens.md) · [Все страницы](README.md)

Каждый экран построен на MVI с небольшим собственным ядром проекта в `:core:mvi`. Данные ходят по кругу в одну сторону: UI отправляет Intent, Executor выполняет работу и сообщает о результатах через Mutation, Reducer превращает каждую из них в новый State, а UI его рисует. Разовые события, например навигация и снэкбары, идут от Executor к UI в виде Effect.

```text
UI ──Intent──▶ Store ──▶ Executor ──Mutation──▶ Reducer ──▶ State ──▶ UI
                            └────────────Effect─────────────────────▶ UI
```

&nbsp;

## Составные части

У каждого экрана один и тот же набор типов. Пример — список чатов (`feature/chats/impl/.../presentation/chatlist/`):

| Тип | Что это | Правило |
|---|---|---|
| `ChatListState` | то, что показывает экран | создаёт только Reducer; асинхронные части — `Loading` / `Loaded`, загрузка никогда не обозначается `null` |
| `ChatListIntent` | что сделал пользователь | назван по тому, что произошло (`ChatClicked`, `BotsToggled`), а не как команда |
| `ChatListMutation` | что произошло | идёт только в Reducer; UI её никогда не видит |
| `ChatListEffect` | разовое событие | идёт через `Channel` внутри Store в `ObserveEffects` |
| `ChatListReducer` | `internal object : Reducer` | чистый: без корутин и ввода-вывода |
| `ChatListExecutor` | логика экрана | вызывает use case; наблюдает за данными, только пока экран виден |
| `ChatListViewModel` | держит Store | переживает поворот; отдаёт `state`, `effects` и `onIntent` |

State, Intent, Mutation и Effect лежат вместе в одном `ChatListContract.kt`; у Reducer, Executor и ViewModel — по отдельному файлу.

&nbsp;

## Почему своё ядро

Известные MVI-библиотеки для Kotlin — MVIKotlin и Orbit. Ядро проекта — около 110 строк кода. Названия оно берёт у MVIKotlin (Store, Executor, Reducer, а Bootstrapper здесь называется `onStart`), но остаётся настолько маленьким, что его можно прочитать за один присест и поменять, когда экрану понадобится что-то новое. Это чистый Kotlin без Android, поэтому логика экранов готова к Kotlin Multiplatform.

| Класс | Делает |
|---|---|
| `Store` | сам цикл: на вход `accept(intent)`, на выход `state` и `effects` |
| `Executor` | логика экрана: `onStart`, `whileSubscribed`, `execute(intent)` |
| `Reducer` | `reduce(state, mutation)`: единственное место, где создаётся новый State |
| `StoreAccess` | что Executor может сделать за один вызов: прочитать State, отправить Mutation (`dispatch`), опубликовать Effect (`publish`) |
| `StoreObserver` | видит каждый Intent, Mutation и Effect и ничего не меняет: для логов |
| `StoreFactory` | создаёт каждый Store приложения; в debug-сборке каждый Store пишет лог под своим именем |

&nbsp;

## Store

State — это `StateFlow`, который меняет только Reducer; Effect попадает в буферизованный `Channel`:

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

- **Store работает в `viewModelScope`**, на главном потоке. Поэтому, когда Executor проверяет State (например, «запрос уже отправлен»), ничто не изменит его до следующей приостановки Executor, и быстрое двойное нажатие отсекается без всяких блокировок.
- **Effect ждёт в Channel**, пока экран не собирает Effect'ы: во время поворота или пока приложение в фоне. `SharedFlow` бы его потерял.
- **Executor получает Store только на время вызова** (`StoreAccess`), поэтому не может поменять State из устаревшей корутины, когда вызов уже закончился.

&nbsp;

## Executor

У Executor три точки входа:

```kotlin
interface Executor<Intent, State, Mutation, Effect> {
    suspend fun onStart(store: StoreAccess<State, Mutation, Effect>) {}
    suspend fun whileSubscribed(store: StoreAccess<State, Mutation, Effect>) {}
    suspend fun execute(intent: Intent, store: StoreAccess<State, Mutation, Effect>)
}
```

| Точка входа | Когда работает | Для чего |
|---|---|---|
| `onStart` | один раз, когда создаётся Store; после поворота не повторяется | разовая работа при открытии экрана: открыть чат на первом непрочитанном сообщении, загрузить пользователей для экрана входа |
| `whileSubscribed` | пока экран собирает State; отменяется через 5 с после того, как экран перестал его собирать | наблюдение за данными, чтобы в фоне ничего не наблюдалось |
| `execute` | на каждый Intent, в своей корутине | действия пользователя |

5 секунд — то же правило, что у `stateIn(WhileSubscribed(5_000))`: после поворота экран подписывается снова намного раньше, и наблюдение не перезапускается. Вот как список чатов наблюдает за своим окном и сессией:

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

Executor не должен бросать исключения: необработанное исключение в `viewModelScope` роняет приложение. Ошибки сети приходят только как `IOException` (см. [Данные](data.md#сеть)), и Executor превращает такую ошибку в Effect, например в снэкбар.

&nbsp;

## ViewModel

ViewModel только держит Store. Executor она создаёт сама — из своих зависимостей и аргументов экрана:

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

Executor не регистрируется в Koin: Koin привязал бы Executor'ы обоих экранов к одному и тому же ключу `Executor`.

`StoreFactory` приходит из `:app`. В debug-сборке каждый Store пишет свои Intent, Mutation и Effect в Logcat:

```sh
adb logcat -s Store
```

```text
D Store   : Chat: intent JumpSomewhereClicked
D Store   : Chat: effect ScrollToSeq(seq=1193)
D Store   : Chat: mutation PageChanged(page=Page(messages=[...], hasOlder=true, hasNewer=true)) -> state ChatState(...)
```

&nbsp;

## State

Типы, которые нужны только внутри State, вложены в него, а загрузка — это отдельное состояние, а не флаг или `null`:

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

Reducer — обычный `when` по Mutation. В нём нет корутин, поэтому тест вызывает его напрямую:

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

## Текстовые поля

Текстовое поле — единственное исключение из правила «State создаёт только Reducer». `TextFieldState` поля создаётся во ViewModel и кладётся в State, а поле меняет его напрямую, пока пользователь печатает, — так советует Compose:

```kotlin
// The typed text survives process death.
initialState = ChatState(input = savedStateHandle.savedTextField(INPUT_KEY)),
```

- **Нет Intent на каждую букву.** Ввод никогда не ждёт Store и не теряет символы.
- **Значения, которые зависят от текста, — это геттеры** State: `val canSend get() = input.text.isNotBlank()`.
- **Executor читает текст в момент отправки** и первым делом очищает поле, поэтому второе нажатие застаёт его пустым и не отправляет сообщение дважды.

&nbsp;

## Effect

Экран обрабатывает Effect через [`ObserveEffects`](../../../core/compose-utils/src/main/kotlin/krio/systemdesign/chatapp/core/composeutils/effects/ObserveEffects.kt):

```kotlin
ObserveEffects(viewModel.effects) { effect ->
    when (effect) {
        is ChatListEffect.OpenChat -> navigate { onOpenChat(effect.chatId) }
        ChatListEffect.ShowError -> showSnackbar(snackbarHostState, errorMessage)
        ChatListEffect.ShowBotsError -> showSnackbar(snackbarHostState, botsErrorMessage)
    }
}
```

- **Effect'ы собираются, пока экран виден** (`repeatOnLifecycle(STARTED)`), на `Main.immediate`: экран обрабатывает Effect прямо внутри `send` у Store. Если бы между ними была задержка, экран, который как раз останавливается, мог бы забрать Effect из канала и потерять его.
- **`navigate { }` работает, только пока экран наверху**, поэтому после быстрого двойного нажатия второй переход отбрасывается.
- **`showSnackbar` работает в своей корутине**: показ снэкбара приостанавливается до его закрытия и иначе задержал бы следующий Effect.

&nbsp;

## Две перегрузки экрана

У экрана две функции с одним именем. Первая получает ViewModel и собирает State и Effect, вторая только рисует:

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

Вторая не зависит от Koin, поэтому её можно показать в превью с любым State. `onIntent` не выходит за пределы файла экрана: части экрана в `components/` (`MessageBubble`, `MessageInput`, `BotsBar`) получают конкретные колбэки, поэтому ничего не знают об Intent экрана, и у каждой есть свои превью.

&nbsp;

## Списки, которые растут с края

`LazyColumn` удерживает на месте первый видимый элемент по его ключу. Когда чат поднимается наверх списка или внизу чата появляется новое сообщение, новый элемент оказывается сразу за краем экрана, и пользователь, который смотрит на этот край, его бы не увидел. Поэтому, когда пользователь у края, экран просит список у этого края и остаться:

```kotlin
// FollowBottom in ChatScreen.kt; FollowTop in ChatListScreen.kt is the same at the top
SideEffect {
    val isAtBottom = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    if (bottomKey != shownBottomKey[0] && isAtBottom) listState.requestScrollToItem(0)
    shownBottomKey[0] = bottomKey
}
```

`SideEffect` срабатывает до того, как список измерит изменение, — пока список ещё там, где его оставил пользователь. Если пользователь читает дальше от края, его место сохраняется. В чате это касается только самых новых сообщений: страница, загруженная под окном в глубине истории, остаётся ниже, и пользователь долистывает до неё сам (см. [Пагинация](pagination.md#новые-сообщения-внизу)).

&nbsp;

## Компоненты и превью

Экран собирается из готовых компонентов: каждый стилизованный элемент берётся из `:core:designsystem` или `:crossfeature:ui` (см. [Модули](modules.md#куда-класть-новый-код)). Верхняя панель каждого экрана — `AppTopBar`, нижние панели — `AppBottomBar`, так что все экраны оформлены одинаково.

Превью лежат рядом с кодом, который показывают, они приватные, и у каждого есть вариант в светлой и тёмной теме. Каждое превью — ещё и [скриншот-тест](testing.md#скриншоты).
