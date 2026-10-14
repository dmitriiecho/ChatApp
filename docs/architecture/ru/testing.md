# Тесты

[English version](../en/testing.md) · [Все страницы](README.md)

Тесты работают на JVM, без эмулятора. Они покрывают ядро MVI, логику экранов, слой данных с синхронизацией, сами экраны (скриншот каждого превью), контракт с сервером и сервер.

&nbsp;

## Запуск тестов

Каждая сборка запускает свои тесты, а CI запускает оба набора на каждый pull request:

```sh
./gradlew test                # приложение: каждый модуль, по одному разу, включая скриншоты
cd server && ./gradlew test   # сервер
./gradlew recordRoborazziDebug -Proborazzi.cleanupOldScreenshots=true  # записывает новые скриншоты после изменения UI
```

Android-модули запускают unit-тесты только в debug-варианте (`chatapp.android.library` выключает release, который бы их просто повторил), поэтому один `test` покрывает и Android-модули, и модули на чистом Kotlin.

&nbsp;

## Как пишется тест

Вот короткий тест из проекта:

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

- **Одно поведение на тест**, и **имя его называет**, в обратных кавычках: `` `offline the bots switch goes back and a snackbar says why` ``, а не `` `bots error` ``.
- **Подготовка, действие, проверка**, разделённые пустыми строками.
- **Данные теста лежат в тесте.** Конструкторы со значениями по умолчанию (`conversation("c", unreadCount = 2)`, `messageDto("c", 3)`) позволяют задать только то, что важно этому тесту.
- **Рабочие заменители вместо моков.** Репозитории — интерфейсы, поэтому тест берёт реализацию в памяти с настоящим поведением; библиотеки для моков нет.
- **Никакого реального времени**: тесты корутин работают на `runTest` с виртуальными часами.
- **Каждый новый тест хотя бы раз видели упавшим**: сломать код, который он проверяет, увидеть, что тест падает, и вернуть код.

&nbsp;

## Имена и места

Всё, что сделано для тестов, начинается с `Test` или `test`:

| Что | Имя | Пример |
|---|---|---|
| Тестовый класс | класс, который он проверяет, + `Test`, в том же пакете | `ChatPaginator` → `ChatPaginatorTest` |
| Заменитель | `Test` + то, что он заменяет | `TestMessagesRepository`, `TestServer` |
| Входная функция вида тестов | вид + `Test` | `viewModelTest {}`, `dataTest {}`, `networkTest {}`, `serverTest {}` |
| Действие, которое выполняет тест | глагол | `typeText()`, `keepCollecting()` |

Помощник, которым пользуются тесты **двух и более модулей**, лежит в **test fixtures** модуля, которому принадлежит то, что он заменяет; помощник одного модуля остаётся в `src/test` этого модуля:

| Помощник | Модуль | Source set |
|---|---|---|
| `viewModelTest {}`, `keepCollecting()`, `typeText()`, `PausedClockPreviewTester` | `:core:compose-utils` | `src/testFixtures` |
| `networkTest {}`, `TestServer`, `apiSample()`, `apiRequest()` | `:core:network` | `src/testFixtures` |
| `dataTest {}`, `messageDto()`, `conversationDto()` | `:crossfeature:data` | `src/test` |
| `TestMessagesRepository`, `TestConversationsRepository`, `TestBotsRepository`… | `:feature:chats:impl` | `src/test` |
| `serverTest {}`, `TestData`, `apiSample()`, `sendApiRequest()` | `server/` | `src/test` |

&nbsp;

## Инструменты

| Инструмент | Для чего |
|---|---|
| `kotlin.test` | `@Test` и остальные аннотации; под ними JUnit4 в приложении (Robolectric работает только с ним) и JUnit5 на сервере |
| [AssertK](https://github.com/assertk-org/assertk) | все проверки: `assertThat(actual).isEqualTo(expected)`, `isInstanceOf<T>()`, `assertFailure {}` |
| `kotlinx-coroutines-test` | `runTest` и виртуальные часы |
| [Turbine](https://github.com/cashapp/turbine) | разовые Effect экрана: `effects.test { awaitItem() }` |
| Ktor `MockEngine` | отвечает на запросы сетевого кода без настоящего сервера (`TestServer`) |
| [Robolectric](https://robolectric.org) | Android-классы на JVM, только для кода, которому без них никак (Room, скриншоты) |
| [Roborazzi](https://github.com/takahirom/roborazzi) | скриншот-тесты, созданные из функций с `@Preview` |

&nbsp;

## Виды тестов

Каждый вид тестов начинается с входной функции, которая готовит то, что этому виду нужно.

### Store

`StoreTest` в `:core:mvi` проверяет сам цикл на маленьком счётчике: Mutation проходят через Reducer, Effect, отправленный, пока никто не собирает, ждёт экран, `onStart` выполняется один раз, а `whileSubscribed` начинается с первым подписчиком и останавливается через 5 с после последнего:

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

`viewModelTest {}` подменяет `Dispatchers.Main`, на котором работает `viewModelScope`, тестовым диспетчером на виртуальных часах `runTest`. ViewModel получает настоящие use case поверх `Test`-репозиториев, поэтому тест задаёт данные и проверяет, что получает экран:

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

- **State собирается всё время, как это делает экран**: `keepCollecting(viewModel.state)` сразу после создания ViewModel. Без подписчика `whileSubscribed` у Executor не запускается, и State остаётся начальным.
- **Ввод текста идёт через `typeText()`**: на устройстве Compose применяет изменение поля на следующем кадре, а у теста кадров нет.
- **Время виртуальное**: `advanceTimeBy()` проматывает секундную паузу отметок прочтения без ожидания.

### Reducer

Reducer — чистая функция, поэтому в его тесте вообще нет корутин: он прогоняет несколько Mutation и проверяет State (см. пример [выше](#как-пишется-тест)).

### Данные

`dataTest {}` собирает слой данных так, как его собирает приложение, на Room в памяти и `TestServer`, со входом от имени Anna. Room нужен Android-`Context`, поэтому такие тестовые классы работают на Robolectric:

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

- **Тесты DAO** проверяют окна на настоящем SQL: окно никогда не перешагивает дыру, голова растёт сама, старая версия не затирает новую.
- **Тесты синхронизации и outbox** ставят в очередь ответы сервера и проверяют, что оказалось в Room: порции, `resetRequired`, повтор после сбоя, дыра, догруженная перед отправленным сообщением.
- **Тест закрывает scope слоя данных раньше базы**: подписчик, который ещё читает закрытую базу, бросил бы исключение.

### Сеть

`networkTest {}` даёт тесту `TestServer` и клиент с настройками приложения ([`serverApi`](../../../core/network/src/main/kotlin/krio/systemdesign/chatapp/core/network/NetworkJson.kt)), поэтому JSON читается тем же `networkJson`. `TestServer` отвечает ответами из очереди и записывает запросы; он работает на `MockEngine` из Ktor:

```kotlin
@Test
fun `an error code is an IOException`() = networkTest { server, client ->
    server.enqueue(code = 500)

    assertFailure { client.item() }.isInstanceOf<IOException>()
}
```

### Контракт с сервером

DTO общие через `contract/`, поэтому имена полей разойтись не могут, а точный JSON и адреса — могут. Синхронными их держит [`server/api-samples/`](../../../server/api-samples/): по JSON-файлу на каждый запрос и ответ, на которые опирается приложение, и `requests.json` с методом и адресом каждого запроса (`"set-bots": "PUT /bots"`).

| Сторона | Проверяет |
|---|---|
| Сервер | принимает образцы запросов по их адресам и отвечает в точности как образцы ответов, включая события |
| Приложение | отправляет запросы в точности как образцы, по их адресам, и читает образцы ответов |

`apiSample("bots.json")` читает образец на любой стороне, своим `Json` этой стороны, а проверка сравнивает деревья JSON: порядок полей не важен, а переименованное, лишнее или пропавшее поле — важно. Изменение формата или адреса на одной стороне валит её тесты, пока не обновят образец, а потом и другую сторону.

Обе сборки объявляют папку образцов входом тех тестов, которые её читают (тестов сервера и тестов приложения в `:core:network` и `:crossfeature:data`), поэтому правка образца запускает их снова, а не оставляет актуальными.

### Скриншоты

Каждое `@Preview` — скриншот-тест, так что писать их руками не нужно. `chatapp.android.screenshots` поручает Roborazzi отрисовать каждое превью на Robolectric и сравнить с картинкой, сохранённой в папке `screenshots/` модуля:

| Команда | Делает |
|---|---|
| `./gradlew test` | сравнивает каждое превью с сохранённой картинкой и падает, если они различаются |
| `./gradlew recordRoborazziDebug -Proborazzi.cleanupOldScreenshots=true` | рисует превью и сохраняет их как новые картинки; удаляет картинки превью, которых больше нет |

Эти тесты не говорят, хорошо ли выглядит экран; они ловят изменения, о которых никто не просил. Поэтому картинки записывает только человек, осознанно, после изменения UI:

1. Изменить UI.
2. Запустить `./gradlew test`. Упасть должны только те превью, которые вы собирались изменить, и `build/outputs/roborazzi/…_compare.png` каждого показывает старую картинку, разницу красным и новую.
3. Записать картинки.
4. Закоммитить картинки вместе с кодом.

> [!IMPORTANT]
> Если вы не собирались менять внешний вид, не записывайте. Тогда упавший скриншот-тест поймал настоящую проблему: отступ, съехавший при рефакторинге, обновление библиотеки, которое поменяло кнопки, общий компонент, задевший другой экран. CI картинки никогда не записывает.

- **Часы остановлены**: `PausedClockPreviewTester` рисует каждое превью на первом кадре, поэтому бесконечная анимация (индикатор загрузки) не подвешивает тест.
- **Время в UTC** во всех скриншот-тестах, поэтому картинка, записанная на одной машине, совпадает на CI.
- **Старые картинки удаляются только при записи**, через этот флаг: Roborazzi удаляет все картинки, которые запуск не нарисовал, поэтому с флагом, включённым всегда, запуск одного теста удалил бы все остальные картинки модуля.
- **Приватные превью тоже считаются**: правила lint от Slack требуют, чтобы превью были приватными, и плагин их включает.

### Сервер

`serverTest {}` запускает сервер в памяти через `testApplication` из Ktor, на SQLite в памяти с данными самого теста (`TestData`: пользователи и чаты кладутся прямо в таблицы), с остановленными часами и клиентом, который подписывает каждый запрос от имени Anna тестовым ключом. Запрос из образцов отправляется через `sendApiRequest("set-bots")`, который падает на любом статусе, кроме успешного. У ботов свои тесты, с фиксированным зерном случайности и без пауз. Подробнее об API сервера — в [`server/README.ru.md`](../../../server/README.ru.md).
