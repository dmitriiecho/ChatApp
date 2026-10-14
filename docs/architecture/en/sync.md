# Sync

[Русская версия](../ru/sync.md) · [All pages](README.md)

The cache in Room has to follow the server: new messages, edits and deletions, chats rising in the list, reads on another device, and messages the user wrote while offline. Five mechanisms do it, each for its own case, and all of them only write into Room (see [Data](data.md)).

&nbsp;

## Who brings what

Each mechanism covers a gap the others leave:

| Mechanism | Runs | Brings |
|---|---|---|
| [The events socket](#the-events-socket) | while the app is on screen | every change, as it happens |
| [Sync by token](#sync-by-token) | on every connection of the socket, and when a chat opens | what was missed while the socket was closed |
| [Pushes](#pushes) | an entry point for a push service; no FCM yet | events while the app is in the background |
| [The outbox](#the-outbox) | whenever there is something to send and a network | the user's messages, in order, also after the app is closed |
| [Read marks](#read-marks) | while the user scrolls a chat | how far the user has read, to every device |

&nbsp;

## The events socket

`RealtimeConnection` holds a WebSocket to `ws://…/events`, and the server sends the user's events over it, one JSON object per frame:

| Event | Means | The app |
|---|---|---|
| `message.new` | a new message in a chat | caches it only as a continuation of the cache (`seq == maxSeq + 1`) |
| `message.changed` | an edit or a deletion | updates the message if it is cached |
| `conversation.updated` | a new message, a read, a rename | updates the chat; a chat that rose to the top shows in the list |
| `conversation.removed` | the user left the chat; the server doesn't send it yet, as leaving a chat isn't in the UI | drops the chat and its messages |
| `bots.changed` | someone turned the server's bots on or off | moves the switch under the chat list |

- **A lone new message isn't cached.** Without its previous message it would be a piece of history with a gap below it, and offline the chat would open on it. What is missing loads when the chat opens.
- **An unknown event type is skipped**, so a new type on the server doesn't break older apps.
- **Events sent while offline are lost**: the sync by token catches up on them after reconnecting.

The socket is open only while someone is signed in and the app is on screen. `RealtimeRunner` in `:app` runs it with `ProcessLifecycleOwner`:

```kotlin
sessions.map { it?.userId }.distinctUntilChanged().collectLatest { userId ->
    if (userId == null) return@collectLatest
    process.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        connection.connectAndListen()
    }
}
```

- **In the background the socket closes**: Android would cut it anyway, and pushes are for that time.
- **`ProcessLifecycleOwner` reports the background about 700 ms late**, so a rotation doesn't reconnect.
- **After a drop, the socket reconnects** with a growing pause (1 s, 2 s, 4 s… up to 30 s), so a server that's down isn't hammered, or as soon as the network comes back (`NetworkMonitor` on `ConnectivityManager`).

&nbsp;

## Sync by token

The server keeps a log of changes: one for each user's chat list, and one for each chat's edits and deletions. A **token** is a position in a log. The app keeps the token it has caught up to and asks for what came after it:

| Situation | The app asks | Gets |
|---|---|---|
| first start | `changes` without a token | no changes and the current token, then the first page |
| it has a token | `changes?since=<token>` | what changed after it and the next token, in batches until `hasMore` is false |
| the token is too old | the same | `resetRequired`: the log no longer reaches back so far, so the list starts over from the first page, and a chat's cache is dropped |

The changes and the new token are written in one transaction:

```kotlin
db.withWriteTransaction {
    conversationDao.upsertIfRelevant(changes.changed.map { it.toEntity() })
    db.removeConversations(changes.removedIds)
    metaDao.upsert(metaDao.get()!!.copy(syncToken = changes.nextToken))
}
```

If anything fails in between, neither is written, and the next sync asks again from the old token. The token never moves past changes that weren't applied.

- **The chat list** (`ConversationsSync`) syncs on every connection of the socket, the first one included. It is the app's job, not the screen's, so chats sync even when the app opens straight in a chat.
- **A chat's edits** (`MessageChangesSync`) sync when the chat opens, and after each connection for the ten chats opened last, so they open offline later with their current text. New messages aren't in this log: the chat's window loads them.
- **The order matters when starting over**: first the token, then the page. A chat changed between the two requests then comes in the page and again from the log, which is harmless (`version`). The other way round, a change between the page and the token would be in neither.

&nbsp;

## Pushes

There is no FCM in the project, but there is a place for it. `RealtimeHandler.onPush(data)` takes a push's data, applies its events like the socket's, and returns those that didn't come late, for a push service to decide which notification to show:

```kotlin
suspend fun onPush(data: Map<String, String>): List<RealtimeEventDto> {
    val raw = data[PushData.EVENTS] ?: return emptyList()
    val events = networkJson.parseToJsonElement(raw).jsonArray.mapNotNull { decodeEventOrNull(it) }
    events.forEach { onEvent(it) }
    return events.filterNot { isStale(it) }
}
```

A push delayed while the phone slept may be about a chat read since on another device: `version` keeps Room safe from it, and `isStale` keeps the notification from showing. The format of a push is in [`server/api-samples/push-data.json`](../../../server/api-samples/push-data.json).

&nbsp;

## The outbox

Sending a message doesn't wait for the network. The message is written to Room first and shows in the chat at once, with "Sending":

```text
Send ──▶ outgoing_messages (Room) ──▶ WorkManager ──▶ OutboxSender.drain() ──▶ POST …/messages ──▶ messages (Room)
```

- **One unique work** in WorkManager (`OutboxWorker`) sends the queue. It runs only with a network, after a failure again with a growing pause, and also after the app is closed.
- **Oldest first, one at a time**, so messages arrive in the order they were written.
- **A resend is harmless**: each message carries a `clientId`, and the server returns the same message for a repeat instead of posting it twice.
- **On a network failure** the waiting messages show as "Not sent. Tap to retry"; a tap puts the whole queue back, since nothing after the failed message went either.
- **A sent message stays on screen**: it goes into the cache as a continuation of the chat. If someone else's messages came while the user was offline and aren't cached, `OutboxSender` first loads the gap, up to three pages.

`APPEND_OR_REPLACE` instead of `KEEP`: with `KEEP`, a message queued while the running work is finishing would wait for the next reason to send.

&nbsp;

## Read marks

A chat is read up to the newest message the user has seen on screen. The screen reports it as an Intent, `MessagesSeen(seq)`, and the Executor sends at most one mark a second while the user scrolls:

- **The server answers with the chat**, now with `unreadCount = 0`, and sends `conversation.updated` to the user's other devices, so their counters drop too.
- **Offline, a mark fails quietly**: the next message seen tries again.
- **The "New messages" divider** is set when the chat opens, above its first unread message, and stays while the chat is open, even after the mark goes.

&nbsp;

## The bots switch

The switch under the chat list is the server's state at this moment, not content to show offline, so it is the one thing kept in memory instead of Room (`BotsRepositoryImpl`):

- **Unknown until the socket's first connection**, which reads `GET /bots`; the switch can't be used until then.
- **Then it follows `bots.changed`**, so a change made on another device moves it by itself.
- **Turning it shows the choice at once**, while the request is on its way; without a network it goes back and a snackbar says why.
