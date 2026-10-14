# Data

[Русская версия](../ru/data.md) · [All pages](README.md)

An Executor calls use cases, a use case calls a repository, and a repository works with Room and the server. The main rule is simple: **the network writes, the screens read.** Everything that comes from the server goes into Room first, and screens show only what Room has.

&nbsp;

## Layers

Shared models and their storage live in `crossfeature/`; what only one feature needs stays in that feature:

| Where | What |
|---|---|
| `:crossfeature:domain` | models (`Message`, `Conversation`, `Window`, `Session`…) and repository interfaces; pure Kotlin |
| `:crossfeature:data` | their implementations: the Room cache, the server's API, the syncs, the events socket, the outbox; everything is `internal`, except the few classes `:app` runs |
| `feature/<name>/impl/…/domain/usecase` | the feature's use cases: `SendMessageUseCase`, `MarkConversationReadUseCase`… |

A use case does one thing through a repository:

```kotlin
// Into the outbox: the message shows at once and is sent when the network is there.
@Factory
internal class SendMessageUseCase(private val repository: OutboxRepository) {
    suspend operator fun invoke(conversationId: String, text: String) = repository.enqueue(conversationId, text)
}
```

Each layer has its own models: a `MessageDto` from `contract/` becomes a `MessageEntity` for Room (`remote/Mappers.kt`), and the entity becomes a `Message` for the screens (next to the entity, in `local/`).

&nbsp;

## Room is the only source the screens read

Pages, syncs, the socket and pushes all write into Room, and a screen watches a window of it:

```text
server ──page / sync / socket / push──▶ Room ──observe a window──▶ Executor ──▶ State
```

- **Offline, the cache opens at once.** A chat opened before shows its messages without the network, and the newest ones come when it is back.
- **Every source writes the same way**, so a message is the same whether a page, a sync or the socket brought it.
- **A screen never merges two sources** (a list from the network plus a list from the cache): it reads one query.

&nbsp;

## Older data never overwrites newer

A page may leave the server before an edit and arrive after the socket brought that edit. So every DTO carries a `version` that grows with each change on the server, and writes keep the newer one:

```kotlin
// MessageDao: writes the messages without overwriting newer data with older; repeats are harmless too.
open suspend fun upsertNewer(items: List<MessageEntity>) { /* ... */ }

// The same for messages already cached only: edits of messages not loaded aren't needed.
open suspend fun updateExistingNewer(items: List<MessageEntity>) { /* ... */ }
```

The same message may come several times and out of order — from the socket, a sync and a push — and the result is the same.

&nbsp;

## The chat's window

A chat's messages have numbers without gaps: `seq` 1, 2, 3… in each chat. The cache, though, may have gaps: a chat opened at its first unread message has a piece of history in the middle. The screen watches a window of two seqs:

| Window | Shows |
|---|---|
| `fromSeq = 2951, toSeq = 3000` | those messages |
| `fromSeq = 2951, toSeq = null` | the **head**: from 2951 to the end of the gapless run, so new messages join it by themselves |

The query never steps over a gap: with `toSeq = null`, it stops where the next seq isn't cached.

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

`ChatPaginator` in the chat's Executor moves the window:

- **Scrolling up** loads the page above (`beforeSeq`), and the window stops being the head: its bottom is fixed at the newest message shown, so new messages don't move what the user reads.
- **Scrolling down** loads the page below (`afterSeq`); at the end the window becomes the head again.
- **Room re-reads the whole window on every change**, so it is kept to 300 messages. The excess is cut where the user isn't looking: at the bottom when scrolling up, at the top when scrolling down. What is cut stays in Room and comes back when the user scrolls there.
- **Opening at a message** (`aroundSeq`: the first unread, or a jump from the toolbar button) loads a page around it, with gaps above and below that load as the user scrolls.

Step by step, with the races between loads, in [Pagination](pagination.md#the-chat).

&nbsp;

## The list's window

The chat list is ordered by `sortKey`, which grows with every new message in a chat. The screen watches a window of two numbers, `offset` and `count`, above the **loaded boundary** — the lowest `sortKey` loaded without gaps from the top:

```kotlin
@Query(
    """SELECT * FROM conversations
       WHERE sortKey >= (SELECT loadedDownToSortKey FROM conversation_list_meta WHERE id = 0)
       ORDER BY sortKey DESC LIMIT :limit OFFSET :offset""",
)
abstract fun observeLoaded(offset: Int, limit: Int): Flow<List<ConversationEntity>>
```

| The user | Window | Chats |
|---|---|---|
| opens the screen | `(0, 50)` | 1–50 |
| scrolls down | `(0, 100)`…`(0, 150)` | grows by pages of 50 |
| scrolls further | `(50, 150)` | 51–200: the top leaves the subscription |
| scrolls back up | `(0, 150)` | everything above is cached, so no network |

- **Re-sorting happens in the query**: a new message raises a chat's `sortKey`, and Room re-reads the window.
- **A chat deep down isn't kept** when only its change comes (from a sync or the socket): the page brings it fresh when the user scrolls there. A chat that rose to the top passes the boundary and shows.

How the list's window grows and moves is in [Pagination](pagination.md#the-chat-list). Paging 3 isn't used: the chat needs gaps, a head that grows by itself and opening at a seq, and `RemoteMediator` is built for a feed without gaps.

&nbsp;

## Room 3

The cache is [Room 3](https://developer.android.com/jetpack/androidx/releases/room3) (`androidx.room3`, with KSP):

| Table | Holds |
|---|---|
| `messages` | the cached messages, by chat and `seq` |
| `conversations`, `conversation_list_meta` | the cached chats, the loaded boundary and the list's sync token |
| `chat_sync_state` | each chat's sync token and when it was last opened |
| `outgoing_messages` | the outbox: messages not on the server yet |
| `session` | who is signed in |

- **The database is a cache**, so a schema change drops it instead of migrating (`fallbackToDestructiveMigration(dropAllTables = true)`), and everything loads again; the sign-in goes too. The schema of each version is committed in [`crossfeature/data/schemas`](../../../crossfeature/data/schemas/).
- **Room runs on `AndroidSQLiteDriver`**, the one Robolectric can run in the DAO tests; for Kotlin Multiplatform the driver changes in one line.
- **A write and its sync token go in one transaction** (`withWriteTransaction`): see [Sync](sync.md#sync-by-token).

&nbsp;

## Network

There is one `HttpClient` for the whole app, in `:core:network`, on Ktor's OkHttp engine. Every API class calls the server through `callApi`, which turns everything that can go wrong into one `IOException`:

```kotlin
suspend inline fun <T> callApi(block: suspend () -> T): T = try {
    block()
} catch (e: ResponseException) {
    throw IOException("The server answered ${e.response.status}", e)
} catch (e: SerializationException) {
    throw IOException("The server's answer can't be read", e)
} // and Ktor's other conversion errors
```

- **To a screen, `IOException` means "no connection"**: the Executors catch `java.io.IOException` only and never see Ktor's exceptions.
- **Every request is signed** with two headers, `X-Api-Key` and `X-User-Id`. `AuthHeaders` gives them for each request, so a sign-in or a sign-out applies to the next one. `SessionAuthHeaders` in `:crossfeature:data` reads the user from the session.
- **The key isn't in git**: it comes from `chatapp.apiKey` in `local.properties` into `BuildConfig`, and from there into Koin. Without it the app builds, as on CI, and the server answers 401.
- **No request timeout**: it would also end the events socket, which stays open for as long as the app is on screen. Connecting and reading time out after 30 s, and the socket finds a dead connection by its ping every 15 s.

<details>
<summary>Why the session in memory is a class of its own</summary>

The HTTP client needs the headers, so it needs the session. Signing out needs the background work stopped first: the socket and the outbox. The socket needs the HTTP client. With the session and the sign-out in one class, Koin would get a cycle: `SessionRepository` → `SessionWork` → `RealtimeConnection` → `HttpClient` → `AuthHeaders` → `SessionRepository`. So the session held in memory, `SessionStore`, is apart from `SessionRepositoryImpl`, and the headers read only `SessionStore`.

</details>

&nbsp;

## The contract with the server

The DTOs live in [`contract/`](../../../contract/), a build both sides include, so they can't disagree about a field's name or type. What they could still disagree about — the exact JSON, the addresses — is checked by samples: every request and response the app relies on has a file in [`server/api-samples/`](../../../server/api-samples/). The server's tests check that it answers exactly so; the app's tests read the same files. More in [Testing](testing.md#contract-with-the-server).
