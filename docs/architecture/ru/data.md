# Данные

[English version](../en/data.md) · [Все страницы](README.md)

Executor вызывает use case, use case вызывает репозиторий, а репозиторий работает с Room и сервером. Главное правило простое: **сеть пишет, экраны читают.** Всё, что приходит с сервера, сначала попадает в Room, а экраны показывают только то, что есть в Room.

&nbsp;

## Слои

Общие модели и их хранение лежат в `crossfeature/`; то, что нужно одной фиче, остаётся в ней:

| Где | Что |
|---|---|
| `:crossfeature:domain` | модели (`Message`, `Conversation`, `Window`, `Session`…) и интерфейсы репозиториев; чистый Kotlin |
| `:crossfeature:data` | их реализации: кэш в Room, API сервера, синхронизация, сокет событий, outbox; всё `internal`, кроме нескольких классов, которые запускает `:app` |
| `feature/<name>/impl/…/domain/usecase` | use case фичи: `SendMessageUseCase`, `MarkConversationReadUseCase`… |

Use case делает одно действие через репозиторий:

```kotlin
// Into the outbox: the message shows at once and is sent when the network is there.
@Factory
internal class SendMessageUseCase(private val repository: OutboxRepository) {
    suspend operator fun invoke(conversationId: String, text: String) = repository.enqueue(conversationId, text)
}
```

У каждого слоя свои модели: `MessageDto` из `contract/` превращается в `MessageEntity` для Room (`remote/Mappers.kt`), а сущность — в `Message` для экранов (рядом с сущностью, в `local/`).

&nbsp;

## Экраны читают только Room

Страницы, синхронизация, сокет и пуши пишут в Room, а экран наблюдает окно над ней:

```text
сервер ──страница / синхронизация / сокет / пуш──▶ Room ──наблюдение окна──▶ Executor ──▶ State
```

- **Без сети кэш открывается сразу.** Чат, который уже открывали, показывает свои сообщения без сети, а новые придут, когда она появится.
- **Каждый источник пишет одинаково**, поэтому сообщение одно и то же, что бы его ни принесло: страница, синхронизация или сокет.
- **Экран никогда не склеивает два источника** (список из сети плюс список из кэша): он читает один запрос.

&nbsp;

## Старые данные не затирают новые

Страница может уйти с сервера до правки и прийти после того, как сокет эту правку уже принёс. Поэтому в каждом DTO есть `version`, который растёт с каждым изменением на сервере, и запись оставляет более новую версию:

```kotlin
// MessageDao: writes the messages without overwriting newer data with older; repeats are harmless too.
open suspend fun upsertNewer(items: List<MessageEntity>) { /* ... */ }

// The same for messages already cached only: edits of messages not loaded aren't needed.
open suspend fun updateExistingNewer(items: List<MessageEntity>) { /* ... */ }
```

Одно и то же сообщение может прийти несколько раз и не по порядку — из сокета, синхронизации и пуша, — а результат будет один.

&nbsp;

## Окно чата

Сообщения чата пронумерованы без пропусков: `seq` 1, 2, 3… в каждом чате. А в кэше пропуски бывают: у чата, открытого на первом непрочитанном, есть кусок истории в середине. Экран наблюдает окно из двух seq:

| Окно | Показывает |
|---|---|
| `fromSeq = 2951, toSeq = 3000` | эти сообщения |
| `fromSeq = 2951, toSeq = null` | **голову**: от 2951 до конца сплошного участка, поэтому новые сообщения добавляются в неё сами |

Запрос никогда не перешагивает через дыру: при `toSeq = null` он останавливается там, где следующего seq нет в кэше.

```kotlin
@Query(
    """SELECT * FROM messages
       WHERE conversationId = :conversationId AND seq >= :fromSeq
       AND seq <= COALESCE(:toSeq,
           (SELECT MIN(m.seq) FROM messages m
            WHERE m.conversationId = :conversationId AND m.seq >= :fromSeq
            AND NOT EXISTS (SELECT 1 FROM messages n WHERE n.conversationId = :conversationId AND n.seq = m.seq + 1)))
       ORDER BY seq DESC""",
)
abstract fun observeWindow(conversationId: String, fromSeq: Long, toSeq: Long?): Flow<List<MessageEntity>>
```

Окно двигает `ChatPaginator` в Executor чата:

- **Прокрутка вверх** загружает страницу выше (`beforeSeq`), и окно перестаёт быть головой: его низ фиксируется на самом новом показанном сообщении, чтобы новые сообщения не сдвигали то, что читает пользователь.
- **Прокрутка вниз** загружает страницу ниже (`afterSeq`); в конце окно снова становится головой.
- **Room перечитывает всё окно при каждом изменении**, поэтому в нём не больше 300 сообщений. Лишнее отрезается там, куда пользователь не смотрит: снизу при прокрутке вверх, сверху при прокрутке вниз. Отрезанное остаётся в Room и возвращается, когда пользователь до него долистает.
- **Открытие на сообщении** (`aroundSeq`: первое непрочитанное или прыжок по кнопке в тулбаре) загружает страницу вокруг него, с дырами сверху и снизу, которые догружаются при прокрутке.

По шагам, с гонками между загрузками, — в [Пагинации](pagination.md#чат).

&nbsp;

## Окно списка

Список чатов упорядочен по `sortKey`, который растёт с каждым новым сообщением в чате. Экран наблюдает окно из двух чисел, `offset` и `count`, над **границей загруженного** — самым маленьким `sortKey`, загруженным сверху без пропусков:

```kotlin
@Query(
    """SELECT * FROM conversations
       WHERE sortKey >= (SELECT loadedDownToSortKey FROM conversation_list_meta WHERE id = 0)
       ORDER BY sortKey DESC LIMIT :limit OFFSET :offset""",
)
abstract fun observeLoaded(offset: Int, limit: Int): Flow<List<ConversationEntity>>
```

| Пользователь | Окно | Чаты |
|---|---|---|
| открывает экран | `(0, 50)` | 1–50 |
| листает вниз | `(0, 100)`…`(0, 150)` | растёт страницами по 50 |
| листает дальше | `(50, 150)` | 51–200: верх выходит из подписки |
| листает обратно вверх | `(0, 150)` | всё выше уже в кэше, сеть не нужна |

- **Пересортировка — в самом запросе**: новое сообщение поднимает `sortKey` чата, и Room перечитывает окно.
- **Чат глубоко внизу не сохраняется**, когда приходит только его изменение (из синхронизации или сокета): страница принесёт его свежим, когда пользователь туда долистает. Чат, который поднялся наверх, проходит границу и появляется в списке.

Как окно списка растёт и сдвигается — в [Пагинации](pagination.md#список-чатов). Paging 3 не используется: чату нужны дыры, голова, которая растёт сама, и открытие на нужном seq, а `RemoteMediator` рассчитан на ленту без дыр.

&nbsp;

## Room 3

Кэш — это [Room 3](https://developer.android.com/jetpack/androidx/releases/room3) (`androidx.room3`, с KSP):

| Таблица | Что хранит |
|---|---|
| `messages` | закэшированные сообщения по чату и `seq` |
| `conversations`, `conversation_list_meta` | закэшированные чаты, границу загруженного и закладку синхронизации списка |
| `chat_sync_state` | закладку синхронизации каждого чата и когда его открывали последний раз |
| `outgoing_messages` | outbox: сообщения, которых ещё нет на сервере |
| `session` | кто вошёл |

- **База — это кэш**, поэтому при смене схемы она удаляется, а не мигрирует (`fallbackToDestructiveMigration(dropAllTables = true)`), и всё загружается заново; вход тоже сбрасывается. Схема каждой версии лежит в git, в [`crossfeature/data/schemas`](../../../crossfeature/data/schemas/).
- **Room работает на `AndroidSQLiteDriver`** — его умеет запускать Robolectric в тестах DAO; для Kotlin Multiplatform драйвер меняется одной строкой.
- **Запись и закладка синхронизации идут в одной транзакции** (`withWriteTransaction`): см. [Синхронизация](sync.md#синхронизация-по-закладке).

&nbsp;

## Сеть

На всё приложение один `HttpClient`, в `:core:network`, на движке OkHttp из Ktor. Каждый API-класс ходит на сервер через `callApi`, который превращает всё, что может пойти не так, в один `IOException`:

```kotlin
suspend inline fun <T> callApi(block: suspend () -> T): T = try {
    block()
} catch (e: ResponseException) {
    throw IOException("The server answered ${e.response.status}", e)
} catch (e: SerializationException) {
    throw IOException("The server's answer can't be read", e)
} // and Ktor's other conversion errors
```

- **Для экрана `IOException` значит «нет связи»**: Executor ловит только `java.io.IOException` и никогда не видит исключений Ktor.
- **Каждый запрос подписан** двумя заголовками, `X-Api-Key` и `X-User-Id`. `AuthHeaders` выдаёт их для каждого запроса, поэтому вход или выход действует со следующего. `SessionAuthHeaders` в `:crossfeature:data` берёт пользователя из сессии.
- **Ключа нет в git**: он берётся из `chatapp.apiKey` в `local.properties`, попадает в `BuildConfig`, а оттуда в Koin. Без него приложение собирается, как на CI, а сервер отвечает 401.
- **Таймаута на весь запрос нет**: он оборвал бы и сокет событий, который открыт всё время, пока приложение на экране. Подключение и чтение ограничены 30 с, а мёртвое соединение сокет находит по пингу каждые 15 с.

<details>
<summary>Почему сессия в памяти — отдельный класс</summary>

HTTP-клиенту нужны заголовки, значит, нужна сессия. Выходу из аккаунта нужно сначала остановить фоновую работу: сокет и outbox. Сокету нужен HTTP-клиент. Если сессия и выход в одном классе, у Koin получается цикл: `SessionRepository` → `SessionWork` → `RealtimeConnection` → `HttpClient` → `AuthHeaders` → `SessionRepository`. Поэтому сессия в памяти, `SessionStore`, отделена от `SessionRepositoryImpl`, а заголовки читают только `SessionStore`.

</details>

&nbsp;

## Контракт с сервером

DTO лежат в [`contract/`](../../../contract/) — сборке, которую подключают обе стороны, поэтому они не могут разойтись в имени или типе поля. В чём они ещё могут разойтись — точный JSON, адреса, — проверяют образцы: у каждого запроса и ответа, на которые опирается приложение, есть файл в [`server/api-samples/`](../../../server/api-samples/). Тесты сервера проверяют, что он отвечает именно так; тесты приложения читают те же файлы. Подробнее в [Тестах](testing.md#контракт-с-сервером).
