# Сборка

[English version](../en/build.md) · [Все страницы](README.md)

Общая настройка модулей лежит в [`build-logic/`](../../../build-logic/) — included build с convention-плагинами. Каждый модуль подключает несколько из них и объявляет только свои зависимости.

&nbsp;

## Convention-плагины

У каждого вида модулей свой плагин:

| Плагин | Для каких модулей | Добавляет |
|---|---|---|
| `chatapp.android.application` | `:app` | настройку Android, targetSdk, подпись и R8 в release, языки приложения для системных настроек, [проверку графа модулей](modules.md#проверка), анализ зависимостей, предупреждения как ошибки на CI |
| `chatapp.android.library` | каждая Android-библиотека | compileSdk, minSdk, Java, анализ зависимостей, предупреждения как ошибки на CI, настройку тестов: unit-тесты только в debug, Robolectric на targetSdk приложения, вывод сообщения упавшего теста в лог |
| `chatapp.android.compose` | модули с Compose | компилятор Compose, BOM, Material 3, правила lint для Compose от Slack |
| `chatapp.koin` | модули с определениями Koin | Koin с компиляторным плагином, который проверяет граф при сборке `:app` |
| `chatapp.android.feature` | каждый `:feature:<name>:impl` | библиотека + Compose + Koin + serialization, библиотеки Navigation 3 и lifecycle |
| `chatapp.android.screenshots` | каждая библиотека с превью | [скриншот-тесты](testing.md#скриншоты), созданные из превью |
| `chatapp.jvm.library` | `:crossfeature:domain`, `:core:mvi`, `:core:network` | Kotlin JVM без Android, анализ зависимостей, предупреждения как ошибки на CI, вывод сообщения упавшего теста в лог |

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

Определения Koin — это аннотации (`@Singleton`, `@Factory`, `@KoinViewModel`, `@KoinWorker`), а его [компиляторный плагин](https://insert-koin.io/docs/reference/koin-compiler/compile-safety/) превращает их в код. При сборке `:app` он проверяет весь граф: недостающая зависимость валит сборку (`KOIN-D001`, с указанием файла и параметра, которому она нужна), а не роняет приложение во время работы.

- **Модули-библиотеки отдельно не проверяются**: граф полный только в `:app`. Сообщения плагина в библиотеках («validation skipped») понижены до уровня info, чтобы CI не принимал их за предупреждения.
- **Версия Kotlin — самая новая из тех, с которыми проверен плагин.** На более новой он предупреждает, а CI падает на предупреждениях, поэтому Kotlin и `koinCompilerPlugin` обновляются вместе в [`libs.versions.toml`](../../../gradle/libs.versions.toml).
- **В комментариях модуля с Koin не упоминаются `@KoinApplication` и `startKoin`**: плагин находит этот текст и принимает модуль за приложение.

&nbsp;

## Версии и репозитории

Все версии лежат в [`gradle/libs.versions.toml`](../../../gradle/libs.versions.toml), и build-logic читает тот же каталог. Модули подключают плагины без указания версий.

- **Репозитории объявлены только в `settings.gradle.kts`**: если модуль объявит свои, сборка упадёт. Google Maven используется только для артефактов Google и AndroidX.
- **Включены configuration cache, build cache и параллельная сборка.** Build cache хранит результаты задач, и CI тоже берёт их из прошлых запусков.
- **`contract/` — included build** и приложения, и сервера; **`server/`** — отдельная сборка со своим Gradle Wrapper и каталогом версий; настройки приложения её не подключают.

&nbsp;

## Стиль кода

Стиль проверяет [ktlint](https://github.com/pinterest/ktlint) 1.8 через Spotless по правилам из корневого [`.editorconfig`](../../../.editorconfig):

```sh
./gradlew spotlessCheck   # проверить
./gradlew spotlessApply   # исправить
```

- **Стиль `android_studio`**: он совпадает с форматтером Android Studio, поэтому автоформатирование в IDE и ktlint не спорят друг с другом.
- **120 символов в строке.**
- **Висячие запятые** в многострочных списках: новый элемент меняет в диффе только одну строку, а строки можно свободно переставлять.
- **По параметру на строку**, когда их два и больше.

Комментарии пишутся на английском, коротко, и объясняют то, чего не скажет сам код: причину, важный порядок действий, обход проблемы. У очевидного кода комментариев нет.

&nbsp;

## CI

[`ci.yml`](../../../.github/workflows/ci.yml) запускается на каждый pull request и на каждый push в `main`, если изменилась не только документация. Параллельно идут две задачи:

| Задача | Шаги |
|---|---|
| Android | стиль кода (ktlint) → зависимости между модулями (`assertModuleGraph`) → неиспользуемые зависимости (`buildHealth`) → проблемы в коде и ресурсах (Android lint с правилами Compose от Slack) → [unit- и скриншот-тесты](testing.md) → сборка приложения, которая заодно проверяет граф Koin |
| Server | стиль кода (ktlint) → API (тесты, включая [образцы API](testing.md#контракт-с-сервером)) |

- **Каждая проверка запускается, даже если предыдущая упала**, поэтому один запуск показывает все проблемы.
- **На CI предупреждения считаются ошибками** — и у Kotlin, и у lint: CI передаёт `-PwarningsAsErrors=true`, а этот флаг читают convention-плагины ([`WarningsAsErrors.kt`](../../../build-logic/src/main/kotlin/WarningsAsErrors.kt)) и сборка сервера. Локальная сборка только печатает предупреждения.
- **Неиспользуемые зависимости** находит плагин [Dependency Analysis](https://github.com/autonomousapps/dependency-analysis-gradle-plugin); его правила — в корневом [`build.gradle.kts`](../../../build.gradle.kts).
- **Исключения lint** — в корневом [`lint.xml`](../../../lint.xml): `LocalDarkTheme`, единственный CompositionLocal, который проект создаёт намеренно (см. [Навигация](navigation.md#записи-помнят-то-что-захватили)), и проверка «доступна новая версия», пониженная до подсказки, чтобы выход новой версии библиотеки не валил CI.
- **Упавшие тесты видны вверху страницы запуска**, каждый со своим сообщением и строкой ([action-junit-report](https://github.com/mikepenz/action-junit-report)), а изменившиеся скриншоты прикладываются к запуску как `changed-screenshots`.
- **Образ Android, который использует Robolectric, кэшируется** между запусками: Robolectric скачивает его в `~/.m2`, а кэш Gradle эту папку не покрывает.
- **Задача останавливается через 30 минут** (задача сервера — через 15), а не висит 6 часов, как по умолчанию в GitHub.

Новый push в pull request отменяет текущий запуск CI для этого pull request, если тот ещё идёт, а запуски на `main` всегда доходят до конца:

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}
```

[`latest-build.yml`](../../../.github/workflows/latest-build.yml) собирает release-APK после того, как CI прошёл на push в `main`, и публикует его как релиз `main-latest`, на который ссылается README. APK нужен ключ сервера: workflow записывает его в `local.properties` из секрета репозитория `CHATAPP_API_KEY` и падает, если секрета нет. Pull request'ы до этого workflow никогда не доходят, поэтому их код ключа не получает.

&nbsp;

## Подпись

Debug-ключ лежит в репозитории ([`build-logic/debug.keystore`](../../../build-logic/debug.keystore)), поэтому у сборки с любого компьютера, включая CI, одна и та же подпись, и новый APK ставится поверх предыдущего.

- **Release-сборки подписаны тем же ключом**, чтобы их можно было поставить на устройство или эмулятор; для публикации в магазине нужен настоящий ключ.
- **В release-сборках включён R8**: он ужимает код и ресурсы.
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
