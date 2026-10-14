# Модули

[English version](../en/modules.md) · [Все страницы](README.md)

Папки — это уровни, снизу вверх: `core/` → `crossfeature/` → `feature/` → `app/`. Модуль никогда не зависит от уровня выше своего, и фичи никогда не зависят друг от друга.

&nbsp;

## Уровни

У каждого уровня свои модули и своё назначение:

| Уровень | Модули | Что там лежит |
|---|---|---|
| `core/` | `designsystem`, `compose-utils`, `mvi`, `network` | код, который подошёл бы любому приложению: тема, иконки, общие компоненты, связка с Compose, ядро MVI, HTTP-клиент |
| `crossfeature/` | `domain`, `data`, `ui` | код про чаты, который нужен двум и более фичам: модели, их хранение и синхронизация, компоненты чата |
| `feature/` | `login`, `chats` | по одной фиче; её экраны лежат в `impl` |
| `app/` | `:app` | приложение: запускает Koin, держит хост навигации и фоновую работу; от него ничего не зависит |

Уровни — это папки, а не только правила зависимостей: по дереву сразу видно, какой код знает про чаты. Модулей-свалок вроде `:core:common`, которые в итоге правит каждая фича, нет.

Рядом с модулями приложения лежат три отдельные Gradle-сборки:

| Сборка | Что там |
|---|---|
| [`build-logic/`](../../../build-logic/) | convention-плагины, которые подключает каждый модуль (см. [Сборка](build.md)) |
| [`contract/`](../../../contract/) | DTO, общие для приложения и сервера: запросы, ответы, события, заголовки |
| [`server/`](../../../server/README.ru.md) | сервер на Ktor; `settings.gradle.kts` приложения его не подключает |

`contract/` — included build и у приложения, и у сервера, поэтому обе стороны компилируются с одними и теми же классами.

&nbsp;

## Куда класть новый код

Код остаётся в фиче, которая им пользуется. Когда он понадобится второй фиче, он переезжает в `crossfeature/`:

- **Нужен одной фиче** — остаётся в этой фиче.
- **Понадобился второй фиче** — переезжает в `crossfeature/`: модели и интерфейсы репозиториев в `:crossfeature:domain`, их реализации в `:crossfeature:data`, компоненты чата (`ChatAvatar`) в `:crossfeature:ui`.
- **Ничего не знает про чаты** — в `core/`: всё, что рисует, в `:core:designsystem`, связка без отрисовки в `:core:compose-utils`.

Стилизованные компоненты подчиняются тому же правилу:

| Компонент | Модуль |
|---|---|
| без изменений подошёл бы любому другому приложению | `:core:designsystem` |
| знает слова чата (чат, сообщение, контакт), и им пользуются две и более фичи | `:crossfeature:ui` |
| знает слова чата, и им пользуется одна фича | `:feature:<name>:ui` |

Фичи сами не стилизуют компоненты Material. Пример — счётчик непрочитанных: `Badge` из Material по умолчанию красный, и он читается как ошибка. Поэтому `CountBadge` цвета акцента лежит в `:core:designsystem`, а список чатов только ставит его на место.

&nbsp;

## Фича

Экраны фичи, её ViewModel, собственные use case и навигация лежат в `impl`:

```kotlin
// feature/chats/impl/build.gradle.kts
plugins {
    alias(libs.plugins.chatapp.android.feature)
    alias(libs.plugins.chatapp.android.screenshots)
}

dependencies {
    implementation(project(":crossfeature:domain"))
    implementation(project(":crossfeature:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:compose-utils"))
    implementation(project(":core:mvi"))
    // ...
}
```

- **Модуль `ui` появляется, когда у фичи есть свои компоненты чата**, которыми не пользуется другая фича. Пока его нет ни у одной: их компоненты — это части экрана, и они лежат в его папке `components/`.
- **Модуля `api` нет.** Фичи не зависят друг от друга, поэтому API фичи нужно только приложению, а оно связывает фичи колбэками (см. [Навигация](navigation.md)).
- **От `:crossfeature:data` зависит только `:app`.** Фичи видят интерфейсы репозиториев из `:crossfeature:domain`, а реализации подставляет Koin.

&nbsp;

## Что фича показывает наружу

В модуле `impl` публично только то, что подключает приложение, и всё это лежит в `presentation/navigation`, плюс модуль Koin в `di/`. Всё остальное — экраны, ViewModel, Executor, use case — `internal`:

```kotlin
object ChatsRoutes {
    @Serializable data object ChatList : NavKey                        // с него приложение открывает фичу
    @Serializable internal data class Chat(val chatId: String) : NavKey
}

fun EntryProviderScope<NavKey>.chatsEntries(navigator: Navigator, onToggleTheme: () -> Unit) { /* ... */ }

val chatsNavKeys = SerializersModule { /* ... */ }                     // для сохранения стека экранов
```

&nbsp;

## Модули Koin

У каждого модуля с определениями ровно один публичный модуль Koin, в пакете `di/`. Он сканирует свой пакет и находит классы с `@Singleton`, `@Factory` или `@KoinViewModel`:

```kotlin
@Module
@Configuration
@ComponentScan("krio.systemdesign.chatapp.feature.chats.impl")
class ChatsModule
```

- **`:app` напрямую зависит от каждого модуля с определениями**, включая `:core:network`. `@KoinApplication` на `ChatApp` собирает все модули с `@Configuration` из classpath `:app`, и компиляторный плагин проверяет там весь граф.
- **Реализация сама привязывается к своим интерфейсам** и остаётся `internal`: `MessagesRepositoryImpl` подставляется везде, где просят `MessagesRepository`.
- **Собственный модуль `:app` сканирует только свои подпакеты** (`network`, `session`, `work`). Его корневой пакет содержит пакеты всех остальных модулей, а сканирование заходит в зависимости, поэтому оно объявило бы их определения второй раз.

&nbsp;

## Пакеты

Пакет библиотеки повторяет путь модуля, а пакет приложения — его applicationId:

| Модуль | Пакет |
|---|---|
| `:crossfeature:ui` | `krio.systemdesign.chatapp.crossfeature.ui` |
| `:feature:chats:impl` | `krio.systemdesign.chatapp.feature.chats.impl` |
| `:core:compose-utils` | `krio.systemdesign.chatapp.core.composeutils` (дефис убирается) |
| `:app` | `krio.systemdesign.chatapp` |

&nbsp;

## Проверка

`./gradlew assertModuleGraph` сверяет каждую зависимость между модулями со списком разрешённых в [`ModuleGraphRules.kt`](../../../build-logic/src/main/kotlin/ModuleGraphRules.kt), и CI запускает её на каждом pull request. Каждое правило — одно регулярное выражение для строки «откуда -> куда»:

```kotlin
allowed = arrayOf(
    """:core:.* -> :core:.*""",
    """:crossfeature:(domain|ui) -> :core:.*""",
    """:crossfeature:data -> :(core:.*|crossfeature:domain)""",
    """:feature:\w+:ui -> :(core:designsystem|crossfeature:ui)""",
    """:feature:\w+:impl -> :(core:.*|crossfeature:(domain|ui))""",
    """:feature:(\w+):impl -> :feature:\1:ui""",   // только свой ui
    """:app -> :(core|crossfeature|feature):.*""",
)
```

Зависимость, которую не разрешает ни одно правило, валит сборку:

```text
[':feature:chats:impl' -> ':crossfeature:data'] not allowed by any of [...]
```

Новый вид зависимости между модулями — осознанное решение: для него нужно новое правило в этом файле, и оно видно в диффе.
