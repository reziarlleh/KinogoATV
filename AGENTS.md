# Инструкции для ИИ-агентов

Этот файл обязателен для любого агента, который меняет KinogoATV.

## С чего начинать

1. Прочитать [`docs/README.md`](docs/README.md).
2. Прочитать [`docs/PROJECT_STATE.md`](docs/PROJECT_STATE.md) и
   [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
3. Для интерфейсной, сетевой, авторизационной или playback-задачи открыть соответствующий
   документ из индекса `docs/README.md`. Компоновка, цвета, TV-брендинг и D-pad-фокус
   определены в [`docs/UI_DESIGN.md`](docs/UI_DESIGN.md).
4. Проверить `git status -sb` и не затрагивать посторонние пользовательские изменения.

## Обязательные инварианты

- Приложение предназначено только для Android TV и горизонтального экрана, минимум Android 9
  (API 28). Все основные действия должны быть доступны обычным D-pad-пультом без аэромыши.
- Это нативный каталог и Media3-плеер, а не WebView-оболочка сайта. Provider WebView допустим
  только как явный изолированный fallback для проверенного источника.
- Домены сервиса заменяемы. В моделях и хранилищах нельзя связывать контент с абсолютным
  доменом, если достаточно стабильного ID и относительного пути.
- Нельзя отключать HTTPS/public-DNS/SSRF-проверки ради «починки» источника или зеркала.
- Нельзя сохранять, логировать или включать в исключения transient media URL, iframe URL с
  токенами, cookies, пароль пользователя либо содержимое DataStore.
- Пароль пользователя должен сохраняться на устройстве, но только через существующий
  `AndroidKeystoreCredentialCipher`; это осознанное продуктовое требование.
- Статус «Не смотрел» означает удаление взаимоисключающего статуса из серверных закладок.
  Независимое «Избранное» при этом не меняется.
- Нельзя запускать `connectedDebugAndroidTest` на пользовательском телевизоре: Gradle managed
  install/uninstall может удалить установленное приложение и его данные. Для точечного
  instrumentation-теста использовать ручную установку test APK и удалять только пакет
  `com.kinogo.atv.test`.
- Не подключаться к реальному TV через ADB, не устанавливать APK и не выполнять аппаратный smoke
  без предварительного явного разрешения владельца на конкретный узкий сценарий. Запрашивать такую
  проверку только когда результат нельзя надёжно установить код-ревью и автоматическими тестами.
- Не выполнять `pm clear`, uninstall `com.kinogo.atv` или очистку всего DataStore без прямого
  разрешения пользователя. Тестовые записи удалять адресно через соответствующий store.
- Сборка APK не доказывает работу плеера и пульта. Изменения воспроизведения, фокуса и media
  keys требуют проверки на реальном TV либо должны быть явно отмечены как непроверенные.
- `.signing/kinogo-tv-dev.keystore` не коммитится. Потеря этого ключа лишит возможности
  обновлять уже установленное приложение.

## Документация является частью Definition of Done

При любом изменении поведения до завершения задачи обновить:

- `docs/CHANGELOG.md` — что изменилось;
- `docs/PROJECT_STATE.md` — если изменилось текущее состояние, версия или доказательства;
- `README.md` — только если изменилась доступная пользователю функция или порядок использования;
- `docs/ROADMAP.md` — если пункт реализован, отменён или переприоритизирован;
- профильный документ (`ARCHITECTURE`, `UI_DESIGN`, `SERVICE_INTEGRATION`, `PLAYBACK`,
  `SECURITY`, `TESTING`, `RELEASE_PROCESS`, `DECISIONS`) — если изменился его контракт.

Не добавлять в пользовательский `README.md` планы, отвергнутые идеи и внутренние ограничения.
Они хранятся в `docs/`.

## Документация как память и система отката

Документация должна позволять восстановить ход разработки без доступа к старому чату.

- `docs/PROJECT_STATE.md` всегда указывает текущий known-good commit/tag, APK hash, устройство
  и проверенные подсистемы.
- `docs/CHANGELOG.md` хранит хронологию изменений; исправленную проблему не удалять из
  истории.
- `docs/REGRESSION_LOG.md` хранит симптом, окружение, affected/last-known-good версии,
  первопричину, исправление и защитный тест.
- Перед рискованной переработкой создать либо записать точку отката: commit/tag и набор
  зелёных проверок. Нельзя начинать широкий рефакторинг с неизвестно сломанного baseline.
- При новом сбое сначала определить последний подтверждённо рабочий baseline и диапазон
  изменений после него, затем менять код.
- Если точная причина старого сбоя неизвестна, так и записать; не заменять пробел догадкой.
- После исправления регрессии добавить автоматическую проверку там, где это возможно, и
  связать её с записью regression log.
- В handoff обязательно указывать baseline/rollback point, изменённые подсистемы и то, какие
  проверки ещё не повторялись.

## Проверка

Минимальный набор для обычного изменения:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Перед запуском требуется JDK 17 и Android SDK. Для release либо установки поверх
пользовательской stable-signed версии дополнительно нужен отдельный signing key; чистый
clone может собирать обычный debug APK стандартным Android debug key. Полный процесс описан
в [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) и [`docs/TESTING.md`](docs/TESTING.md).

<!-- REPOWISE_AGENTS:START — Do not edit below this line. Auto-generated by Repowise. -->
## Codebase Intelligence for KinogoATV (Repowise)

Indexed by [Repowise](https://repowise.dev). Last indexed: 2026-08-25 (commit 134ae00). Confidence: 100%.
### How to work in this repo

- **Trust the index.** `verified: true` means the bytes were checked against the live tree, so never re-read those lines. Re-read only on `bounds: "approximate"`, `_meta.stale_warning`, `search_method: "bm25"` or `confidence: "low"`; `index_behind: true` alone is informational.
- **Pre-edit, not instead-of-edit.** These tools decide *which* files to read and edit. Reading a file before you edit it is correct and expected.
- **Noisy commands** (tests, builds, `git log`/`diff`, searches, listings): prefer `repowise distill <cmd>`, the same command with its exit code preserved and errors-first output. A `[repowise#<ref>: N lines omitted]` marker is recoverable via `repowise expand <ref>` (add `-q <regex>` to filter); never re-run the command to see omitted output.
- **Recording a decision** you had to reason out: `repowise decision add --title T --decision D` records it without prompting and prints the id (`--format json` to parse it back). It lands `proposed`, for a person to confirm.

### Tools

| Tool | When and why |
|------|--------------|
| `get_answer(question)` | First call for any how/where/why question. Cite `confidence: "high"` or `grounding: "extracted"` directly; `degraded` means judge by `retrieval_quality`. `symbol_bodies` has live bodies. |
| `get_context(targets=[...])` | Triage card for files/modules/symbols: docs, signatures, hotspot, fix history. No source bytes — `include=["skeleton"]` for the whole file verified, `["callers"|"decisions"]` for depth. Batch targets. |
| `get_symbol(id)` | **Follow-up, not an entry point** — one verified body for an id a prior response named (`path.py::Name`, `path.py:140-180`, `repowise#<hex>`). Never walk a file symbol by symbol; Read it. |
| `search_codebase(query)` | Hybrid search, auto-routed by query shape; force with `mode=symbol|path|concept|hybrid`. A hit whose `sources` are `[fts]` only has no semantic agreement, so verify it. |
| `get_why(query, targets?)` | Why the code is shaped this way: decision records, git archaeology, rationale comments. Call before a refactor or a pattern divergence. |
| `get_risk(targets, changed_files?)` | What history says about touching these files. PR mode (`changed_files`) leads with a `directive`: read `will_break` / `missing_cochanges` / `missing_tests` / `tests_to_run` first. |
| `get_change_risk(revspec, extensions?, exclude_patterns?)` | Defect score for a whole commit or `base..head` range, from its diff on the live checkout. Lead with `risk_percentile`. Scores a range; `get_risk` scores paths. |
| `get_health(targets?, include?)` | Defect / maintainability / performance scores and findings. Self-check the files you touched before finishing. |
| `get_dead_code()` | Confidence-tiered unreachable files / unused exports / zombie packages. For cleanup sweeps, not targeted fixes. |
| `get_overview()` | Architecture map. Call once, first, in an unfamiliar repo; skip it after that. |

### Architecture
**Files:** 246 | **Lines:** 54584 | **Import cycles:** 2
KinogoATV is a kotlin codebase of 246 files. Ranked by PageRank over the import graph: the files most of the codebase ultimately depends on. ---
*Built from the code's structure. It states what is there, not why it is that
way.

### Key modules
- `app/src/main/java/com/kinogo/atv/data` — app/src/main/java/com/kinogo/atv/data/auth · app/src/main/java/com/kinogo/atv/data/catalog ·…
- `app/src/main/java/com/kinogo/atv/domain` — app/src/main/java/com/kinogo/atv/domain
**Language:** kotlin | **Files:** 9 | **Public symbols:** 373 / 375
Covers the 9 source files in…
- `app/src/main/java/com/kinogo/atv/data/library` — app/src/main/java/com/kinogo/atv/data/library · app/src/main/java/com/kinogo/atv/data/mirror ·…
- `app/src/main/java/com/kinogo/atv/ui` — app/src/main/java/com/kinogo/atv/ui · app/src/main/java/com/kinogo/atv/ui/components · app/src/main/java/com/kinogo/atv/ui/image ·…
- `app/src/main/java/com/kinogo/atv/data/playback/collaps` — app/src/main/java/com/kinogo/atv/data/playback/collaps · app/src/main/java/com/kinogo/atv/data/settings ·…
- `app/src/main/java/com/kinogo/atv/data/catalog` — app/src/main/java/com/kinogo/atv/data/catalog · app/src/main/java/com/kinogo/atv/data/favorites ·…
- `app/src/main/java/com/kinogo/atv/data/playback` — app/src/main/java/com/kinogo/atv/data/playback · app/src/main/java/com/kinogo/atv/data/playback/cinemar
**Language:** kotlin | **Files:**…
- `app/src/main/java/com/kinogo/atv/player` — app/src/main/java/com/kinogo/atv/player
**Language:** kotlin | **Files:** 11 | **Public symbols:** 299 / 336
Covers the 11 source files in…
- `app` — app · app/src/main/java/com/kinogo/atv · app/src/main/java/com/kinogo/atv/diagnostics
**Language:** kotlin | **Files:** 12 | **Public…
- `app/src/main/java/com/kinogo/atv/ui/screens` — app/src/main/java/com/kinogo/atv/ui/screens
**Language:** kotlin | **Files:** 13 | **Public symbols:** 61 / 96
Covers the 13 source files…

### Entry points
- `app/src/main/java/com/kinogo/atv/player/web/CinemarEmbedPlayerScreen.kt`

### Files that need care (bug-fix history first, then churn — check `get_risk` before editing)
- `app/src/main/java/com/kinogo/atv/KinogoAppRoot.kt` — 1 bug fix, last fix 2 days ago; 8 commits/90d
- `app/src/main/java/com/kinogo/atv/player/ui/TvPlayerScreen.kt` — 1 bug fix, last fix 2 days ago; 5 commits/90d
- `app/src/main/java/com/kinogo/atv/ui/screens/SettingsScreen.kt` — 1 bug fix, last fix 2 days ago; 5 commits/90d
- `app/src/main/java/com/kinogo/atv/ui/screens/DetailsScreen.kt` — 1 bug fix, last fix 2 days ago; 4 commits/90d
- `app/src/main/java/com/kinogo/atv/domain/PlaybackMediaPlan.kt` — 1 bug fix, last fix 2 days ago; 4 commits/90d

### Code health
Three co-equal signals: defect risk 7.86/10 avg, hotspot health 4.83/10 (stable), worst `app/src/main/java/com/kinogo/atv/player/ui/TvPlayerScreen.kt` at 2.15/10 · maintainability 8.62/10 · performance risk 8 open static I/O-in-loop / N+1 findings. Detail: `get_health()`.

Critical files:
- `app/src/main/java/com/kinogo/atv/player/ui/TvPlayerScreen.kt` — god class (TvPlayerRuntime) — impact −1.6
- `app/src/main/java/com/kinogo/atv/ui/KinogoTvApp.kt` — large method (KinogoTvApp) — impact −1.5
- `app/src/main/java/com/kinogo/atv/KinogoAppRoot.kt` — change entropy — impact −1.3
- `app/src/main/java/com/kinogo/atv/ui/screens/HomeScreen.kt` — churn risk — impact −1.1
- `app/src/main/java/com/kinogo/atv/ui/screens/CatalogScreen.kt` — churn risk — impact −1.1

<!-- REPOWISE_AGENTS:END -->
