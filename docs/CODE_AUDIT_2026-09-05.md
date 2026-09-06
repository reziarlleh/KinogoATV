# Code audit C-012 — 5–6 сентября 2026

## Цель и baseline

Аудит выполнен поверх чистого `main` `a46ceb5` после опубликованного C-011 / `0.5.5`.
Rollback point для общего integration — C-011; подтверждённый playback baseline остаётся
B-001. Анализ использовал docs, agent-memory, RepoWise 0.45.0, прямой поиск исходников,
Gradle/Kotlin compiler, Android lint, R8 и разбор APK. RepoWise-сигналы принимались только
после проверки живого исходника.

## Подтверждённые изменения

- Release включил `isMinifyEnabled` и `isShrinkResources`.
- Удалены шесть неиспользуемых production dependency declarations:
  `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `ui-tooling-preview`,
  два `paging-*` и `media3-ui-compose-material3`; также удалён test-only `paging-common`.
- Удалены неподключённые capability parser/extended PlayerJS commands и compatibility wrapper
  `FavoritesScreen`.
- Удалены `HtmlCatalogPagingSource`, `FixtureCatalogPagingSource` и тесты только этого
  параллельного пути. Production уже использует ручной feed coordinator в `KinogoAppRoot`.
- Удалены неиспользуемый `SafeHtmlClient`, его fake-network tests и промежуточный
  `HtmlTransport`; общие route/body policy оставлены production session client.
- Удалён production fixture playback на внешний Big Buck Bunny MP4. Unknown content ID теперь
  не получает синтетический media plan.
- Общий cancellable OkHttp adapter заменил дублированные реализации и blocking `execute()` в
  update/mirror путях. Отмена сохраняется во время чтения response body; добавлены guards.
- Все сетевые User-Agent берут exact `BuildConfig.VERSION_NAME`.
- Обновлены стабильные Media3 1.11.0, Coil 3.6.2, Gson 2.14.0 и jsoup 1.23.2.
- Добавлены dependency verification metadata и SHA-256 Gradle 9.5 wrapper.

## Измерения размера

Опубликованный C-011 universal APK без R8: **38 419 162 bytes**, четыре DEX-файла, около
36,0 MB несжатого DEX. Exact post-commit C-012 candidate: **6 703 237 bytes**, один
DEX, экономия **31 715 925 bytes / 82,55%**, SHA-256
`2C257AEADA9C5E158A509F78F5109BFD74A597B1DAC9B84F28960ECE104FE569`. Это code 20 /
`0.6.0`, embedded revision `108519861faf67bc50dcdc574cecf38f94c00a13`. Exact R8 mapping:
64 765 967 bytes, SHA-256
`285F17906594B6F9F232E0DD099CD8BB1C2D70D8AF3C8C5A09DD765A34CD25D0`. APK и mapping
сохранены в ignored private archive `.signing/release-evidence/0.6.0-code20-1085198/`.

## Проверка

Canonical с `--write-verification-metadata sha256` завершён **SUCCESS за 23 мин 55 с**;
обычный повторный canonical завершён **SUCCESS за 1 мин 20 с**: **90 suites / 473 tests**,
0 failures/errors/skips; lint — **0 errors**, два version advisory. APK: package
`com.kinogo.atv`, code/name `20/0.6.0`, min/target 28/37, zipalign PASS, v2 true, один signer,
certificate SHA-256 `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.
R8 usage output и source не содержат удалённый fixture URL/PlayerJS capability/Paging symbols.
Exact post-commit `assembleRelease --rerun-tasks` завершён **SUCCESS за 15 мин 3 с**,
52/52 release tasks executed.

## Continuity tools

Agent-memory сохранён как observations `mem_mtop2qk9_2ead515f09f1`,
`mem_mtpb5q3n_8e02a12bd36d` и `mem_mtpby49w_20c7a0c374e2` без URL-токенов, cookies или
пользовательских данных. RepoWise 0.45.0 обновлён до exact branch HEAD: 674 SQL/vector
records, stores/FTS согласованы, coordinator drift 0%, high-confidence safe dead code — 0.
`doctor` продолжает отмечать около 30 derived pages старого renderer как stale; это известный
остаток index-only режима, а не отставание commit или рассинхронизация stores. RepoWise CLI
0.49 доступен, но глобальное tooling-обновление намеренно не смешивалось с release branch.

## Проверенные и отклонённые сигналы

- RepoWise `unused_internal` для Kotlin extension-функций оказался ложноположительным:
  вызовы через receiver подтверждены `rg` и компилятором.
- `PlaybackCheckpointWriteQueue.awaitIdle` не должен выносить lock за цикл: ожидание под lock
  заблокировало бы `enqueue`; короткие synchronized reads здесь корректны.
- Update client делает filesystem write только для выбранного успешного response, а не для
  каждого endpoint; сигнал IO-in-loop не требует изменения.
- `StartupDiagnostics` синхронно пишет маленькие crash/launch markers ради durability. Это
  осознанный correctness trade-off, не hot-frame UI loop.

## Отложенные рискованные изменения

1. Пошагово вынести state holders/use cases из `KinogoAppRoot` с characterization tests;
   не совмещать это с playback/network изменениями.
2. Разделить `TvPlayerRuntime` по ответственности только после TV characterization,
   поскольку Media3 callback order и media keys нельзя доказать одной сборкой.
3. Выполнить Compose D-pad instrumentation и API 28 emulator smoke; test APK собран на
   `junit4.v2`, но на устройстве не запускался.
4. Миграцию OkHttp 4 → 5 и Gradle 9.5 → 9.7 проводить отдельно: lint сообщает о версиях,
   но это не безопасный patch-level cleanup.
5. Перед публикацией C-012 сохранить exact R8 mapping вместе с приватными evidence, повторить
   remote CI/public transport checks и выполнить ручной TV smoke с разрешения владельца.
