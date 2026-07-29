# Реестр регрессий и точек отката

Последнее обновление: **29 июля 2026 года**.

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
- Исправление до B-001: единый `TvPreferences.cycle`, `SettingCycleDirection`, DataStore
  codec/store и D-pad row handling.
- Уточнение 0.4.0-dev: значение меняется только по OK через
  `SettingCycleDirection.NEXT`; Left/Right больше не перехватываются строкой и остаются
  навигационными клавишами, включая выход в rail.
- Guards: `TvPreferencesTest`, `TvPreferencesStoreTest`, `TvPreferencesUiMapperTest`,
  `SettingsScreenDpadTest`.
- При повторе: отдельно проверить pure cycle, persistence и Compose key routing.

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
