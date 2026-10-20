# Pagination

[Русская версия](../ru/pagination.md) · [All pages](README.md)

Both screens show long lists: a user has more than 200 chats, and a chat may have thousands of messages. Neither screen ever holds all of them. Each one watches a **window** into the cache in Room, and a **paginator** in its Executor moves the window as the user scrolls. It goes to the network only when the cache doesn't have what the window moves onto.

&nbsp;

## The same idea on both screens

The two windows differ in shape, because the two lists differ in what they are ordered by:

| | Chat list | Chat |
|---|---|---|
| Ordered by | `sortKey`, which grows with every new message in a chat | `seq`: 1, 2, 3… in each chat, without gaps |
| The window | `offset` and `count` among the loaded chats | `fromSeq` and `toSeq` |
| A page | 50 chats | 50 messages |
| The most in the window | 150 chats | 300 messages |
| Opens | at the top | at the first unread message, or at the newest |
| New items appear | at the top, as chats rise | at the bottom, as messages come |
| Gaps in the cache | never: chats are loaded from the top down | yes: a chat opened at its first unread message has only a piece from the middle |
| Paginator | `ConversationsPaginator` | `ChatPaginator` |
| Repository | `ConversationsRepository` | `MessagesRepository` |

Why a window at all? Room re-runs a query on every change to the tables it reads. A chat with bots changes several times a minute, and re-reading thousands of rows each time would be wasted work. A window keeps every re-read to a few hundred rows at most, however far the user scrolls; what leaves the window stays in Room and comes back instantly.

&nbsp;

## How a screen asks for more

The screen doesn't load anything itself. It watches where the user is and sends an Intent when the user is near an edge and there is more there:

```kotlin
// ChatListScreen.kt
.collect { check ->
    if (check.isNearTop && check.hasAbove) currentOnIntent(ChatListIntent.ReachedTop)
    if (check.isNearBottom && check.hasMore) currentOnIntent(ChatListIntent.ReachedBottom)
}
```

- **"Near" is 10 items** from the edge (`EDGE_THRESHOLD`), so the next page comes before the user reaches the end.
- **The check runs again when the window or the flags change**, not only when the user scrolls. After a short page the user may still be at the edge, and without this the list would stop. The chat list watches how many chats it has; the chat watches the bounds of its window (the newest and the oldest message), because a 300-message window that moves by a page keeps its size, and a count would miss the move.
- **Items are counted, not rows.** The loading row at the end comes and goes with every try; if it were counted, the screen would keep retrying forever while offline.
- **A second Intent while a page is loading is ignored** by the Executor (`isLoadingMore`, `isLoadingOlder`, `isLoadingNewer`). The Store runs on the main thread, so that check holds without a lock (see [Screens](screens.md#the-store)).

The chat's list is drawn upside down (`reverseLayout = true`): its first item is the newest message, at the bottom next to the input field. So "the top" of a chat is the end of its list.

&nbsp;

## The chat list

### What is loaded

The cache keeps chats loaded from the top down without gaps, and a row in `conversation_list_meta` says how far down that goes:

| Field | Means |
|---|---|
| `loadedDownToSortKey` | the **loaded boundary**: the lowest `sortKey` loaded without gaps from the top |
| `hasMore` | the server has chats below the boundary |
| `syncToken` | the list's sync token (see [Sync](sync.md#sync-by-token)) |

The screen's query reads only chats above the boundary, so a chat that came from the socket but lies deep down doesn't show up in the middle of a list with a gap above it:

```kotlin
@Query(
    """SELECT * FROM conversations
       WHERE sortKey >= (SELECT loadedDownToSortKey FROM conversation_list_meta WHERE id = 0)
       ORDER BY sortKey DESC LIMIT :limit OFFSET :offset""",
)
abstract fun observeLoaded(offset: Int, limit: Int): Flow<List<ConversationEntity>>
```

The repository asks for one row more than the window holds: if it comes, the cache has more below, without a separate `COUNT` query.

### Opening

The list doesn't load its first page itself: the sync does, on the socket's first connection (see [Sync](sync.md)). Until then the screen shows a spinner, because an empty cache then means "not loaded yet", not "no chats". The window starts at `(0, 50)`.

### Scrolling down

`ConversationsPaginator.loadBelow()` grows the window by a page, up to 150 chats, and then moves it:

| The user | Window | Chats |
|---|---|---|
| opens the screen | `(0, 50)` | 1–50 |
| scrolls down | `(0, 100)`, then `(0, 150)` | 1–150 |
| scrolls further | `(50, 150)` | 51–200: the top 50 are no longer watched |
| scrolls further | `(100, 150)` | 101–250 |

Before the window moves, the chats it moves onto are loaded:

```kotlin
suspend fun loadBelow() {
    val current = window.value
    val next = if (current.count + PAGE_SIZE <= MAX_WINDOW_SIZE) {
        current.copy(count = current.count + PAGE_SIZE)
    } else {
        current.copy(offset = current.offset + PAGE_SIZE)
    }
    ensureConversationsLoaded(count = next.offset + next.count)
    window.compareAndSet(current, next)
}
```

- **`ensureLoaded(count)`** loads page after page below the boundary (`GET /conversations?beforeSortKey`) until the cache has that many chats or the server has nothing more. If the user scrolled this far before, the cache has them already and there is no request at all.
- **Offline it fails with `IOException`, and the window stays as it was**: there is nothing to undo. The screen shows a snackbar, and it tries again when the user scrolls again.
- **`compareAndSet`**: if the window changed while the page was loading, the new one isn't overwritten.

### Scrolling back up

Everything above the window is cached already, so `loadAbove()` only moves the window up by a page, without the network.

### When the list changes under the window

- **A chat moves to the top** with a new message: its `sortKey` grows, it passes the boundary, and Room re-reads the window. If the user is at the very top, the screen follows it (`FollowTop`, see [Screens](screens.md#lists-that-grow-at-an-edge)); further down the user keeps their place.
- **The list starts over** (`resetRequired`), or chats disappear while the window is far down: the window now points where there are no chats, so the paginator moves it back to the top.
- **A page from the old list** arrives after the sync has started the list over: the boundary has changed in the meantime, so the page is dropped instead of leaving a gap, and the next try loads the right one.
- **An empty page counts as the end** of the list, so loading more can't loop forever.

&nbsp;

## The chat

### The window and the head

A window is two seqs, and the query never steps over a gap in the cache:

| Window | Shows |
|---|---|
| `fromSeq = 701, toSeq = 1000` | those messages |
| `fromSeq = 951, toSeq = null` | the **head**: from 951 to the end of the gapless run, so new messages join it by themselves |

A window is the head only while the user is at the bottom of the chat. Once the user scrolls up, its bottom is fixed, so new messages don't move what the user reads.

### Opening

`ChatExecutor.onStart` opens the chat once, when the screen's Store is made:

1. **If there are unread messages**, it opens at the first unread one (`openAt(lastReadSeq + 1)`): a page around it is loaded if it isn't cached, and the window covers the gapless part of the cache around it, up to 25 messages each way. The "New messages" divider goes above it, and the screen scrolls to it.
2. **Otherwise**, it opens the newest cached messages, at once and from the cache only (`openLatest`), as the head.
3. **Then, in both cases**, it refreshes from the network (`refreshLatest`): the chat itself, to get its `lastSeq`, edits to the cached messages, and the latest page. New messages that continue the cache join the head by themselves.

If the chat hasn't been opened for a long time, there is a gap between the cache and the latest page, and the head can't step over it. The window then moves to the latest page, but only if it is still the head the user left. A window opened at the first unread message, or one the user has scrolled in the meantime, stays where it is, and the "to the latest" button takes the user down.

Offline, steps 1–2 work from the cache and step 3 fails: the chat stays on screen, and a snackbar explains why nothing new arrives.

### Scrolling up

`loadOlder` asks the repository for the 50 messages above the window. The repository takes them from the cache if it has all of them without a gap, and from the server (`beforeSeq`) otherwise:

```kotlin
val targetSeq = max(1, beforeSeq - PAGE_SIZE)
val isCached = messageDao.count(conversationId, targetSeq, beforeSeq - 1) == (beforeSeq - targetSeq).toInt()
val error = if (isCached) null else tryNetwork { save(chatApi.before(conversationId, beforeSeq, PAGE_SIZE)) }
// Offline the window grows by as much as the cache has without a gap.
val newFromSeq = cachedDownFrom(conversationId, beforeSeq, notBelow = targetSeq)
if (newFromSeq == beforeSeq && error != null) throw error
```

- **Offline the window grows by what the cache has** without a gap; the error reaches the screen only when the cache has nothing above at all.
- **The window stops being the head**: its bottom is fixed at the newest message shown.
- **Above 300 messages, the newest are cut off**, at the bottom the user has scrolled away from:

```text
[701, 1000] scrolled up to 651  →  [651, 1000], 350 messages  →  [651, 950]
```

### Scrolling down

`loadNewer` is the same downwards (`afterSeq`). When it reaches the chat's end (`lastSeq`), the window becomes the head again, and the Executor reports the messages that arrived at the bottom, as it does for a new message (see below). Above 300 messages, the oldest are cut off:

```text
[651, 950] scrolled down to 1000  →  [651, 1000], 350 messages  →  [701, 1000]
```

### New messages at the bottom

A page loaded below a window deep in the history stays below the screen: the list keeps the user's place, and the user scrolls down to it. The list follows the bottom only while the window is the head.

A new message from the socket is cached only as a continuation of the cache (`seq == maxSeq + 1`), so it joins the head. When the head grows past 300, its oldest messages are cut off, far above the screen. If the user is within three messages of the bottom, the screen scrolls down to the new one; a user reading higher up keeps their place.

### Jumps

| Jump | Does |
|---|---|
| **"To the latest" button** | shows while there are messages below the window (`hasNewer`); opens the newest cached messages as the head, scrolls to the bottom, then refreshes from the network |
| **Sending from deep in the history** | the same: the chat jumps to its end, where the sent message appears |
| **The toolbar button** | opens the window around a random message from the history, usually one not cached yet, so scrolling either way loads from the network; it is there to try out the pagination |

### Races

A paginator runs on the main thread, but its loads suspend, and the window may change in the meantime: a jump, a new message, the user scrolling the other way. Two rules keep a late answer from breaking the window:

- **A load applies only to the window it started from.** When it finishes, it compares the current window with the one it read at the start, and does nothing if they differ.
- **The newest message shown is used only if it was read for the current window.** Right after a jump, the screen still shows the old window's messages for a moment, and using them would produce a window that ends before it starts.

&nbsp;

## Why not Paging 3

Paging 3 with a `RemoteMediator` is built for a feed loaded from one end without gaps. The chat needs gaps in the cache, a head that grows by itself as messages arrive, and opening at any message; the list needs re-sorting as chats move up. Both fit in two small classes over Room: `ConversationsPaginator` is about 35 lines of code, `ChatPaginator` about 120.

&nbsp;

## Tests

| Test | Checks |
|---|---|
| `MessageDaoTest`, `ConversationDaoTest` | the window queries on real SQL: never stepping over a gap, the growing head, the boundary |
| `MessagesRepositoryImplTest` | pages from the cache or the server, growing from the cache while offline, opening around a seq |
| `ChatPaginatorTest` | a load right after a jump doesn't empty the window |
| `ChatViewModelTest` | opening at the first unread message, the head stopping when scrolling up, the window kept to 300, becoming the head again at the end, the jump |
| `ChatListViewModelTest` | the list window growing and then moving, scrolling back up without the network, working offline, returning to the top when the window points at nothing |
