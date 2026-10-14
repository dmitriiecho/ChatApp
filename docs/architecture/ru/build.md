# Сборка

[English version](../en/build.md) · [Все страницы](README.md)

Общая настройка модулей лежит в [`build-logic/`](../../../build-logic/) — included build с convention-плагинами. Каждый модуль подключает несколько из них и объявляет только свои зависимости.

&nbsp;

## Convention-плагины

У каждого вида модулей свой плагин:

| Плагин | Для чего | Добавляет |
|---|---|---|
| `chatapp.android.application` | `:app` | настройку Android, targetSdk, подпись и R8 в release, языки приложения для системных настроек, [проверку графа модулей](modules.md#проверка), анализ зависимостей, предупреждения как ошибки на CI |
| `chatapp.android.library` | каждая Android-библиотека | compileSdk, minSdk, Java, анализ зависимостей, предупреждения как ошибки на CI, настройку тестов: unit-тесты только в debug, Robolectric на targetSdk приложения, сообщение упавшего теста в логе |
| `chatapp.android.compose` | модули с Compose | компилятор Compose, BOM, Material 3, правила lint для Compose от Slack |
| `chatapp.koin` | модули с определениями Koin | Koin с компиляторным плагином, который проверяет граф при сборке `:app` |
| `chatapp.android.feature` | каждый `:feature:<name>:impl` | библиотека + Compose + Koin + serialization, библиотеки Navigation 3 и lifecycle |
| `chatapp.android.screenshots` | каждый модуль-библиотека с превью | [скриншот-тесты](testing.md#скриншоты), созданные из превью |
| `chatapp.jvm.library` | `:crossfeature:domain`, `:core:mvi`, `:core:network` | Kotlin JVM без Android, анализ зависимостей, предупреждения как ошибки на CI, сообщение упавшего теста в логе |

Поэтому файл сборки модуля короткий:

```kotlin
// crossfeature/ui/build.gradle.kts
plugins {
    alias(libs.plugins.chatapp.android.library)
    alias(libs.plugins.chatapp.android.compose)
    alias(libs.plugins.chatapp.android.screenshots)
}

android {
    namespace = "krio.systemdesign.chatapp.crossfeature.ui"
}

dependencies {
    implementation(project(":core:designsystem"))
}
```

Версии SDK и Java заданы в одном месте, [`AndroidConfig.kt`](../../../build-logic/src/main/kotlin/AndroidConfig.kt):

```kotlin
internal object AndroidConfig {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 26
    const val TARGET_SDK = 37
    val JAVA_VERSION = JavaVersion.VERSION_21
}
```

&nbsp;

## Компиляторный плагин Koin

Определения Koin — это аннотации (`@Singleton`, `@Factory`, `@KoinViewModel`, `@KoinWorker`), а его [компиляторный плагин](https://insert-koin.io/docs/reference/koin-compiler/compile-safety/) превращает их в код. При сборке `:app` он проверяет весь граф: недостающая зависимость валит сборку (`KOIN-D001`, с файлом и параметром, которому она нужна), а не роняет приложение при запуске.

- **Модули-библиотеки отдельно не проверяются**: граф полный только в `:app`. Заметки плагина в библиотеках («validation skipped») понижены до info, чтобы CI не принимал их за предупреждения.
- **Kotlin держится на самой новой версии, с которой проверен плагин.** На более новой он предупреждает, а CI падает на предупреждениях, поэтому Kotlin и `koinCompilerPlugin` обновляются вместе в [`libs.versions.toml`](../../../gradle/libs.versions.toml).
- **В комментариях модуля с Koin не пишут `@KoinApplication` и `startKoin`**: плагин находит этот текст и принимает модуль за приложение.

&nbsp;

## Версии и репозитории

Все версии лежат в [`gradle/libs.versions.toml`](../../../gradle/libs.versions.toml), и build-logic читает тот же каталог. Модули подключают плагины без указания версий.

- **Репозитории объявлены только в `settings.gradle.kts`**: модуль, который объявит свои, сломает сборку. Google Maven используется только для артефактов Google и AndroidX.
- **Включены configuration cache, build cache и параллельная сборка.** Build cache хранит результаты задач, и CI тоже переиспользует их из прошлых запусков.
- **`contract/` — included build** и приложения, и сервера; **`server/`** — отдельная сборка со своим wrapper и каталогом версий, которую настройки приложения не подключают.

&nbsp;

## Стиль кода

Стиль проверяет [ktlint](https://github.com/pinterest/ktlint) 1.8 через Spotless по правилам из корневого [`.editorconfig`](../../../.editorconfig):

```sh
./gradlew spotlessCheck   # проверить
./gradlew spotlessApply   # исправить
```

- **Стиль `android_studio`**, совпадающий с форматтером Android Studio, поэтому его автоформат и ktlint не спорят.
- **120 символов в строке.**
- **Висячие запятые** в многострочных списках, чтобы дифф оставался в одну строку, а строки можно было свободно переставлять.
- **По параметру на строку**, когда их два и больше.

Комментарии на английском и короткие, и объясняют то, чего не может сказать сам код: причину, важный порядок, обход проблемы. У очевидного кода комментариев нет.

&nbsp;

## CI

[`ci.yml`](../../../.github/workflows/ci.yml) запускается на каждый pull request и на каждый push в `main`, если изменилась не только документация. Параллельно идут две задачи:

| Задача | Шаги |
|---|---|
| Android | стиль кода (ktlint) → зависимости между модулями (`assertModuleGraph`) → неиспользуемые зависимости (`buildHealth`) → проблемы в коде и ресурсах (Android lint с правилами Compose от Slack) → [unit- и скриншот-тесты](testing.md) → приложение собирается, граф Koin проверен |
| Server | стиль кода (ktlint) → API (тесты, включая [образцы API](testing.md#контракт-с-сервером)) |

- **Каждая проверка запускается, даже если предыдущая упала**, поэтому один запуск показывает все проблемы.
- **Предупреждения на CI — ошибки**, и Kotlin, и lint: CI передаёт `-PwarningsAsErrors=true`, его читают convention-плагины ([`WarningsAsErrors.kt`](../../../build-logic/src/main/kotlin/WarningsAsErrors.kt)) и сборка сервера. Локальная сборка только печатает предупреждения.
- **Неиспользуемые зависимости** находит плагин [Dependency Analysis](https://github.com/autonomousapps/dependency-analysis-gradle-plugin); его правила — в корневом [`build.gradle.kts`](../../../build.gradle.kts).
- **Исключения lint** — в корневом [`lint.xml`](../../../lint.xml): `LocalDarkTheme`, единственный CompositionLocal, который проект создаёт намеренно (см. [Навигация](navigation.md#записи-помнят-то-что-захватили)), и «доступна новая версия», пониженная до подсказки, чтобы выход новой версии библиотеки не валил CI.
- **Упавшие тесты видны вверху страницы запуска**, каждый со своим сообщением и строкой ([action-junit-report](https://github.com/mikepenz/action-junit-report)), а изменившиеся скриншоты прикладываются к запуску как `changed-screenshots`.
- **Android, на котором работает Robolectric, кэшируется** между запусками: Robolectric скачивает его в `~/.m2`, а кэш Gradle туда не дотягивается.
- **Задача останавливается через 30 минут** (задача сервера — через 15), а не висит 6 часов, как по умолчанию в GitHub.

Новый push в pull request отменяет его запуск, если тот ещё идёт, а запуски на `main` всегда доходят до конца:

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}
```

&nbsp;

## Подпись

- **Release-сборки подписаны debug-ключом**, чтобы ставиться на эмулятор; для публикации в магазине нужен настоящий ключ.
- **В release-сборках включён R8**, он сжимает код и ресурсы.
- **Ключ сервера** берётся из `chatapp.apiKey` в `local.properties`, которого нет в git. Сборка без него работает, но сервер отвечает 401.

&nbsp;

## Команды

Основные команды, из корня проекта:

```sh
./gradlew :app:installDebug            # поставить приложение на подключённое устройство
./gradlew assertModuleGraph            # проверить зависимости между модулями
./gradlew buildHealth                  # найти неиспользуемые зависимости
./gradlew lintDebug                    # запустить Android lint на всех модулях
./gradlew test                         # запустить тесты приложения, включая скриншоты
./gradlew recordRoborazziDebug -Proborazzi.cleanupOldScreenshots=true  # записать новые скриншоты после изменения UI
cd server && ./gradlew test            # запустить тесты сервера
server/deploy.sh                       # проверить, собрать и перезапустить сервер на его машине
```

Те же проверки, что на CI, с предупреждениями как ошибками:

```sh
./gradlew -PwarningsAsErrors=true spotlessCheck assertModuleGraph buildHealth lintDebug test :app:assembleDebug
cd server && ./gradlew -PwarningsAsErrors=true spotlessCheck test
```

> [!WARNING]
> Никогда не запускайте две сборки Gradle в одном checkout одновременно: у них общие папки `build/`, и они портят результаты друг друга.
