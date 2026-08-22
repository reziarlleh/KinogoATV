# Реестр регрессий и точек отката

Последнее обновление: **23 августа 2026 года**.

Назначение этого файла — служить долговременной памятью разработки. Запись не удаляется после
исправления: статус меняется на `Resolved`, добавляются fix/guard и verified baseline.

До первого Git commit точные first-bad/last-good commits отсутствовали. Для старых инцидентов
указаны milestone/date и честно отмечены неизвестные места.

## Known-good baselines

### B-001 — 0.3.3-dev

- Статус: verified rollback baseline
- Runtime-дата: 28 июля 2026 года
- Source tag: `baseline-0.3.3-dev`
- APK: `dist/KinogoTV-0.3.3-dev.apk`
- SHA-256:
  `931253976140D5A76276AB4F30E7A709600CD61EABFE1FD8A36C29F38B454A77`
- Certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`
- Automated: 257 unit tests, lint 0 errors, assembleDebug, alignment/signature metadata
- Hardware: KIVI 4K Android TV, Android TV 14
- Runtime: cold launch, rail/catalog/search data, account/library/history, real native
  playback, hidden seek → timeline focus

Это первая полноценная точка возврата. Tag указывает на первый Git commit проекта. По
отношению к аппаратно проверенному APK application source не менялся; commit дополнительно
содержит документацию, repository hygiene и clean-clone signing fallback.

## Validation candidates

### C-008 — 0.5.2 validation

- Статус: application source, local canonical tests/lint, exact stable-signed APK, CI,
  signed manifest, regular Release, Pages/jsDelivr metadata и все заявленные APK transports
  verified; hardware playback и in-app updater runtime — **PENDING**
- Metadata in source: version code 16, version `0.5.2`, minSdk 28, targetSdk 37
- Application source commit: `4cfa7ac8ebd48b70c7b172e54a0716fec09669a1`
- APK: `dist/KinogoATV-0.5.2-code16.apk`, 38 353 630 bytes, SHA-256
  `FC70D02A2BC7A3F9E5E2F04A1A7B139037AC215C85166E72E9842D0DB3CB4B38`; package
  `com.kinogo.atv`, code 16 / `0.5.2`, minSdk 28, target/compile SDK 37, LEANBACK
  launcher/banner, zipalign OK, v2 true, embedded revision `4cfa7ac`, certificate SHA-256
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`
- Source scope: watchdog recovery при зависании без Media3 error; новое поколение player
  после refresh; fresh Details и доступное Play/Continue после exit/failure;
  сериализованные generation-scoped checkpoints с zero-position activation; единая
  политика fixed-quality cap для adaptive tracks и fixed variants; configurable Media3
  buffer reserve 5/10/15/20/30 секунд с общей recovery policy; bounded in-memory preload
  только следующей episode item через границы сезонов; update controls в конце Settings;
  удаление ручной native HUD-кнопки refresh.
- Protective source evidence: `PlaybackSourceRefreshTest`, `KinogoAppRootResumeTest`,
  `PlaybackProgressCodecTest`, `PlaybackQualityPolicyTest`,
  `PlaybackBufferPolicyTest`, `PlaybackMediaPlanTest`, `PlaybackPlaylistNavigationTest`,
  `PlaybackQualitySwitchGuardTest`, `PlaybackPreloadFailurePolicyTest`,
  `PlaybackSourceSelectionModelTest`, `TvPreferencesTest`, `TvPreferencesStoreTest`,
  `TvPreferencesUiMapperTest`.
- Automated: canonical `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
  assembleRelease` — SUCCESS за 5 мин 20 с; 87 suites / 441 tests, 0 failures, 0 errors,
  0 skipped; lint 0 errors / 22 warnings / 2 hints. Post-commit
  `assembleRelease --rerun-tasks` — SUCCESS за 5 мин 29 с.
- Publication: public repository [reziarlleh/KinogoATV](https://github.com/reziarlleh/KinogoATV);
  application source вошёл первым merge `08c90c9`, tag `v0.5.2` указывает на этот validation
  release. [Regular latest Release](https://github.com/reziarlleh/KinogoATV/releases/tag/v0.5.2)
  (`draft=false`, `prerelease=false`) содержит exact
  [asset](https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.2/KinogoATV-0.5.2-code16.apk)
  `KinogoATV-0.5.2-code16.apk` и lowercase GitHub digest, совпадающий с SHA-256 выше.
- Signed update manifest: merge `367bcf288dd5b3ad729af94d9b21308e5c96354c`, 1 273 bytes,
  SHA-256 `BCB6699708CC2C6FF4A71F8379032F709742AC714440622F179130D5AFA80E94`, issued
  `2026-08-22T21:02:03Z`, expires `2026-09-21T21:02:03Z`, четыре Pages/ghfast/ghproxy/direct
  APK URL. Старый code 15 manifest до первого merge C-008 намеренно удалялся, чтобы Pages
  workflow не развернул stale payload.
- Remote evidence: Android CI run `32598900494` — SUCCESS для `367bcf2`; Pages run
  `32598900503` — SUCCESS. Exact bytes подтверждены для Pages manifest+APK, jsDelivr
  manifest и ghfast/ghproxy/direct APK. Это транспортное разнообразие вокруг GitHub asset,
  а не доказательство независимого operator-owned hosting.
- Publication level: это validation release для ручной проверки владельцем, не baseline tag
  и не hardware claim.
- Runtime: не выполнялся и не будет выполняться агентом без предварительного явного
  разрешения владельца на конкретный узкий непредсказуемый сценарий.
- Rollback: C-007 / `8b0be72` для предыдущего integration state; B-001 /
  `baseline-0.3.3-dev` для полного playback baseline.

C-008 нельзя назначать known-good baseline по green build или публикации. Все exact числа и
runtime-утверждения разделяются по фактическому evidence; данные C-007 не переносятся.

### C-007 — 0.5.1 validation

- Статус: local automated/artifact/manifest и focused KIVI native/navigation evidence passed;
  CI, publication и extended TV evidence **PENDING**
- Application source commit: `8b0be72cf32d6807f0dc4ff5c5e21da95e847874`; это
  integration rollback point для `0.5.1`, но не новый полный playback baseline
- Metadata in source: version code 15, version `0.5.1`, minSdk 28, targetSdk 37
- APK: `dist/KinogoATV-0.5.1-code15.apk`, 38 304 478 bytes, SHA-256
  `3166898FDFA882DB9A637ECDA6CDA612A5AF0B5F70D30580FD1449A906EBF875`; package
  `com.kinogo.atv`, code 15 / `0.5.1`, minSdk 28, targetSdk 37, LEANBACK launcher/label
  `KinogoATV`, zipalign OK, v2 true,
  certificate SHA-256
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`
- Source scope: lazy Cinemar `/api/playlist/load` grant resolution; true Back и
  Search query/results/focus restoration; до 10 локальных recent searches; About первым
  в Settings и на focusable rail logo; first-party PlayerJS resume с pause-before-dispose;
  signed multi-endpoint update manifest с GitHub Release API fallback.
- Protective source evidence: `CinemarNativeSourceAdapterTest`, `CinemarGrantClientTest`,
  `CinemarDeferredGrantRegistryTest`, `CinemarDeferredGrantPlaybackPlanTest`,
  `SearchHistoryStoreTest`, `KinogoTvInitialFocusTest`, `KinogoNavigationRailTest`,
  `SignedUpdateManifestParserTest`, `SignedManifestUpdateClientTest` и
  `FallbackAppUpdateClientTest`.
- Automated: final local canonical command `testDebugUnitTest lintDebug assembleDebug
  assembleDebugAndroidTest assembleRelease` — SUCCESS за 4 мин 27 с; 82 suites / 393 tests,
  0 failures, 0 errors, 0 skipped; lint 0 errors / 22 warnings / 2 hints. Exact source
  Source commit — `8b0be72`; CI — **PENDING**.
- Final local signed manifest: `update/manifest.json`, 1 273 bytes, SHA-256
  `3C167F87208077E6EC4717F202F968AD555B800C76043CFCF69B941627323070`, code 15 /
  `0.5.1`, `issuedAtEpochSeconds=1787294465`, `expiresAtEpochSeconds=1794984054`
  (18 ноября 2026 года, 06:40:54 UTC), четыре URLs и exact APK size/hash выше. Это
  локальное evidence, не live release evidence.
- Publication: signed manifest/Pages workflow подготовлены в source; Pages/jsDelivr
  metadata, exact Release asset, best-effort proxy downloads и live update ещё не подтверждены.
- Runtime: KIVI `192.168.1.112`, Android TV 14; `install -r` сохранил
  `firstInstallTime=2026-07-26 16:42:18`. Current Cinemar runtime route дал native selector
  с озвучками/сезонами 1–4/сериями, resume 10:48 и Media3 S2E5 11:01 → 11:39. `OK` открыл
  HUD без паузы; Player → Details → History прошёл. Non-first History и Search cards
  восстановили точный focus; Web resume и updater до Android OS confirmation ещё pending.
- Device-data cleanup: случайно изменённый «Spider-Man» восстановлен адресно — кнопка
  `В избранное`, а после `Не смотрел` материал отсутствует в серверном «Все» (10/10);
  broad clear/uninstall не выполнялись.
- Rollback: для интеграционного 0.5.x state — C-006 / `6567088`; для полного
  playback — B-001 / `baseline-0.3.3-dev`.

C-007 не заменяет C-006 или B-001 как точку отката, пока не зафиксированы final commit,
publication и расширенный runtime pass.

### C-006 — 0.5.0 validation

- Статус: local automated + final Release artifact/device smoke passed; CI/public release
  and extended live/player evidence pending
- Application source commit: `6567088`
- Metadata in source: version code 14, version `0.5.0`, minSdk 28, targetSdk 37
- APK: `dist/KinogoATV-0.5.0-code14.apk`, 38 140 638 bytes
- SHA-256: `3650C44B40A7AC066F98B597E0831BB800512CA5695EBD554DDD5620E15ED52B`
- Certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`;
  zipalign OK, v2 true
- Automated: `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
  assembleRelease` SUCCESS; 75 suites / 348 unit tests, 0 failures/errors/skips;
  lint 0 errors / 19 warnings / 2 hints.
  First GitHub Actions run на final commit PENDING.
- Runtime: KIVI debug smoke подтвердил cold rail focus, Settings Switch/dropdown,
  About/QR/exact links и ~14-second native playback с Back → focused
  `Продолжить с 0:14`. Registration rules D-pad instrumentation подтвердил default decline
  и explicit accept.
- Final artifact runtime: X96Max Plus Ultra Android TV 14; `adb install -r` сохранил
  `firstInstallTime` `2026-08-14 08:34:38`; installed base hash/size совпали; cold launch
  1023 ms, initial Home rail focus, catalog/posters loaded, no FATAL/ANR. Final rules test
  `OK (1)`, scroll boundary → safe decline, test package removed.
- Pending runtime: live account submit, actual expired-source refresh, natural
  cross-season end и newer-version updater/installer.
- Rollback: для catalog — C-005 / `15efacc`; для полного playback — B-001 /
  `baseline-0.3.3-dev`

C-006 нельзя назначать полным playback baseline по green build и focused smoke. Нужны public
release и ещё не закрытые live/player evidence на той же ревизии.

### C-005 — 0.4.3-dev

- Статус: automated + all-seven-sorts Home/Catalog hardware smoke passed; extended filters,
  long pagination и full player runtime pass pending
- Application source commit: `15efacc`
- APK: `dist/KinogoTV-0.4.3-dev.apk`
- SHA-256: `5A3EAAF4A23663AE73FE987CFDCEE6F311ED4AFD3A48B29833C44C5DAB5F67E9`
- Certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`
- Metadata: version code 13, minSdk 28, targetSdk 37
- Automated: 68 suites, 309 unit tests, lint 0 errors / 7 warnings / 2 hints,
  assembleDebug, zipalign и v2 signature
- Hardware: KIVI 4K Android TV 14; `install -r` сохранил `firstInstallTime`
  `2026-07-26 16:42:18`; cold launch 2504 ms; все семь server sorts загрузились без ошибки
  на Главной и в Каталоге; rating ASC/DESC изменил выдачу; финальный logcat без catalog
  error, fatal exception и ANR
- Pending: combinations подборки/года/страны, длинная Home/Catalog/Search-пагинация,
  overscan каждого раздела и полный player pass
- Rollback point: B-001 / `baseline-0.3.3-dev`

C-005 не заменяет B-001: сортировки проверены на реальном TV, но полный playback regression
для этого APK не выполнялся. C-004 остаётся историческим кандидатом ниже и содержит
регрессию R-013.

### C-004 — 0.4.2-dev

- Статус: automated + focused Home/Catalog/D-pad hardware smoke passed; extended
  catalog/search и full player runtime pass pending
- Application source commit: `6f5fd7a`
- APK: `dist/KinogoTV-0.4.2-dev.apk`
- SHA-256: `1FFCD5C90F2BCC93268727ACB5D500E326A749FE6A336A8E60AE4698F595F741`
- Certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`
- Metadata: version code 12, minSdk 28, targetSdk 37
- Automated: 68 suites, 307 unit tests, lint 0 errors / 7 warnings / 2 hints,
  assembleDebug, zipalign и v2 signature
- Hardware: KIVI 4K Android TV 14; `install -r` сохранил `firstInstallTime`
  `2026-07-26 16:42:18`; cold launch 2616 ms; Главная — 12 видимых реальных названий без
  loading, два `Down` + пять `Right` достигли шестой карточки третьего ряда; прямой вход в
  Каталог — 20+ карточек в default `Новинках`; финальные проверки без fatal/ANR и
  Home/Catalog errors
- External observation: единичная pre-smoke mirror-health ошибка исчезла после явной
  повторной проверки и не классифицирована как application regression
- Pending: полный перебор live xSort, длинный Search append, overscan каждого раздела и весь
  player pass из C-003
- Rollback point: B-001 / `baseline-0.3.3-dev`

C-004 не заменяет B-001: startup paging и текущий D-pad путь проверены на устройстве, но
полный playback regression для этого APK не выполнялся. C-003 остаётся отдельным
историческим кандидатом ниже.

### C-003 — 0.4.1-dev

- Статус: automated + focused Home/Catalog/D-pad hardware smoke passed; extended
  catalog/search и full player runtime pass pending
- Application source commit: `071300c`
- APK: `dist/KinogoTV-0.4.1-dev.apk`
- SHA-256: `ECF7BEADF8606987D19F663E352D72FCB7E1D1D30A8D3FD7A4B1476CE7A1B56B`
- Certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`
- Automated: 67 suites, 304 unit tests, lint 0 errors / 7 warnings / 2 hints,
  assembleDebug, zipalign и v2 signature
- Hardware: KIVI 4K Android TV 14; `install -r` с сохранённым `firstInstallTime`, cold
  launch/foreground, default `Новинки`, category popup/Back/long D-pad scroll, отдельное
  направление сортировки и ранний Home append без сброса фокуса
- Pending: полный перебор live xSort, длинный Search append, overscan каждого раздела и весь
  player pass из C-002
- Rollback point: B-001 / `baseline-0.3.3-dev`

C-003 не заменяет B-001: новый catalog/focus flow проверен точечно, а playback completion
flow всё ещё не прошёл естественное окончание на устройстве.

### C-002 — 0.4.0-dev

- Статус: automated + UI/D-pad hardware smoke passed; full player runtime pass pending
- Application source commit: `5a22f2a`
- APK: `dist/KinogoTV-0.4.0-dev.apk`
- SHA-256:
  `188A2CF14226C1541B2E0D5822F9CD445E09EF1E2FCE1B41483C5CC2E093EFFE`
- Certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`
- Automated: 67 suites, 281 unit test, lint 0 errors, assembleDebug, alignment/signature
- Hardware: KIVI 4K Android TV 14; `install -r`, cold launch, persisted history/checkpoint,
  shell/catalog/search/settings/details/source-selection smoke
- Pending: реальный cross-season Previous/Next/auto-next, natural Media3 end callbacks,
  buffering overlay и timeline marker
- Rollback point: B-001 / `baseline-0.3.3-dev`

C-002 нельзя переименовывать в B-002 и помечать baseline-tag до закрытия pending playback
сценариев. Документальный commit после `5a22f2a` application source не меняет.

## Инциденты

### R-001 — Нажатие плитки визуально ничего не делало

- Статус: Resolved before B-001
- Наблюдалось: 22 июля 2026 года, ранняя линия 0.2.x
- Симптом: приложение сразу исчезало/падало до видимого Compose UI.
- Последний known-good до сбоя: неизвестен, Git history отсутствовала.
- Причина: точная исходная exception не была надёжно зафиксирована; отдельной проблемой было
  отсутствие видимого pre-Compose слоя и сохраняемого crash evidence.
- Исправление: `MainActivity` получил native first frame, staged
  `StartupDiagnostics`, fatal handler, stall report и retry/export UI; launcher icon/banner
  исправлены отдельно.
- Guard: cold-launch hardware check + unit tests diagnostics codecs/formatter/classifier.
- При повторе: не начинать с сетевого parser; сначала проверить crash buffer, startup stage и
  foreground Activity.

### R-002 — Зеркало доступно на ПК, но TV не могло выбрать его; «Подробнее» не работало

- Статус: Resolved before B-001
- Наблюдалось: июль 2026 года, 0.2.x
- Симптом: built-in origins показывали error/quarantine, final redirect не становился
  отдельным рабочим candidate, TV action details не давал полезного результата.
- Причина: redirect target требовал отдельной безопасной проверки и активации; UI action/focus
  path также нуждался в TV-specific обработке.
- Исправление: `MirrorRefreshCoordinator` регистрирует safe resolved origin отдельно,
  `MirrorRegistry` применяет trust/TTL centrally, Settings использует explicit row action.
- Guards: `MirrorHealthCheckerTest`, `MirrorRegistryTest`, `MirrorRowActionTest`,
  `SettingsScreenDpadTest`.
- При повторе: сравнить origin report и resolved-origin report; не считать redirector trusted.

### R-003 — Настройки quality/seek/subtitles не менялись пультом

- Статус: Resolved before B-001
- Наблюдалось: 22 июля 2026 года
- Симптом: focus был виден, но Left/Right/OK не меняли значения либо значение не сохранялось.
- Исправление до B-001: DataStore codec/store и D-pad row handling. Текущий контракт C-008
  использует явный `set(settingId, optionId)`: boolean меняется Switch по OK, enum выбирается
  из dropdown, включая запас буфера `5/10/15/20/30 сек` (default `15`). Старые
  неиспользуемые `TvPreferences.cycle` и `SettingCycleDirection` удалены; Left/Right не
  перехватываются строкой и остаются навигационными клавишами, включая выход в rail.
- Guards: `TvPreferencesTest`, `TvPreferencesStoreTest`, `TvPreferencesUiMapperTest`,
  `SettingsScreenDpadTest`.
- При повторе: отдельно проверить explicit option mapping, persistence и Compose key routing.

### R-004 — «Нативный источник недоступен / Не удалось загрузить данные источника»

- Статус: Resolved for supported providers
- Наблюдалось: 26 июля 2026 года, переход к 0.3.x
- Симптом: карточка содержала iframe, но приложение не строило source/voice/season/episode
  matrix.
- Причина: одного generic iframe URL недостаточно для Media3; provider configurations
  различаются и могут быть ephemeral.
- Исправление: fresh preparation, `ProviderEmbedDocumentClient`, Cinemar/Collaps adapters,
  `NativePlaybackPlanMapper`, selection screen и explicit Web fallback.
- Guards: provider parser/adapter fixtures, `KinogoPlaybackPreparationServiceTest`,
  `PlaybackSourceSelectionModelTest`.
- При повторе: сохранить redacted fresh document fixture и определить provider-specific
  contract; не ослаблять network boundary.

### R-005 — Отдельный фильм отвечал player 404

- Статус: Resolved path; возможен повтор при изменении сервиса
- Пример: «Ночной бизнес», июль 2026 года
- Симптом: ранее найденный player endpoint возвращал 404.
- Причина: stale/incorrect player descriptor либо provider-specific offer; URL нельзя
  кэшировать как постоянный.
- Исправление: fresh details перед каждым play, strict title/year gateway matching и
  playback-time recovery без persisted offer.
- Guards: gateway fixtures `gateway_night_business_*`,
  `OfficialGatewayPlayerDiscoveryTest`, preparation tests.
- При повторе: различать 404 fresh detail, gateway offer и final provider document.

### R-006 — История показывала только ID 35182 без постера/названия

- Статус: Resolved in 0.3.3-dev
- Симптом: контент, открытый из поиска, после restart отображался numeric-only и не открывался.
- Причина: старый `WatchProgress` не содержал snapshot карточки, а ID сам по себе не задавал
  canonical route.
- Исправление: progress codec v2 с `CatalogItem` snapshot, atomic enrichment,
  `LegacyHistoryDetailsResolver` с strict numeric ID/content-root allowlist и terminal route.
- Guards: `PlaybackProgressCodecTest`, `LegacyHistoryDetailsResolverTest`,
  `LegacyHistoryLookupItemTest`, `PendingDetailsPosterTest`.
- Rollback warning: возврат к codec v1 снова теряет metadata новых search-only записей.

### R-007 — После seek HUD фокусировал Play/Pause вместо timeline

- Статус: Resolved in 0.3.3-dev
- Симптом: hidden-HUD Left/Right перематывал, но последующие стрелки могли навигировать по
  controls вместо продолжения seek.
- Причина: одноразовый Compose `FocusRequester.requestFocus()` возвращал `false`, пока
  timeline ещё не был подключён к focus tree.
- Исправление: `requestHudFocusWithRetry` повторяет запрос на следующих frames; visible-HUD
  root routing сохраняет быстрые повторные key events. В 0.4.0-dev тяжёлая focus-рамка
  timeline заменена белой точкой текущей позиции без изменения focus target.
- Guards: `HudFocusRequestTest`, `VisibleHudKeyRoutingTest`, `PlayerHudVisualStateTest`,
  player reducer tests.
- Hardware evidence: timeline focus подтверждён на KIVI после появления HUD.

### R-008 — Managed instrumentation затронул данные пользовательского приложения

- Статус: Process guard active
- Симптом: запуск `connectedDebugAndroidTest` мог переустановить target package и удалить
  профиль/историю.
- Причина: managed install/uninstall lifecycle Android Gradle Plugin не подходит для личного
  TV с production-like data.
- Исправление процесса: этот command запрещён в `AGENTS.md`; точечный test собирается и
  запускается вручную, затем удаляется только `com.kinogo.atv.test`.
- Guard: review checklist в `TESTING.md` и `RELEASE_PROCESS.md`.

### R-009 — Граница сезона и завершение player flow не были формализованы

- Статус: Preventive contract implemented in 0.4.0-dev; hardware verification pending
- Обнаружено: 29 июля 2026 года как явное продуктовое требование; подтверждённый старый
  runtime-сбой не зафиксирован.
- Affected baseline: B-001 подтверждает обычное воспроизведение и переходы в пределах
  проверенного сценария, но не доказывает границу сезонов или естественное окончание
  последнего материала.
- Риск: алгоритм `episode ± 1` останавливается на конце сезона, создаёт несуществующую серию
  либо оставляет пользователя на fullscreen/source screen после `STATE_ENDED`.
- Исправление: `PlaybackMediaPlan.previousEpisodeCoordinate/nextEpisodeCoordinate` обходят
  реальные sparse-координаты выбранных source/voiceover; `PlaybackCompletionPolicy`
  выполняет cross-season auto-next и возвращает в details для фильма, последней серии или
  отключённого auto-next. Автоматический переход Media3 сохраняет checkpoint завершённой
  серии до смены координат, а `PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM` отдельно
  закрывает сценарий с выключенным auto-next, где `STATE_ENDED` между элементами нет.
- Protective tests: `PlaybackMediaPlanTest`, `PlaybackCompletionPolicyTest`.
- Runtime verification: в этой записи не заявляется; требуется TV-сценарий с контентом,
  содержащим минимум два сезона, и отдельная проверка окончания фильма/последней серии.
- Rollback point: B-001 остаётся проверенной точкой возврата для основной playback-базы, но
  не содержит новый completion contract.

### R-010 — Ленты обрывались, а фокус постеров мог перескакивать

- Статус: Resolved by automated guards + focused KIVI smoke in 0.4.1-dev; extended TV
  verification pending
- Обнаружено: 1 августа 2026 года при пользовательской проверке интерфейса.
- Affected version: C-002 / `0.4.0-dev`.
- Last-known-good: для непрерывной пагинации всех трёх лент отсутствует; B-001/C-002 не
  доказывали этот полный контракт.
- Симптом: Главная, Каталог или Поиск могли закончиться на фиксированном наборе карточек;
  геометрический Compose focus search и перезапрос начального фокуса создавали визуально
  произвольные переходы при движении D-pad и append.
- Причина: экраны имели разные paging/focus paths, search не дозагружался, а focus graph не
  задавал точного соседа по шести колонкам и identity конкретной выдачи. Дополнительно xSort
  fragment мог не содержать sidebar, смена cookie-сессии делала applied-query cache
  устаревшим, а обычный append мог отменить уже начатый offscreen focus move.
- Исправление: независимые `CatalogFeedState` сохраняют query/next page; общий
  `TvPosterGrid` использует stable ID, явные индексные переходы, query-aware preload
  boundary, сохраняет in-flight move при append и прокручивает ровно одну строку. Ошибка
  append поиска получила явный retry. Empty-only category fallback использует ровно 28
  allowlisted `CatalogCategory.entries`; cookie epoch и active-filter postcondition не дают
  смешать страницы разных server states, а bounded transaction retry автоматически
  восстанавливает запрос после конкурентного login/reconnect.
- Protective tests: `HtmlCatalogRepositoryXSortTest`, `KinogoRoutesTest`,
  `KinogoHtmlParserTest`, `TvPosterGridTest`, `CatalogFilterBarLogicTest`.
- Runtime verification: на KIVI проверены category popup/Back/длинный scroll, точные
  соседние переходы и ранний Home append. Полный перебор xSort и длинный Search append ещё
  не проверены.
- Rollback point: B-001 / `baseline-0.3.3-dev`; он сохраняет основную playback-базу, но не
  новый каталог/xSort/paging contract.

### R-011 — После cold start Главная просила вручную повторить загрузку

- Статус: Resolved in C-003 / `0.4.1-dev`
- Обнаружено: 1 августа 2026 года на промежуточном stable-signed APK до source commit
  `071300c`; этот промежуточный artifact не является точкой отката.
- Симптом: после `adb install -r` и cold launch Главная показывала
  `Сессия каталога изменилась во время загрузки. Повторите запрос`, хотя сеть и зеркало были
  доступны.
- Причина: начальный xSort transaction и фоновое восстановление сохранённого аккаунта
  одновременно меняли origin-scoped cookies. Epoch guard правильно не возвращал смешанную
  страницу, но оставлял восстановление пользователю.
- Исправление: `HtmlCatalogRepository` при `CatalogSessionChangedException` обнуляет applied
  identity и ограниченно повторяет весь clear/apply/page transaction с короткой задержкой.
  Postcondition и лимит попыток сохраняются; бесконечного retry нет.
- Protective test: `HtmlCatalogRepositoryXSortTest.sessionChangeDuringAppendReappliesSelectionAutomatically`.
- Runtime verification: финальный APK SHA-256 `ECF7BEAD…A1B56B` прошёл cold launch на KIVI
  за 2958 ms; Главная загрузила реальные карточки, а в очищенном post-launch log нет
  `KinogoAppRoot` error, fatal exception или ANR.
- Rollback point: application source commit `071300c`; playback rollback по-прежнему B-001 /
  `baseline-0.3.3-dev`.

### R-012 — Начальная лента могла не успевать за D-pad, пока прогревался невидимый Каталог

- Статус: Resolved by guards + focused KIVI smoke in C-004 / `0.4.2-dev`; long-feed
  verification pending
- Обнаружено: 1 августа 2026 года при доводке ранней пагинации C-003.
- Affected version: C-003 / `0.4.1-dev`; точный first-bad commit не устанавливался, потому
  что ранний preload-контракт до C-004 не гарантировал резерв двух строк.
- Last-known-good: для startup scheduling отсутствовал; playback baseline B-001 не доказывал
  этот новый catalog contract.
- Симптом: grid начинала append только у последней загруженной строки, а Главная и невидимый
  Каталог могли стартовать параллельно. При быстром D-pad-переходе вниз пользователь мог
  догнать незавершённую страницу вместо заранее подготовленных следующих рядов.
- Причина: default `preloadRows = 1`, отсутствие минимального начального резерва Home и
  отсутствие приоритета между видимым Home и фоновым Catalog warmup.
- Исправление: общая grid запрашивает следующую страницу при остатке менее двух загруженных
  строк. Home page chain набирает минимум 18 уникальных карточек или останавливается на
  terminal/non-advancing pager, затем запускает Catalog warmup. Прямой переход в Catalog
  инициирует его собственную загрузку; default category остаётся `Новинки`.
- Protective tests: `TvPosterGridTest.default preload keeps two loaded rows below focus`,
  `KinogoAppRootPreloadTest.home preloads until three poster rows are ready`, guards
  terminal pager и deferred Catalog warmup.
- Runtime verification: C-004 на KIVI после cold launch показал 12 видимых реальных названий
  без loading; два `Down` и пять `Right` достигли шестой карточки третьего ряда. Прямой вход
  в Каталог показал 20+ `Новинок`; final checks не содержали fatal/ANR/Home/Catalog errors.
- External note: единичная ошибка mirror-health перед smoke исчезла после явной повторной
  проверки. Она не воспроизвелась и не приписывается этому application fix.
- Follow-up: C-005 удалил невидимый Catalog warmup, сохранив стартовый резерв Home и загрузку
  Каталога при прямом входе.
- Rollback point: C-003 / `071300c` возвращает предыдущий paging threshold; для playback
  rollback остаётся B-001 / `baseline-0.3.3-dev`.

### R-013 — Часть сортировок завершалась ошибкой загрузки каталога

- Статус: Resolved in C-005 / `0.4.3-dev`
- Обнаружено: 1 августа 2026 года при пользовательской проверке C-004.
- Affected version/commit: C-004 / `0.4.2-dev`, application source commit `6f5fd7a`.
- Last-known-good: отсутствовал для полного набора live sorts; C-003/C-004 проверяли только
  отдельные xSort и paging-сценарии.
- Устройство/source: KIVI 4K Android TV 14, активное live-зеркало Kinogo.
- Воспроизведение: на Главной выбрать `по рейтингу` — появлялось `Не удалось загрузить
  каталог`; в Каталоге аналогично не завершалась часть видов сортировки.
- Причина: Android-клиент согласовывал HTTP/2, и stateful xSort request иногда завершался
  `Http2Stream$StreamTimeout.takeHeaders` / `SocketTimeoutException`. Последовательность
  состоит из меняющих серверную сессию POST, поэтому простой повтор одного запроса был бы
  некорректен: повтор той же sort-команды может переключить направление. Фоновый невидимый
  Catalog warmup и неотменённый obsolete reset создавали лишние конкурирующие транзакции.
- Исправление: DLE session transport закреплён на HTTP/1.1; при одном network timeout
  репозиторий инвалидирует applied query и перезапускает всю `clear + apply` транзакцию.
  Повторная ошибка завершает запрос без бесконечного retry. Отмена также инвалидирует cache;
  obsolete same-feed reset отменяется, предыдущая видимая выдача сохраняется при transient
  reset failure, а невидимый Catalog warmup удалён. Прямой вход в Каталог сохранён.
- Protective tests:
  `HtmlCatalogRepositoryXSortTest.ambiguousPostTimeoutRestartsWholeXSortTransaction` и
  `HtmlCatalogRepositoryXSortTest.repeatedPostTimeoutStopsAfterOneWholeTransactionRetry`.
- Runtime verification: C-005 на KIVI загрузил без ошибки все семь sort values на Главной и
  в Каталоге; rating ASC/DESC дал различную выдачу. Финальный logcat не содержит catalog
  error, fatal exception или ANR.
- Fix commit: application source commit `15efacc`.
- Pre-fix bisect point: C-004 / source `6f5fd7a` (известно affected; не использовать как
  функциональный откат сортировок). Подтверждённый playback rollback остаётся B-001 /
  `baseline-0.3.3-dev`.

### R-014 — При cold start фокус уходил с rail в содержимое

- Статус: Resolved; C-006 debug TV smoke passed.
- Обнаружено: 15 августа 2026 года при ревизии TV focus contract.
- Affected: C-005 / `0.4.3-dev`; first-bad commit неизвестен.
- Last-known-good: отсутствует для требования initial rail focus; старые smoke проверяли
  доступность сетки, а не ownership первого фокуса.
- Причина: экраны Home/Catalog/Search/History/Settings независимо запрашивали initial focus
  при первой composition и могли опередить выбранный пункт navigation rail.
- Исправление: shell первым запрашивает selected rail item; content screen suppresses свой
  initial request до активации раздела пользователем.
- Protective tests: `KinogoTvInitialFocusTest`, `KinogoNavigationRailTest` и compile guard
  экранов.
- Runtime verification: cold launch C-006 на KIVI оставил видимый focus на выбранном rail
  item; расширенный обход каждого раздела остаётся в общей матрице.
- Rollback point: C-005 для catalog либо B-001 для полного playback.

### R-015 — Настройки не показывали тип выбора и могли удерживать D-pad

- Статус: Resolved; C-006 debug TV smoke passed.
- Обнаружено: 15 августа 2026 года при ревизии Settings/focus UX.
- Affected: C-005 / `0.4.3-dev`; historical related incident R-003.
- Причина: единый cycle-row скрывал множество вариантов за повторными OK и не давал
  отдельного TV-контракта возврата фокуса; слабый visual focus осложнял навигацию.
- Исправление: boolean values стали Switch, enum values — D-pad dropdown со stable option
  IDs и возвратом фокуса на trigger; Left/Right не меняют значение. Общий button/row focus
  усилен белой рамкой `3 dp` и тенью.
- Protective tests: `SettingsScreenDpadTest`, `TvPreferencesTest`,
  `TvPreferencesStoreTest`, `TvPreferencesUiMapperTest`.
- Runtime verification: Settings Switch и D-pad dropdown проверены на KIVI; расширенный
  полный обход всех значений остаётся release-smoke пунктом.
- Rollback point: C-005; не использовать откат как исправление R-003 без повторного smoke.

### R-016 — Завершённая default-серия маскировала более новую незавершённую

- Статус: source fix in C-006; real-series verification pending.
- Обнаружено: 15 августа 2026 года при characterization resume-selection.
- Affected/first-bad: точный first-bad неизвестен; affected прежняя exact/default lookup
  через History/Catalog/Search.
- Last-known-good: B-001 доказывает exact checkpoint resume, но не выбор между несколькими
  эпизодами одного сериала.
- Причина: entrypoint сначала искал checkpoint default episode и мог получить completed
  запись раньше нового unfinished episode.
- Исправление: единая `preferredResumeProgress` сначала выбирает newest активную unit
  content ID среди eligible/explicit-completed/нулевой episodic activation и только затем
  проверяет completion. Если newest unit завершена, Continue отсутствует и более старая
  unfinished серия не подставляется; Details показывает season/episode/time.
- Protective test: `KinogoAppRootResumeTest` и существующие `WatchProgressTest`.
- Runtime verification: basic same-unit checkpoint подтверждён на KIVI: после ~14 секунд
  native playback Back вернул Details с focused `Продолжить с 0:14`. Выбор между несколькими
  эпизодами реального сериала после restart остаётся **PENDING**.
- Rollback point: B-001 для базового playback; C-005 для catalog.

### R-017 — Переход в следующий сезон мог остановиться после конца серии

- Статус: source fix in C-006, bounded preload hardening in C-008; final automated и TV
  verification pending; уточняет R-009.
- Обнаружено: 15 августа 2026 года при разборе end-of-item state transition.
- Affected/first-bad: C-002–C-005 completion flow; exact first-bad неизвестен, потому что
  cross-season natural end не был аппаратно закрыт.
- Причина: pause/end signal мог завершить flow до cross-season completion policy, а
  replacement items наследовали `playWhenReady=false` завершившейся серии.
- Исправление: при включённом auto-next end pause передаётся completion policy; переход на
  первую совместимую серию следующего сезона создаётся с явным force-play. При отключённом
  auto-next возврат в Details сохранён. В C-008 все совместимые sparse coordinates выбранных
  source + voiceover разворачиваются в один ordered Media3 playlist через границы сезонов.
  `ExoPlayer.PreloadConfiguration` прогревает в памяти только непосредственно следующую item:
  2,5 с для buffer 5 с, иначе 5 с. Gate требует episodic auto-next, активное
  несупрессированное воспроизведение, remaining не больше target buffer и buffered end не
  раньше `duration - 500 ms`; pause/suppression/close/transition и backseek disarm-ят его.
  Cinemar current leaf разрешается при обычном open, immediate-next — лишь
  оппортунистически после gate; весь сезон, disk cache и отдельный resolver warmup не
  создаются. Нефатальная future load error не запускает recovery current item, а terminal
  failure учитывается только когда exact generation/index/variant стала текущей; stale
  events игнорируются.
- Protective tests: `PlaybackCompletionPolicyTest`, `PlaybackMediaPlanTest` для flattened
  cross-season coordinates, `PlaybackBufferPolicyTest` для exact gate/duration и
  `PlaybackPreloadFailurePolicyTest` для future-error isolation.
  Эти классы входят в успешный C-008 canonical pass: 87 suites / 441 tests без
  failures/errors/skips.
- Runtime verification: **PENDING** — natural end последней серии сезона и фактический
  старт следующего сезона обычным пультом.
- Rollback point: B-001 / `baseline-0.3.3-dev`.

### R-018 — Истёкший media URL оставлял playback в тупике

- Статус: source recovery in C-006; live expiry verification pending.
- Обнаружено: 15 августа 2026 года как повторяющийся failure mode transient providers.
- Affected: C-005 и более ранние native flows требовали ручного возврата в Details.
- Last-known-good: отсутствует для автоматического fresh-source recovery.
- Причина: Media3 retry повторял уже подготовленный ephemeral URL; отсутствовал bounded
  запрос полной fresh details/provider preparation.
- Исправление: один automatic refresh на stable `content/season/episode` unit с сохранением
  checkpoint, нормализацией выбора и переносом attempted set в replacement player.
  Consumed attempted-unit budget записывается в active session **до** запуска загрузки и
  disposal failing player. Recovery launch помечается `discardActivePlaybackOnExit`, поэтому
  Back, missing content или отсутствие active verified mirror очищают dead player и
  показывают явную ошибку; Back из неё возвращает в Details и не resurrect-ит старую
  Media3 session.
  Позиция применяется автоматически только при exact same-unit recovery; если свежий plan
  нормализовал другую серию, приложение возвращает selector с позицией 0. В C-008 native
  HUD больше не содержит отдельный manual refresh: после исчерпания автоматической попытки
  пользователь возвращается в Details и запускает новую fresh preparation действием
  `Смотреть`/`Продолжить`; inter-screen loop запрещён.
- Protective tests: `PlaybackSourceRefreshTest` и три pure safety guards в
  `KinogoAppRootResumeTest`: consumed attempts + discard, ordinary-launch budget unchanged,
  explicit errors для missing content/mirror. Отдельный guard запрещает применять position
  к другой серии.
- Runtime verification: **PENDING** на реальном истёкшем/404 source; URL в evidence не
  записывать.
- Rollback point: B-001 / `baseline-0.3.3-dev`.

### R-019 — DLE rules gate мог быть неявно принят, а focus — застрять в rules scroll

- Статус: Resolved in C-006; live registration submit pending.
- Обнаружено: 15 августа 2026 года при проверке реального двухшагового DLE flow.
- Affected/last-known-good: registration до C-006 отсутствовала; baseline для этого flow нет.
- Причина: сервер может сначала вернуть отдельную страницу правил, и её нельзя трактовать
  как обычную account form либо автоматически POST-ить hidden `dle_rules_accept`. Без
  explicit lower D-pad boundary focus мог остаться внутри scrollable rules surface.
- Исправление: parser выделяет `RegistrationDocument.Rules`; dialog показывает текст правил
  отдельным шагом, по умолчанию фокусирует `Не принимаю`, а accept POST выполняется только
  после явного OK. Login/e-mail/password/CAPTCHA хранятся в Compose `remember`, не
  `rememberSaveable`; bounded bitmap decode ограничивает 4096 px/8 млн pixels и downsample.
  Generation+origin guard отбрасывает late rules/form/submit responses после dismiss/retry
  или смены зеркала. Rules scroll имеет явный нижний D-pad boundary: Down возвращает focus
  на безопасное `Не принимаю`, а не оставляет его в scroll trap.
- Protective tests: `RegistrationHtmlParserTest`, `KinogoRegistrationApiTest`,
  `RegistrationDialogDpadTest.rulesGateStartsOnDeclineAndAcceptsOnlyExplicitCenterPress`.
- Runtime verification: final hardware instrumentation — `OK (1)`; default decline,
  explicit accept и scroll-boundary возврат на `Не принимаю` подтверждены, test package
  удалён. Live creation/login новой учётной записи **PENDING**.
- Rollback point: C-005 исключает registration целиком; для остальных подсистем B-001/C-005.

### R-020 — Cinemar перестал давать native source и оставлял только Web fallback

- Статус: Resolved in C-007; final local canonical pass + real KIVI native playback.
- Обнаружено: 21 августа 2026 года по пользовательскому симптому «только
  альтернативный web-плеер».
- Affected: C-006 / `0.5.0` и прежний Cinemar parser; first-bad в коде нет — сломался
  изменившийся provider contract.
- Last-known-good: C-006 ранее запускал direct Cinemar media variants, но дата
  provider switch неизвестна; полный playback rollback — B-001.
- Воспроизведение: authenticated Kinogo detail возвращает exact `cinemar.cc` player
  document на непрозрачном runtime route, а не обязательно публичный `/embed/...`. Его
  browser-visible playlist содержит leaf `{id,title,title2,data,file}`; конечный HLS
  появляется только после POST selected opaque `data` на `/api/playlist/load`.
- Первопричина: старый parser/grant использовал один `/embed/...` validator и отклонял
  текущий exact-host runtime document как `INVALID_EMBED_ADDRESS`. Предыдущая часть C-007
  уже научилась deferred leaf, но без отдельной player-document policy реальный TV flow
  всё ещё не мог открыть grant.
- Исправление: discovery неизвестного предложения остаётся strict `/embed/...`, а уже
  найденный Cinemar player document принимает отдельный `validatedPlayerDocumentUri` только
  на exact HTTPS host `cinemar.cc`: non-root, non-`/api/`, без query/fragment/userinfo и
  без нестандартного порта. Grant endpoint конструируется отдельно как фиксированный
  same-origin `/api/playlist/load`; cookies, redirect и retry запрещены, non-HLS fail-closed.
  Deferred token остаётся в session-owned registry, Media3 получает случайную local reference.
- Protective tests: `CinemarEmbedResolverTest`, `KinogoPlaybackPreparationServiceTest`,
  `CinemarNativeSourceAdapterTest`, `CinemarGrantClientTest`,
  `CinemarDeferredGrantRegistryTest`, `CinemarDeferredGrantPlaybackPlanTest` и fixture
  `movie_deferred_grant.html`.
- Runtime verification: KIVI Android TV 14, «Далеко во Вселенной»: native Cinemar selector
  показал озвучки, сезоны 1–4 и серии; resume 10:48, Media3 S2E5 продвинулся 11:01 → 11:39.
  Opaque token, player path и media URL в evidence не записаны.
- Rollback point: C-006 возвращает старый parser, но не исправляет изменившийся live
  provider; для полного playback использовать B-001 только как source baseline.

### R-021 — Back из карточки выбрасывал в Home и терял состояние Search

- Статус: Resolved in C-007; final local canonical pass + History/Search non-first TV focus.
- Обнаружено: 21 августа 2026 года по прямому замечанию пользователя.
- Affected/last-known-good: C-006 и более ранние root recreation flows; полного
  known-good для source-destination + exact Search focus не было.
- Причина: после закрытия Details shell создавался с default Home, а query/results/focused
  item частично жили внутри Search composition.
- Исправление: root хранит `currentDestination`, query/results query, feed и focused
  stable ID; `KinogoTvApp` получает фактический `initialDestination`. Добавлен bounded
  `SearchHistoryStore` на 10 запросов. History пишется только по OK/Enter, голосу или
  recent chip; dynamic debounce-строки в неё не попадают.
- Protective tests: `SearchHistoryStoreTest`, `TvPosterGridTest`,
  `KinogoTvInitialFocusTest` и source-level navigation guards.
- Runtime verification: на KIVI вторая History card «История его служанки» после
  source/details chain и Back → Details → Back → History снова имела `focused=true`.
  В Search запрос `Chris`, его результаты и ровно вторая карточка «Рождественская неделя»
  сохранились после Details → physical Back; эта карточка снова имела `focused=true`.
  Горизонтальная recent-query row также подтверждена ранее.
- Rollback point: C-006 / `6567088`; для playback — B-001.

### R-022 — GitHub-only updater не имел доступного альтернативного канала

- Статус: source fix в C-007; final local canonical pass; Pages/jsDelivr deployment и
  live updater pending.
- Обнаружено: 21 августа 2026 года; GitHub может быть недоступен в целевой сети.
- Affected: C-006 / `0.5.0`; первый updater зависел от GitHub Release API/asset.
- Last-known-good: нет для signed multi-transport update channel; C-006 сохранён как
  GitHub fallback.
- Причина: metadata и APK имели один transport/provider failure domain.
- Исправление: primary signed multi-endpoint manifest с public key installed signer,
  strict schema/expiry/agreement и несколькими signed download URLs; GitHub API остался
  fallback. APK verification и Android OS confirmation не ослаблены.
- Protective tests: `SignedUpdateManifestParserTest`, `SignedManifestUpdateClientTest`,
  `FallbackAppUpdateClientTest`, `GitHubReleaseParserTest` и `ApkUpdatePolicyTest`.
- Runtime verification: **PENDING** — Pages/jsDelivr metadata сейчас не считаются
  развёрнутыми; нужны release asset, live signature/check, каждый заявленный
  best-effort proxy download и передача Package Installer. Operator-owned non-GitHub host пока
  отсутствует.
- Rollback point: C-006 сохраняет GitHub-only updater; он не решает сетевую
  недоступность и потому не является functional fix.

### R-023 — Выход из Web fallback сбрасывал provider checkpoint

- Статус: preventive source fix в C-007; final local canonical pass; bounded WebView
  launch/Back smoke passed, actual provider resume pending.
- Обнаружено: 21 августа 2026 года при разборе PlayerJS continuation contract.
- Affected/last-known-good: C-006 Web fallback отправлял `stop`; аппаратно
  подтверждённого web-resume baseline нет.
- Причина: PlayerJS `stop` перематывает playback state, а не просто останавливает
  renderer; немедленный dispose мог прервать асинхронную команду.
- Исправление: exit command заменена на `pause`; WebView живёт до callback либо
  bounded 750 ms grace, затем `CookieManager.flush()` фиксирует cookie state internal
  WebView profile на диске. First-party cookies/DOM storage exact provider origin сохраняются
  только в изолированном WebView profile; third-party cookies запрещены. Основной
  resume остаётся штатным PlayerJS state по stable `cuid`/playlist item, а не cookie sync.
- Protective test: `CinemarWebViewRecoveryStateTest` фиксирует `Pause` как exit command.
- Runtime verification: D-pad selector достиг `Оригинальный web-плеер`
  (`Смотреть онлайн · cinemar`), fullscreen WebView открылся, Back чисто вернул Details,
  затем History. Provider playlist/position недоступны accessibility/safe logs, поэтому
  выход/повторный вход с тем же playlist item и позицией остаётся **PENDING**.
  Межустройственную/native-to-web синхронизацию smoke не доказывает.
- Rollback point: C-006 возвращает affected `stop`; для native playback откатываться к
  B-001.

### R-024 — Воспроизведение зависало без Media3 error и не восстанавливалось само

- Статус: source fix и local automated verification passed in C-008; hardware verification
  **PENDING**.
- Обнаружено: 22 августа 2026 года по пользовательскому наблюдению длительного визуального
  зависания, после которого seek либо ручное обновление источника возобновляли просмотр.
- Affected: C-007 / `0.5.1` и прежний error-only recovery path.
- Last-known-good: отсутствует для stall recovery без явного Media3 exception; B-001
  подтверждает обычный playback, но не этот failure mode.
- Воспроизведение: player мог оставаться в initial/rebuffering либо READY без продвижения
  позиции и не вызывать `onPlayerError`, поэтому существующий error callback не запускал
  fresh preparation.
- Причина: recovery был привязан к Media3 error и не имел ограниченного progress watchdog.
- Исправление: `PlaybackStallWatchdog` отслеживает только actively-playing unit и запускает
  один fresh-source request. `PlaybackBufferPolicy` связывает watchdog с выбранным запасом
  `S`: initial timeout = `max(20, S)` секунд, rebuffer timeout = `clamp(S, 5, 10)` секунд,
  READY-no-progress = 15 секунд. Pause, playback suppression и ended не считаются stall;
  near-end `READY`/`BUFFERING` без прогресса остаётся recoverable до реального `ENDED`;
  failing player при refresh останавливается, а attempted-unit budget остаётся одноразовым.
  Exact checkpoint position автоматически переносится только если fresh normalization
  сохранила тот же content/season/episode; remap открывает selector с позицией 0.
  Та же pure policy задаёт Media3 min/max target `S`, start threshold
  `clamp(target/3, 1 с, 2,5 с)`, rebuffer-start `clamp(target/2, 2 с, 5 с)` и
  `prioritizeTimeOverSizeThresholds=true`.
- Protective tests: `PlaybackSourceRefreshTest` для timeout/progress/pause/suppression/end и
  one-shot budget; `PlaybackBufferPolicyTest` для exact 5/10/15/20/30 target mapping,
  fallback 15 и recovery thresholds; preferences/store/UI mapper tests для explicit option
  и persistence. Все входят в C-008 canonical pass: 87 suites / 441 tests без
  failures/errors/skips, lint 0 errors.
- Runtime verification: **PENDING**; не инициировать искусственное зависание на TV без
  отдельного явного разрешения владельца.
- Rollback point: C-007 возвращает error-only recovery; B-001 остаётся полным playback
  baseline.

### R-025 — После выхода или failed recovery действие «Смотреть» оставалось недоступным

- Статус: source fix и local automated verification passed in C-008; hardware verification
  **PENDING**.
- Обнаружено: 22 августа 2026 года по повторному пользовательскому наблюдению: после выхода
  из player кнопку нельзя было активировать до закрытия и повторного открытия Details.
- Affected: C-007 / `0.5.1`; exact first-bad неизвестен.
- Last-known-good: C-006 short playback smoke показывал focused `Продолжить с 0:14`, но не
  охватывал failed refresh и session replacement.
- Причина: после fresh preparation root не всегда публиковал обновлённую detail model в
  открытой карточке, а structurally equal replacement plan мог сохранить прежний Media3
  instance.
- Исправление: свежая detail page обновляет live Details cache; playback session получает
  generation, входящий в identity player, поэтому refresh создаёт новый Media3 instance.
  Back/error очищает dead session и возвращает в ту же карточку с доступным
  `Смотреть`/`Продолжить`.
- Protective test: `KinogoAppRootResumeTest`, source/session generation guards и Details
  focus contract. Финальный C-008 canonical pass успешен: 87 suites / 441 tests без
  failures/errors/skips.
- Runtime verification: **PENDING**; C-006 evidence остаётся историческим и не переносится.
- Rollback point: C-007 / `8b0be72`; для полного playback — B-001.

### R-026 — Поздний checkpoint возвращал старую серию и терял новый SEEK/переход

- Статус: source fix и local automated verification passed in C-008; hardware verification
  **PENDING**.
- Обнаружено: 22 августа 2026 года по симптомам случайного продолжения с первой либо уже
  просмотренной серии и сомнениям в сохранении последней позиции.
- Affected: C-007 / `0.5.1`; race существовал в asynchronous checkpoint callbacks, точный
  first-bad неизвестен.
- Last-known-good: B-001 подтверждает одиночный exact checkpoint, но не concurrent writes,
  manual episode change или cross-season transition.
- Причина: независимые writes старого и replacement player могли завершиться не по порядку;
  новая episodic unit с позицией `0` не всегда становилась newest unfinished checkpoint, а
  manual SEEK/episode mutation мог изменить координаты до фиксации старой unit.
- Исправление: checkpoint writes сериализованы, имеют monotonic timestamp и playback-session
  generation guard; store отклоняет позднюю старую запись. Перед ручной сменой серии и
  transition фиксируется предыдущая unit, затем явно записывается activation новой unit,
  включая `positionMs=0`; history snapshot объединяется с progress store. Resume ждёт
  очередь записи и выбирает newest active exact coordinates; если newest unit завершена,
  Continue отсутствует и policy не возвращается к старой unfinished серии.
- Protective tests: `KinogoAppRootResumeTest` и `PlaybackProgressCodecTest` для ordering,
  generation, zero-position/newest-unit, ended state и merge; source transition guards для
  manual SEEK и cross-season activation. Финальный C-008 canonical pass успешен:
  87 suites / 441 tests без failures/errors/skips.
- Runtime verification: **PENDING** — multi-episode restart, manual SEEK и natural
  cross-season не проверялись на C-008.
- Rollback point: B-001 для одиночного checkpoint; C-007 для предыдущего integration state.

### R-027 — Выбранный предел качества подменялся фактическим вариантом или Auto

- Статус: source fix и local automated verification passed in C-008; hardware verification
  **PENDING**.
- Обнаружено: 22 августа 2026 года по пользовательскому сомнению, что выбранное качество
  реально применяется и сохраняется для следующих серий.
- Affected: C-007 / `0.5.1`; exact first-bad неизвестен.
- Last-known-good: отсутствует для cap policy через adaptive manifest и отдельные fixed
  media variants.
- Причина: selection смешивал пользовательское пожелание с фактически выбранным вариантом и
  мог предпочесть `Auto` до появления Media3 track list, игнорируя отдельный fixed source
  ниже заданного предела.
- Исправление: desired quality хранится отдельно от active variant. `Авто` оставляет
  adaptive selection; fixed preference выбирает точное качество, иначе максимальное
  доступное не выше предела, а при отсутствии такого — минимальное доступное выше него.
  Policy сравнивает adaptive tracks и fixed variants в одной матрице, трактует `4K` как
  2160p и переносит исходный предел на следующие серии, не заменяя его fallback-значением.
  Любая смена intent также пересчитывает fixed MediaItems будущих серий до preload, сохраняя
  exact current variant/reference, индекс, позицию и play state и инвалидируя stale generations.
- Protective tests: `PlaybackQualityPolicyTest`, `PlaybackSourceSelectionModelTest` и
  `PlaybackPlaylistNavigationTest`/`PlaybackQualitySwitchGuardTest` с
  exact/lower/only-higher, 4K, mixed adaptive+fixed, next-episode и
  E1-only-720/E2-720+1080 playlist rebuild cases. Финальный C-008 canonical pass успешен:
  87 suites / 441 tests без failures/errors/skips.
- Runtime verification: **PENDING**; без отдельного разрешения не измерять фактические
  tracks на TV.
- Rollback point: C-007 / `8b0be72`; аппаратно подтверждённого quality-cap baseline нет.

## Шаблон новой записи

```markdown
### R-NNN — Краткий симптом

- Статус: Open / Investigating / Resolved / Monitoring
- Обнаружено:
- Affected version/commit:
- Last-known-good version/commit/tag:
- First-bad version/commit:
- Устройство/Android/source:
- Воспроизведение:
- Причина:
- Исправление:
- Protective test:
- Runtime verification:
- Rollback point:
- Связанные файлы:
```

## Правило rollback

1. Найти последний baseline, где конкретная подсистема действительно проверена.
2. Сравнить commits после него, не полагаясь только на versionName.
3. Создать новый branch от baseline либо revert конкретного commit; не использовать
   `git reset --hard` поверх пользовательской работы.
4. Для Android APK назначить новый увеличенный versionCode и подписать тем же key — обычный
   downgrade может быть запрещён системой.
5. Повторить protective test и аппаратный сценарий, затем создать новый baseline/tag.
