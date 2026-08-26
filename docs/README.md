# Документация KinogoATV

Этот каталог — точка входа для разработчика или ИИ-агента, который впервые видит проект.
Документы разделены по назначению, чтобы не смешивать пользовательское описание, текущее
состояние, архитектурные контракты и исторические исследования.

Последняя ревизия индекса: **26 августа 2026 года**.

## Быстрый старт для нового чата

Читайте в таком порядке:

1. [`PROJECT_STATE.md`](PROJECT_STATE.md) — что работает сейчас, какая версия проверена и
   какие ограничения ещё актуальны.
2. [`ARCHITECTURE.md`](ARCHITECTURE.md) — слои, entry points, потоки данных и карта кода.
3. Профильный документ задачи:
   - компоновка, цвета, launcher assets и D-pad-навигация —
     [`UI_DESIGN.md`](UI_DESIGN.md);
   - сеть, зеркала, каталог, регистрация/авторизация и закладки —
     [`SERVICE_INTEGRATION.md`](SERVICE_INTEGRATION.md);
   - извлечение источников и плеер — [`PLAYBACK.md`](PLAYBACK.md);
   - защита данных и сетевые границы — [`SECURITY.md`](SECURITY.md);
   - локальная разработка — [`DEVELOPMENT.md`](DEVELOPMENT.md);
   - тестирование и TV-проверка — [`TESTING.md`](TESTING.md);
   - версия, подпись и выпуск APK — [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md).
4. [`DECISIONS.md`](DECISIONS.md) — решения, которые нельзя случайно «упростить».
5. [`REGRESSION_LOG.md`](REGRESSION_LOG.md) — известные поломки, причины, защитные тесты и
   точки отката.
6. [`ROADMAP.md`](ROADMAP.md) и [`CHANGELOG.md`](CHANGELOG.md) — что делать дальше и как
   проект пришёл в текущее состояние.

Корневой [`AGENTS.md`](../AGENTS.md) содержит обязательные правила работы агента и
автоматически заметнее большинству coding-сред.

## Текущий release candidate

C-009 / `0.5.3` локально проверен на exact source
`777c8a0528f24db67402536631257d6cdc91f148`. Stable-signed
`KinogoATV-0.5.3-code17.apk` имеет `38,386,398` bytes, SHA-256
`3C88DF356A9815865DB02F7821DA53BE3C6E25F03FE493516FCCAF0F48F0C17A`, package
`com.kinogo.atv`, min/target SDK `28/37`, `zipalign` **PASS**, ровно один v2 signer с certificate
SHA-256 `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`; embedded
revision совпадает с exact source. Canonical прошёл `89 suites / 455 tests` за `7m12s`,
post-commit release rerun — за `4m04s`. Tag, Release, code 17 signed manifest, CI/Pages,
live metadata/download transports и hardware runtime остаются **PENDING**. Это различие
между local candidate и опубликованным/аппаратно проверенным выпуском обязательно сохранять
во всех handoff и отчётах.

## Операционные документы

| Файл | Назначение | Когда обновлять |
| --- | --- | --- |
| [`PROJECT_STATE.md`](PROJECT_STATE.md) | Текущий проверенный снимок проекта | При изменении версии, функций или validation evidence |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Слои, зависимости, entry points и data flow | При изменении границ модулей или потока данных |
| [`UI_DESIGN.md`](UI_DESIGN.md) | Текущий визуальный контракт, launcher assets и D-pad-навигация | При изменении компоновки, цветов, фокуса или TV-брендинга |
| [`SERVICE_INTEGRATION.md`](SERVICE_INTEGRATION.md) | HTML-протокол, зеркала, registration/account и закладки | При изменении сетевого контракта или парсеров |
| [`PLAYBACK.md`](PLAYBACK.md) | Discovery, адаптеры, Media3, Web fallback и resume | При любом изменении воспроизведения |
| [`SECURITY.md`](SECURITY.md) | Секреты, SSRF, CAPTCHA, updater, signing и repository hygiene | При изменении trust boundary |
| [`DEVELOPMENT.md`](DEVELOPMENT.md) | Подготовка окружения и команды Gradle/ADB | При обновлении toolchain или процесса сборки |
| [`TESTING.md`](TESTING.md) | Автотесты, TV-матрица и безопасная работа с данными | При появлении новых проверок или устройств |
| [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md) | Версия, подпись, updater contract, упаковка и публикация | При каждом выпуске |
| [`DECISIONS.md`](DECISIONS.md) | Ключевые архитектурные и продуктовые решения | При новом решении или пересмотре старого |
| [`REGRESSION_LOG.md`](REGRESSION_LOG.md) | Симптомы, причины, исправления и rollback baselines | При каждом обнаруженном или исправленном сбое |
| [`ROADMAP.md`](ROADMAP.md) | Предстоящая работа и приоритеты | После планирования и завершения пунктов |
| [`CHANGELOG.md`](CHANGELOG.md) | История пользовательских и инженерных изменений | В каждой задаче, меняющей поведение |
| [`HANDOFF_TEMPLATE.md`](HANDOFF_TEMPLATE.md) | Шаблон передачи незавершённой работы | Перед завершением неполной сессии |

## Специализированные исследования и спецификации

Эти файлы полезны, но не являются самостоятельным доказательством текущего поведения.
Часть из них содержит целевой дизайн и исторические наблюдения. При конфликте приоритет
имеют код, тесты и `PROJECT_STATE.md`.

| Файл | Статус |
| --- | --- |
| [`AUTH_AND_SYNC.md`](AUTH_AND_SYNC.md) | Registration/login HTML-протокол, CAPTCHA, закладки и границы синхронизации |
| [`OFFICIAL_APP_RESEARCH.md`](OFFICIAL_APP_RESEARCH.md) | Clean-room исследование официального APK и закрытого JSON-шлюза |
| [`LAZYMEDIA_DELUXE_RESEARCH.md`](LAZYMEDIA_DELUXE_RESEARCH.md) | UX-референс и архитектурные выводы без заимствования кода |
| [`NATIVE_PROVIDER_ADAPTERS.md`](NATIVE_PROVIDER_ADAPTERS.md) | Спецификация и ограничения provider adapters |
| [`NATIVE_PLAYER_TV_UX.md`](NATIVE_PLAYER_TV_UX.md) | Контракт управления и целевая модель TV-плеера |
| [`renders/`](renders/) | Ранние интерфейсные рендеры; не считать точными runtime-скриншотами |

Сторонние APK, декомпилированный код и локальные research-артефакты намеренно исключены из
Git. В репозитории остаются только clean-room выводы.

## Что считается источником истины

При расхождении используйте порядок:

1. Текущее поведение кода и тестов.
2. Последняя аппаратная проверка, записанная в `PROJECT_STATE.md`.
3. Операционные документы из таблицы выше.
4. Исторические research/specification документы.
5. Предположения из старого чата.

Обнаруженное расхождение нужно исправить в документации в той же задаче, а не оставлять
«на потом».

## Политика постоянной актуализации

Документация входит в Definition of Done. Минимальная матрица:

- пользовательская функция изменилась → `README.md`, `CHANGELOG.md`, `PROJECT_STATE.md`;
- реализован пункт плана → `ROADMAP.md`, `CHANGELOG.md`, профильный документ;
- изменились модели, зависимости или data flow → `ARCHITECTURE.md`, `DECISIONS.md`;
- изменились компоновка, цвета, launcher assets или D-pad-фокус → `UI_DESIGN.md`,
  `TESTING.md`, аппаратная проверка;
- изменились HTML/JSON-парсеры, зеркала или синхронизация → `SERVICE_INTEGRATION.md`,
  `SECURITY.md`, контрактные fixtures/tests;
- изменился плеер или управление пультом → `PLAYBACK.md`, `TESTING.md`, аппаратная проверка;
- обнаружена или исправлена регрессия → `REGRESSION_LOG.md`, known-good/first-bad commit,
  protective test и rollback point;
- подготовлен APK → `RELEASE_PROCESS.md`, `PROJECT_STATE.md`, `CHANGELOG.md`,
  `dist/SHA256SUMS.txt`.

Планы, отказавшиеся идеи, внутренние риски и протокольные ограничения хранятся здесь, а не в
пользовательском `README.md`.

Аппаратная проверка в этой матрице означает отдельный уровень evidence, а не обязательное
действие каждого агента. Сначала выполняются code review и автоматические тесты. Подключаться
к реальному TV через ADB, устанавливать APK или запускать smoke разрешено только после
предварительного явного согласия владельца на конкретный узкий сценарий, результат которого
нельзя надёжно установить без устройства. Если такого разрешения не было, документ честно
фиксирует hardware evidence как `PENDING`, не заменяя его предположением.
