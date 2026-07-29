# Локальная разработка

Последнее обновление: **29 июля 2026 года**.

## Требования

- Windows, macOS или Linux;
- JDK 17;
- Android SDK Platform 37;
- Android SDK Build Tools и Platform Tools;
- Git;
- интернет для первой загрузки Gradle и Maven dependencies.

Зафиксированный toolchain:

| Компонент | Версия |
| --- | --- |
| Gradle Wrapper | 9.5.0 |
| Android Gradle Plugin | 9.3.0 |
| Kotlin Compose plugin | 2.3.21 |
| compile / target SDK | 37 / 37 |
| min SDK | 28 |
| Compose BOM | 2026.06.01 |
| Media3 | 1.10.1 |
| Coil | 3.5.0 |
| jsoup | 1.22.2 |

Локальный `.tools/` в исходной рабочей папке — только bootstrap cache и в Git не входит.
Свежий clone использует Gradle Wrapper и установленный Android SDK.

## Подготовка clone

```powershell
git clone https://github.com/reziarlleh/KinogoATV.git
Set-Location KinogoATV
```

Private repository потребует авторизацию GitHub.

Укажите JDK и Android SDK:

```powershell
$env:JAVA_HOME = 'C:\Path\To\jdk-17'
$env:ANDROID_HOME = 'C:\Path\To\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

При необходимости создайте некоммитируемый `local.properties`:

```properties
sdk.dir=C\:\\Path\\To\\Android\\Sdk
```

Проверьте окружение:

```powershell
.\gradlew.bat --version
```

## Подпись

### Обычная разработка и CI

Если stable key отсутствует, debug variant использует стандартную Android debug-подпись.
Этого достаточно для unit/lint/emulator/чистого тестового устройства.

Такой APK **не обновит** установленную на пользовательском TV stable-signed версию.

### Обновление пользовательского TV и выпуск

По умолчанию ожидается:

```text
.signing/kinogo-tv-dev.keystore
```

Можно указать внешний безопасный путь в user Gradle properties:

```properties
KINOGO_SIGNING_STORE_FILE=D:/secure/kinogo-tv-dev.keystore
KINOGO_SIGNING_STORE_PASSWORD=<secret>
KINOGO_SIGNING_KEY_ALIAS=<alias>
KINOGO_SIGNING_KEY_PASSWORD=<secret>
```

Или использовать environment variables с префиксом `ORG_GRADLE_PROJECT_`.
Файл и значения не добавляются в project `gradle.properties`.

При наличии ключа debug и release получают стабильную подпись. Release-задача без него
завершается явной ошибкой.

## Канонические команды

На машине с ограниченной памятью:

```powershell
.\gradlew.bat testDebugUnitTest `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'

.\gradlew.bat lintDebug assembleDebug `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Полная локальная проверка:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release build выполняйте только по [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md).

## Android Studio

1. Открыть корень проекта.
2. Выбрать JDK 17 для Gradle.
3. Дождаться sync с Android SDK 37.
4. Для emulator использовать Android TV system image.
5. Не запускать default managed instrumentation на пользовательском TV.

Touchscreen объявлен необязательным, leanback — обязательным. Телефонный layout не является
целевым.

## Структура исходников

```text
app/src/main/java/com/kinogo/atv/
  data/auth/          credentials, HTML login, cookie session
  data/catalog/       safe HTML transport, routes, parsers, repository
  data/history/       progress codec/store and legacy recovery
  data/library/       server statuses/favorite and local outbox
  data/mirror/        candidates, health, trust and persistence
  data/network/       resilient public DNS
  data/playback/      discovery, provider documents, adapters, mapping
  data/settings/      TV preferences
  diagnostics/        startup crash/stall reporting
  domain/             host-independent models and invariants
  player/             reducer, key mapping, Media3 and Web fallback
  ui/                 TV components, mappers and screens
```

Подробная карта — [`ARCHITECTURE.md`](ARCHITECTURE.md).

## Правила изменений

- Сначала добавить/обновить characterization test для parser/reducer/store.
- Не смешивать UI polish с сетевым или storage migration.
- Не использовать fixture video в production live-flow.
- Не добавлять absolute mirror host в persisted content identity.
- Не логировать transient URLs и account state.
- D-pad focus — часть функционального контракта, а не косметика.
- Любая пользовательская строка должна помещаться в TV safe area и оставаться читаемой на
  расстоянии.
- В конце обновить документацию по матрице из `AGENTS.md`.

## PowerShell

Аргументы Gradle `-P...` с точками в имени нужно заключать в кавычки:

```powershell
'-Pkotlin.compiler.execution.strategy=in-process'
```

Без кавычек PowerShell может передать `.compiler.execution.strategy=in-process` как имя
Gradle task.

## Перед началом работы

```powershell
git status -sb
git branch --show-current
```

Не используйте destructive Git-команды и не перезаписывайте незнакомые локальные изменения.
Для незавершённой задачи заполните [`HANDOFF_TEMPLATE.md`](HANDOFF_TEMPLATE.md).
