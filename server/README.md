# ChatApp server

[Русская версия](README.ru.md)

A Ktor server for the app: chats, messages, sync logs and live events, with bots that write on their own.

- **Address:** `http://2.56.204.151:8081/` (there's no domain, so no HTTPS).
- **Build:** a separate Gradle build that the app's `settings.gradle.kts` doesn't include.
- **Formats:** the DTOs in [`../contract`](../contract/), an included build that the app uses too.
- **Storage:** a single SQLite file. Its tables are created on the first start, and an empty database is filled with demo data.

&nbsp;

## API

Every request carries two headers; without them the server answers `401`.

| Header | Value |
|---|---|
| `X-Api-Key` | The key from `API_KEY`. The app has its copy in `local.properties` (`chatapp.apiKey`). |
| `X-User-Id` | Who is asking: the id of a user from `GET /users`. Not needed for `GET /users` itself. |

The requests:

| Request | Returns |
|---|---|
| `GET /users` | The people you can sign in as (bots aren't listed) |
| `GET /conversations?limit` | The user's most active chats |
| `GET /conversations?beforeSortKey&limit` | The chats below that sortKey |
| `GET /conversations/{id}` | One chat, or 404 when the user isn't in it |
| `GET /conversations/changes?since&limit` | The user's chat list log after a token |
| `GET /conversations/{id}/messages?limit` | The newest messages |
| `…/messages?beforeSeq` / `afterSeq` / `aroundSeq` | Messages older than, newer than, or around a seq |
| `GET /conversations/{id}/messages/changes?since&limit` | The chat's log of edits and deletions after a token |
| `POST /conversations/{id}/messages` | Sends a message; returns it and the chat |
| `POST /conversations/{id}/read` | Marks the chat read up to a seq; returns the chat |
| `GET /bots` | Whether the bots are on |
| `PUT /bots` | Turns the bots on or off for everyone |
| `WS /events` | The user's events, one JSON object per text frame |

Every request and response the app relies on has a sample in [`api-samples/`](api-samples/), and `requests.json` in that folder lists each request's method and address. The server's tests check that it answers exactly as the samples show, and the app's tests read the same files, so changing a format or an address on one side fails the tests on that side.

### `GET /users`

```json
[{"id": "anna", "name": "Anna"}, {"id": "max", "name": "Max"}]
```

### `GET /conversations`

A page of the user's chats, most active first:

| Parameter | Meaning |
|---|---|
| `limit` | From 1 to 100. |
| `beforeSortKey` | The next page: the chats below this `sortKey`. Without it, the first page. |

```json
{
  "items": [
    {
      "id": "anna-bot",
      "title": "Oliver",
      "sortKey": 1,
      "version": 1,
      "unreadCount": 1,
      "lastSeq": 3,
      "lastReadSeq": 2,
      "lastMessage": {
        "id": "anna-bot:3",
        "conversationId": "anna-bot",
        "seq": 3,
        "version": 1,
        "authorId": "bot",
        "authorName": "Oliver",
        "text": "How are you?",
        "sentAtMillis": 1700000003000,
        "isDeleted": false
      }
    }
  ],
  "hasMore": false
}
```

- `lastSeq` is the chat's newest message, and `lastReadSeq` is how far the user has read; when there are unread messages, the app opens the chat at `lastReadSeq + 1`.
- `GET /conversations/{id}` returns one chat in the same format.

### `GET /conversations/{id}/messages`

A page of messages, newest first:

| Parameter | Meaning |
|---|---|
| `limit` | From 1 to 100. |
| `beforeSeq` | Messages older than this seq. |
| `afterSeq` | Messages newer than this seq. |
| `aroundSeq` | Messages around this seq, including it. |

Without any of the three, you get the newest page; more than one is a `400`. A `limit` out of range is a `400` in every request.

```json
{"items": [{"id": "anna-bot:3", "seq": 3, "text": "How are you?", "...": "..."}]}
```

### `GET …/changes`

A sync log after a token: `GET /conversations/changes` for the chat list, `GET /conversations/{id}/messages/changes` for a chat's edits and deletions.

| Parameter | Meaning |
|---|---|
| `since` | The token the client has caught up to. Without it: no changes, only the current token. |
| `limit` | From 1 to 500. |

```json
{
  "changed": [{"id": "dm-anna-max", "sortKey": 3, "version": 2, "unreadCount": 1, "...": "..."}],
  "removedIds": [],
  "nextToken": "1",
  "hasMore": false,
  "resetRequired": false
}
```

The messages log has no `removedIds`: a deleted message stays in it as a tombstone.

### `POST /conversations/{id}/messages`

```json
{"clientId": "c-1", "text": "Fine, thanks"}
```

The response holds the message as stored, and the chat as the sender now sees it:

```json
{
  "message": {"id": "anna-bot:4", "seq": 4, "authorId": "anna", "text": "Fine, thanks", "...": "..."},
  "conversation": {"id": "anna-bot", "sortKey": 3, "version": 2, "unreadCount": 0, "lastReadSeq": 4, "...": "..."}
}
```

- **`clientId`** is the app's own id for the message: a retry with the same id returns the message sent the first time instead of posting it again.
- **The text is trimmed** and must then be 1 to 4,000 characters long; otherwise `400`.
- **Sending marks the chat read** for the sender.

### `POST /conversations/{id}/read`

```json
{"seq": 3}
```

Returns the chat. `lastReadSeq` only grows: an older seq changes nothing.

### `GET /bots`, `PUT /bots`

```json
{"enabled": false}
```

`GET` returns the current value; `PUT` takes this body and returns the new value. The switch applies to the whole server, see [Bots](#bots).

### `WS /events`

The user's events, one per text frame, each carrying the whole object in its current state:

```json
{
  "type": "message.new",
  "message": {"id": "dm-anna-max:3", "seq": 3, "authorId": "max", "text": "Lunch at noon?", "...": "..."}
}
```

| `type` | Field | Sent when |
|---|---|---|
| `message.new` | `message` | a new message in one of the user's chats |
| `message.changed` | `message` | an edit or a deletion |
| `conversation.updated` | `conversation` | a new message, a read, an edit of the last message |
| `conversation.removed` | `conversationId` | the user left a chat; not sent yet, since there is no way to leave a chat |
| `bots.changed` | `enabled` | someone turned the bots on or off |

A push carries the same events, as a JSON array in its `events` field ([`push-data.json`](api-samples/push-data.json)).

&nbsp;

## Rules

The rules the app relies on, grouped by what they are about.

### Messages
- **`seq`** numbers a chat's messages 1, 2, 3… with no gaps. A deleted message stays as a tombstone (`isDeleted`, empty text), so a gap in a client's cache is always a part it hasn't loaded yet.
- **`version`** grows with every change to the message.

### Chats
- **`sortKey`** is unique per user and only grows: a new message moves the chat to the top. So a chat can only move up.
- **`version`** grows with every change to the chat as the user sees it: a message, a read, an edit of the last message.
- **`unreadCount`** counts other people's messages after `lastReadSeq`, not counting deleted ones.
- **Title:** a group has its own; a direct chat is named after the other person.

### Sync logs
- **A token** is a position in a log, as a string.
- **A batch** holds each changed object once, as it is now, with `nextToken` and `hasMore`.
- **Logs:** one per user for the chat list, one per chat for message edits. Each keeps its last 5,000 entries.
- **`resetRequired`** is returned for a token that is older than the kept entries, newer than the newest one (the database was recreated), or not a number.

### Events
- **A new message** sends each person in the chat `message.new` and `conversation.updated`.
- **An edit or a deletion** sends `message.changed`, and `conversation.updated` too when it is the last message.
- **A read** sends `conversation.updated` to all of the reader's sockets, so other devices drop the unread count.
- **The bots switch** sends `bots.changed` to every open socket of every user. It has no log: a client reads `GET /bots` on each connection.
- **A slow socket** that falls 256 events behind is closed; the client reconnects and syncs.
- **Bots** have no sockets and no logs.

&nbsp;

## Limits

Writes are rate-limited per user, and every request body is limited in size:

| What | Limit |
|---|---|
| Messages sent | 10 a minute per user, then `429` |
| Reads | 30 a minute per user, then `429` |
| Request body | 16 KB, then `413` |

&nbsp;

## Data

An empty database is filled with three people (Anna, Max, Kate) and 220 bots. Each person has:

- **direct chats** with the two others;
- **a group**, Project team, with them and four bots;
- **a chat with Historian**, 3,000 messages long, more than fits in the app's chat window;
- **215 chats with bots**, more than fits in the app's list window.

Some chats start with unread messages. The data is the same every time (a fixed random seed).

### Bots
- **On their own:** every 10–30 s, a bot writes to one of its chats; sometimes it edits (5 %) or deletes (2 %) its last message instead.
- **Replies:** a bot replies 2–5 s later to a person who writes to it in a direct chat.
- **The switch:** `PUT /bots` turns them off for the whole server, replies included. The value is kept in the database (`settings`), so it survives a restart and a deploy; bots are on by default.
- **No bots at all:** `BOTS_ENABLED=false`, as in the tests; the switch then stores its value but changes nothing.

&nbsp;

## Running

The server's own Gradle wrapper is in this folder:

```sh
./gradlew test            # the tests, including the API samples
API_KEY=dev ./gradlew run # locally on port 8081 with chat.db in server/
./gradlew spotlessCheck   # code style (rules in the root .editorconfig); spotlessApply fixes it
./deploy.sh               # test, build and restart the server on this machine
```

`deploy.sh` runs the server as the user systemd service `chatapp-server` from a copy in `~/server/chatapp/app`:

| Path | Holds |
|---|---|
| `~/server/chatapp/data/chat.db` | The database; a deploy keeps it |
| `~/server/chatapp/server.env` | `API_KEY`, made on the first deploy (mode 600) |

```sh
journalctl --user -u chatapp-server -f        # logs, including every request
systemctl --user status chatapp-server        # check whether it is running (also: stop, restart)
```

To start over with fresh data, stop the service, delete `chat.db` and start the service again: clients will then get `resetRequired`.
