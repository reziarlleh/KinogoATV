# История изменений

Формат близок к Keep a Changelog, но ранние версии являются development milestones, а не
стабильными SemVer releases.

Важно: до 29 июля 2026 года локальная ветка не имела commit history. Ранние записи ниже
честно реконструированы по APK в `dist/SHA256SUMS.txt`, датам файлов, тестам и
пользовательскому циклу проверки. Это milestone history, не точный список коммитов.

## [Unreleased] — 2026-07-29

### Documentation

- Переработан корневой README в пользовательское описание только реализованных функций.
- Создан полный комплект agent/developer docs: state, architecture, service, playback,
  security, development, testing, release, decisions, regression memory, roadmap и handoff.
- Добавлен корневой `AGENTS.md` с обязательной актуализацией документации в каждой задаче.
- Исторические APK/decompiled research отделены от clean-room Markdown-выводов.

### Build and repository

- Подготовлен безопасный первый Git snapshot: расширены `.gitignore` и `.gitattributes`.
- Clean clone теперь может выполнять unit/lint/debug со стандартной Android debug signature.
- Stable signing автоматически применяется при наличии key; release без него запрещён.
- Добавлена настройка внешнего пути `KINOGO_SIGNING_STORE_FILE`.

### Validation

- Повторены 257 unit-тестов, lint (0 errors) и `assembleDebug`.
- Проверен debug build с отсутствующим stable key.
- Проверен явный отказ `packageRelease` без stable key.

## [0.3.3-dev] — 2026-07-28

### UI

- Navigation rail стал постоянно развернутым и подсвечивает текущий раздел.
- Каталожная сетка стала adaptive; исправлена ранняя подгрузка.
- Из quality badge удалены префиксы «Качество/Якість».
- В details убраны лишние заголовки и пустой выбор просмотра, описание показывается полностью.

### History

- `WatchProgress` получил snapshot карточки, чтобы search/history записи не превращались в
  числовой ID.
- Snapshot enrichment стал атомарным и не перезаписывает свежий checkpoint.
- Добавлен strict legacy resolver для numeric-only history с allowlist путей и обработкой
  terminal same-origin redirect.

### Player

- HUD уплотнён: graphical previous/play/next и selectors находятся в одной строке.
- Episode row перенесён вниз, отдельная кнопка «Серии» убрана.
- Первый OK показывает HUD без немедленной паузы.
- Hidden-HUD seek открывает HUD с timeline focus.
- Добавлен retry focus request на следующих Compose frames.
- Сезоны и серии зависят от выбранной озвучки.

### Validation

- 257 unit tests, 0 failures/errors/skipped.
- Lint 0 errors.
- Stable-signed debug APK прошёл alignment/signature/metadata check.
- Cold launch, каталог, account/library/history и реальный native playback проверены на KIVI
  Android TV 14.

SHA-256 APK:
`931253976140D5A76276AB4F30E7A709600CD61EABFE1FD8A36C29F38B454A77`.

## [0.3.0-dev – 0.3.2-dev] — 2026-07-26

- Создан единый native playback plan и предварительный TV-экран source/voice/season/episode/
  quality.
- Реализованы direct HLS/DASH/MP4, Cinemar и Collaps adapters.
- Добавлен fullscreen Media3 player с D-pad reducer, MediaSession, subtitles и resume.
- Добавлен explicit provider Web fallback с TV HUD и virtual cursor.
- Исправлены unavailable source/404 flows и fresh playback preparation.
- Проведено clean-room UX-исследование LazyMedia Deluxe; код и assets не заимствованы.

## [0.2.1-dev – 0.2.3-dev] — 2026-07-22

- Исправлен запуск приложения на реальном Android TV.
- Добавлены полноценные Android TV icon/banner и LEANBACK launcher metadata.
- Реализован нативный startup frame, crash/stall report и кнопки восстановления.
- Исправлены mirror selection/details actions и redirect handling.
- Реализована HTML-авторизация, постоянное encrypted хранение credentials и re-login.
- Реализованы server status bookmarks, independent favorite и sync/outbox.
- Настройки quality/seek/subtitles/auto-next стали сохраняемыми и D-pad-editable.
- Исследован официальный APK Kinogo и optional playback gateway.

## [0.1.0-dev – 0.2.0-dev] — 2026-07-16

- Создан Kotlin/Compose Android TV проект с minSdk 28.
- Реализованы TV shell, left navigation, home, catalog, search, details, bookmarks, history и
  settings.
- Добавлены live server-rendered catalog parser, posters и automatic page preload.
- Созданы replaceable mirror models, health/fingerprint checks, quarantine и manual input.
- Добавлены public-DNS/SSRF boundaries и safe HTML transport.
- Созданы local history/resume models и первые Media3/player contracts.

Полный список исторических artifact hashes хранится в `dist/SHA256SUMS.txt`.
