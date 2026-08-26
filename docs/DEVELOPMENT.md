# Локальная разработка

Последнее обновление: **26 августа 2026 года**.

## Требования

- Windows, macOS или Linux;
- JDK 17;
- Android SDK Platform 37;
- Android SDK Build Tools и Platform Tools;
- Git;
- Python 3 — только для независимой проверки release update manifest;
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

До фактической смены visibility repository может требовать авторизацию GitHub. Public-readiness
документация сама по себе не доказывает, что visibility уже изменена.

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
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

`.github/workflows/android.yml` на push в `main` и pull request выполняет clean-clone subset
`testDebugUnitTest lintDebug assembleDebug` с JDK 17 / SDK 37. Official Actions закреплены
полными commit SHA и используют Node 24. CI использует обычную debug signature, не собирает
распространяемый stable-signed APK и не заменяет полный локальный canonical набор выше.

Текущий local exact snapshot C-009 / `0.5.3` code 17 привязан к application
source `777c8a0528f24db67402536631257d6cdc91f148`. Canonical command
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease` —
SUCCESS за 7 мин 12 с; 89 suites / 455 tests, 0 failures/errors/skips; lint —
0 errors / 22 warnings / 2 hints. Post-commit `assembleRelease --rerun-tasks` — SUCCESS за
4 мин 04 с, 50 tasks. Exact `dist/KinogoATV-0.5.3-code17.apk`: 38 386 398 bytes,
SHA-256 `3C88DF356A9815865DB02F7821DA53BE3C6E25F03FE493516FCCAF0F48F0C17A`, package
`com.kinogo.atv`, code 17 / `0.5.3`, minSdk 28, targetSdk 37, zipalign PASS, v2 true,
ровно один signer, certificate SHA-256
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`; embedded revision
точно совпадает с application source. Publication/tag, Android CI, signed manifest,
Pages/live transports и TV runtime остаются **PENDING**.

Предыдущий опубликованный exact snapshot C-008 для application source
`4cfa7ac8ebd48b70c7b172e54a0716fec09669a1`: `dist/KinogoATV-0.5.2-code16.apk`,
38 353 630 bytes, SHA-256
`FC70D02A2BC7A3F9E5E2F04A1A7B139037AC215C85166E72E9842D0DB3CB4B38`, package
`com.kinogo.atv`, code 16 / `0.5.2`, minSdk 28, target/compile SDK 37, LEANBACK
launcher/banner, zipalign OK, v2 true, certificate SHA-256
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.

Application source вошёл первым merge `08c90c9`, tag/release — `v0.5.2`, final manifest/main
merge — `367bcf288dd5b3ad729af94d9b21308e5c96354c`. Android CI run `32598900494` и Pages run
`32598900503` завершились SUCCESS. Опубликованный `update/manifest.json` — 1 273 bytes,
SHA-256 `BCB6699708CC2C6FF4A71F8379032F709742AC714440622F179130D5AFA80E94`, issued
`2026-08-22T21:02:03Z`, expires `2026-09-21T21:02:03Z`, четыре URLs. Exact live bytes
подтверждены для Pages manifest+APK, jsDelivr manifest и ghfast/ghproxy/direct APK.
Аппаратная playback-приёмка и runtime updater на TV остаются **PENDING**; публикация C-008
не делает его playback baseline. После любого production change snapshot недействителен до
полного повторного прогона, пересборки APK и manifest.

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release build выполняйте только по [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md).

## RepoWise

RepoWise 0.45.0 используется как локальный структурный индекс и MCP-помощник для выбора
затрагиваемых файлов, рисков и тестов. Он дополняет, но не заменяет чтение исходника перед
редактированием, документацию проекта и проверки Gradle.

Первичная настройка нового checkout:

```powershell
repowise init --codex --no-prose --yes
repowise hook install
repowise doctor
```

`--no-prose` не вызывает модель, не требует API-ключ и не расходует токены. Текущая
конфигурация использует mock embedder: структурные страницы и full-text search доступны,
а semantic search потребует отдельно настроенный embedder, например локальный Ollama.

Полезная проверка перед изменением:

```powershell
repowise status
repowise context app/src/main/java/com/kinogo/atv/KinogoAppRoot.kt
repowise health
repowise hook status
```

Post-commit hook выполняет `repowise update` в фоне и пишет диагностику только в
`.repowise/.update.log`; он не должен блокировать commit. После установки другого hook
manager либо настройки `core.hooksPath` повторно выполнить `repowise hook status`, потому
что другой installer может заменить или обойти локальный `.git/hooks/post-commit`.
В этом проекте локальный `.repowise/config.yaml` содержит
`editor_files.agents_md: false`: динамический snapshot RepoWise не записывается в tracked
`AGENTS.md`, иначе каждый post-commit update оставлял бы новый commit/hash/health diff.
RepoWise-контекст Codex загружается project-local `.codex` SessionStart/MCP wiring.

После обычной перезагрузки ПК повторная инициализация не нужна: пользовательский PATH,
`.repowise/`, project-local `.codex` wiring и Git hook сохраняются. После первой установки
RepoWise или изменения пользовательского PATH нужно один раз полностью перезапустить Codex
и терминал; уже открытая задача не подхватывает новый MCP server динамически. После clone,
переноса checkout в другой путь, удаления `.repowise/` либо смены машины нужно повторить
три команды первичной настройки, поскольку абсолютные editor/MCP paths и сам индекс
намеренно не коммитятся.

Если новый процесс всё ещё не находит команду, проверить пользовательский PATH и
`repowise --version`; не зашивать путь `C:\Users\...\repowise.exe` в repository files.
Анонимная telemetry RepoWise включена upstream по умолчанию и не содержит код, пути или имя
репозитория; при желании её можно отключить локально командой `repowise telemetry disable`.

### Update manifest endpoints

Code 16 имеет два default signed-manifest transports: GitHub Pages и jsDelivr. Client
допускает максимум четыре distinct metadata endpoints всего, поэтому к двум default можно
добавить не более двух новых URL. Они зашиваются в APK Gradle property с разделителем `|`:

```powershell
.\gradlew.bat assembleRelease `
  '-PKINOGO_UPDATE_MANIFEST_URLS=https://updates.example.org/kinogo/manifest.json' `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Значение попадает в `BuildConfig.UPDATE_MANIFEST_URLS`, поэтому зашивать можно только
публичные HTTPS URL, но не secrets/tokens. Host не становится trusted: client требует
envelope signature installed APK identity, strict schema/expiry/agreement, а затем повторяет
APK size/SHA/package/version/signer checks. GitHub API остаётся последним fallback.
Pages/jsDelivr/proxy/direct дают транспортное разнообразие вокруг GitHub publication, но не
заменяют operator-owned non-GitHub endpoint для инфраструктурной независимости.
Создание и публикация manifest описаны только в
[`RELEASE_PROCESS.md`](RELEASE_PROCESS.md); development build не должен создавать его автоматически.

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
  data/auth/          credentials, HTML login/registration/CAPTCHA, cookie session
  data/catalog/       safe HTML transport, routes, parsers, repository
  data/history/       progress codec/store and legacy recovery
  data/library/       server statuses/favorite and local outbox
  data/mirror/        candidates, health, trust and persistence
  data/network/       resilient public DNS
  data/playback/      discovery, provider documents, adapters, mapping
  data/search/        bounded local recent-query history
  data/settings/      TV preferences
  data/update/        signed multi-endpoint + GitHub fallback and APK verification
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
- Не логировать Cinemar grant token, local resolver URI вместе с его registry state,
  iframe или конечный media URL; deferred leaf должен разрешаться только при открытии.
- Не логировать registration hidden fields/CAPTCHA и update download redirects.
- Не считать Pages/jsDelivr/proxy trusted по имени host; update trust даёт только
  installed-signer manifest signature и полная повторная проверка APK.
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
