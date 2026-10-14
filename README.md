# ChatApp

[![CI](https://github.com/dmitriiecho/ChatApp/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/dmitriiecho/ChatApp/actions/workflows/ci.yml)

[Русская версия](README.ru.md)

ChatApp is an example Android chat built on a modern stack, with its own server. You pick who you are and get a list of chats: with two other people, a group, and a couple of hundred bots that write on their own and answer you.

&nbsp;

## How it looks

Signing in as Max, scrolling the chat list, opening a chat with a bot, writing to it and getting an answer:

<p>
  <img src=".github/readme/flow.webp" width="240" alt="Signing in as Max, scrolling the chat list, opening a chat with Liam 6, sending Hi! Are you there? and getting the answer Hi! How are you?">
</p>

&nbsp;

## What's inside

The app is small, but most of what a real messenger needs is there:

- **Offline first**: the screens read only the cache in Room. A chat opened before opens without a network, and new messages come when it is back.
- **Live updates** over a WebSocket: new messages, edits and deletions, chats rising to the top of the list, reads on another device.
- **Catching up by token**: after the app was in the background or offline, it asks the server only for what changed since then.
- **Sending through an outbox**: a message shows at once and is sent by WorkManager when there is a network, in order and never twice, also after the app is closed.
- **Long chats and long lists**: windows over the cache that load pages up and down, open at the first unread message and never hold more than a few hundred items. A button in a chat's top bar jumps to a random message of its history to try it.
- **Read marks** with a "New messages" divider and unread counters that follow every device.
- **Bots** that write every 10–30 seconds, and a switch under the chat list that turns them off for everyone.
- **A light and a dark theme**, switched from the chat list.

&nbsp;

## Architecture

The modules are arranged in four levels, and the build checks that no module depends on a level above its own:

```text
app/           app                                the application
feature/       login, chats                       one feature each
crossfeature/  domain, data, ui                   chat code used by two or more features
core/          designsystem, compose-utils,       knows nothing about chats
               mvi, network
```

Screens run on MVI on a small core of the project's own, DI is Koin with its compiler plugin, navigation is Navigation 3. How modules, navigation, screens, data, sync and the build work is explained in the [architecture docs](docs/architecture/en/README.md).

&nbsp;

## Tests and CI

- **Tests** run on the JVM and cover the MVI core, the screens' logic, the data layer with its syncs, the contract with the server, and a screenshot of every preview.
- **CI** runs on every pull request: code style, module rules, unused dependencies, lint, tests and a build of the app, which also checks its Koin graph.

&nbsp;

## Running

The easiest way is to download the [APK from `main`](https://github.com/dmitriiecho/ChatApp/releases/download/main-latest/ChatApp.apk). It is a release build for Android 8.0 and newer, rebuilt by CI after every code change to `main`.

To build the app yourself, you need JDK 21, the Android SDK and the server's key:

```properties
# local.properties
chatapp.apiKey=<the key>
```

```sh
./gradlew :app:installDebug   # installs the app on a connected device
```

Without the key the app builds and starts, but the server answers 401 and the sign-in shows an error.

&nbsp;

## Server

The chats, messages and events are served by a Ktor server with SQLite in [`server/`](server/README.md). It is deployed at `http://2.56.204.151:8081/`, and both debug and release builds use it.

&nbsp;

## Built with AI agents

The app is built with the AI agents Claude Code, Cursor and Grok Build, running on Claude Opus and Grok models. Agents speed up the work a lot, but they don't write good code on their own: they need rules, step-by-step guidance, and someone to check their output. Here is how that works in this project:

- **The rules are written down for the agents**: [CLAUDE.md](CLAUDE.md) describes the module layout, the code style and the conventions for screens and data.
- **Changes are made in small steps**, and each one is discussed and checked before it is committed.
- **Behaviour is checked by tests and CI** (see [above](#tests-and-ci)), and every change is also tried out on an emulator.
