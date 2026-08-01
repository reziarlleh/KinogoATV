# Текущее состояние проекта

Последнее обновление: **1 августа 2026 года**.

## Краткий итог

Версия `0.4.2-dev` развивает контракт Главной, Каталога и Поиска: общая D-pad-сетка начинает
дозагрузку, когда ниже фокуса остаётся меньше двух загруженных строк. При старте Главная
последовательно набирает минимум 18 уникальных карточек, затем прогревается невидимый
Каталог; прямой вход в Каталог запускает его загрузку сразу. До выбора другой категории
Каталог по-прежнему открывает `Новинки`.

Stable-signed APK установлен поверх пользовательской установки на KIVI 4K Android TV 14
через `adb install -r`; account/history/DataStore не очищались. Пройдены автоматическая
проверка, запуск и точечный Home/Catalog/D-pad smoke. Полный перебор фильтров, длительный
Search append и player regression не завершены, поэтому B-001 остаётся единственным
полностью подтверждённым playback baseline, а новая сборка — validation candidate C-004.

## Текущий validation candidate

| Поле | Значение |
| --- | --- |
| Candidate | **C-004 / 0.4.2-dev** |
| Application source commit | `6f5fd7a` |
| Application ID | `com.kinogo.atv` |
| Version code | `12` |
| Version name | `0.4.2-dev` |
| Минимальная версия | Android TV 9 / API 28 |
| Compile / target SDK | 37 / 37 |
| UI | Kotlin + Jetpack Compose, landscape TV-only |
| Плеер | AndroidX Media3 / ExoPlayer |
| Подпись APK | стабильный локальный ключ, APK Signature Scheme v2 |
| Baseline tag | не создавался: полный catalog/player runtime pass pending |

Проверенный локальный APK: `dist/KinogoTV-0.4.2-dev.apk`. APK не коммитится в Git; в
`dist/SHA256SUMS.txt` хранится его контрольная сумма.

SHA-256:
`1FFCD5C90F2BCC93268727ACB5D500E326A749FE6A336A8E60AE4698F595F741`.

Certificate SHA-256:
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.

Документальный commit после application source commit не меняет APK.

## Known-good baseline и откат

Текущий полностью подтверждённый playback baseline: **B-001 / 0.3.3-dev**.

- Runtime evidence: 28 июля 2026 года.
- Source baseline tag: `baseline-0.3.3-dev`.
- Локальный rollback artifact: `dist/KinogoTV-0.3.3-dev.apk`.
- Artifact SHA-256:
  `931253976140D5A76276AB4F30E7A709600CD61EABFE1FD8A36C29F38B454A77`.
- Signature certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.

Предыдущий C-003 / `0.4.1-dev` сохраняется как исторический catalog/xSort candidate с
точечным Home/Catalog/D-pad smoke. C-002 / `0.4.0-dev` остаётся историческим UI/player
candidate. Ни один из них не получил baseline tag из-за незакрытого полного player pass.
Подробные evidence и точки отката находятся в [`REGRESSION_LOG.md`](REGRESSION_LOG.md).

Rollback APK допустим только с совместимой подписью и разрешённым Android versionCode. Для
отката source использовать tag/commit, пересобрать тем же signing key и назначить новый
увеличенный versionCode; обычный downgrade Android может запретить.

## Состояние подсистем

| Подсистема | Статус | Подтверждённое поведение |
| --- | --- | --- |
| Запуск | Работает | Нативный first frame, Compose bootstrap, cold launch, crash/stall diagnostics |
| Android TV launcher | Работает | LEANBACK launcher, фирменные TV banner и ATV icon |
| Навигация | Работает | Постоянный rail, D-pad focus, подтверждение выхода |
| Главная | Работает; focused TV smoke passed | Без hero/history/title; live xSort, минимум 18 уникальных карточек при старте и ранний append |
| Каталог | Работает; focused TV smoke passed | Default `Новинки`, 28 allowlisted категорий, xSort dropdowns, отдельные `↑`/`↓` и append |
| Поиск | Работает; long append pending | Debounce 750 ms, immediate submit, keyboard hide, retry и paged query |
| Общая сетка | Работает; focused smoke passed | Шесть колонок, stable IDs, exact neighbours, no wrap, preload при остатке менее двух строк |
| Карточка | Работает | Крупный постер, полный текст, основные/status/favorite actions |
| Постеры | Работает | HTTPS-only загрузка, memory/disk cache, безопасная заглушка |
| Зеркала | Работает | Built-in/ручные кандидаты, fingerprint/redirect, TTL и active origin |
| Аккаунт | Работает | HTML-login, Keystore credentials, восстановление cookie-сессии |
| Закладки | Работает | Статусы сайта, независимое избранное, sync и локальный outbox |
| История | Работает | Snapshot, постер, сезон, серия, позиция, resume и legacy ID recovery |
| Выбор источника | Работает | Source/voice/season/episode/quality sparse-матрица до запуска |
| Нативный плеер | Работает; новый end flow pending TV | Media3, HUD, selectors, D-pad/media keys; cross-season/completion покрыты unit tests |
| Web fallback | Работает | Явный provider-only WebView с origin boundary, TV HUD и виртуальным курсором |
| Настройки | Работает | Компактные строки; значение меняется по OK, Left/Right навигируют |

## Последняя проверка C-004

Автоматическая проверка application source commit `6f5fd7a`:

- 68 test suites, **307 unit tests**, 0 failures, 0 errors, 0 skipped;
- Android Lint: 0 errors, 7 warnings и 2 hints;
- `assembleDebug`: успешно;
- ZIP alignment: успешно;
- v2 signature: успешно, certificate digest совпал;
- metadata: `com.kinogo.atv`, version code 12, `0.4.2-dev`, minSdk 28, targetSdk 37,
  LEANBACK launcher/banner присутствуют;
- APK SHA-256:
  `1FFCD5C90F2BCC93268727ACB5D500E326A749FE6A336A8E60AE4698F595F741`.

Контролируемый smoke на KIVI 4K Android TV 14:

- `adb install -r` завершён успешно; `firstInstallTime` сохранился
  (`2026-07-26 16:42:18`), версия стала code 12 / `0.4.2-dev`;
- финальный cold launch: `Status: ok`, `LaunchState: COLD`, `TotalTime: 2616 ms`;
- Главная показала 12 видимых реальных названий без loading state; два `Down`, затем пять
  `Right` достигли шестой карточки третьего ряда в соответствии с нажатиями;
- прямой вход в Каталог запустил его feed и показал 20+ карточек; выбранной по умолчанию
  осталась категория `Новинки`;
- после финальных Home/Catalog-проверок нет fatal exception, ANR или ошибок этих экранов;
- единичная ошибка mirror-health во время предварительного smoke исчезла после явной
  повторной проверки зеркал. Это внешний transient health result, а не подтверждённая
  регрессия application source.

Автоматически подтверждены дополнительные инварианты: общая сетка запрашивает следующую
страницу при остатке менее двух загруженных строк; Главная продолжает page chain до 18
уникальных карточек или terminal pager; невидимый Catalog ждёт этот резерв, после чего
прогревается, а прямой вход в Catalog не зависит от фонового прогрева.

Не считать этот focused smoke полным доказательством всех сетевых и playback-сценариев.
Для C-004 ещё pending:

- все combinations сортировки/подборки/года/страны и пустые результаты на live mirror;
- длинная пагинация поиска и смена cookie-сессии непосредственно во время live append;
- полный overscan/focus pass каждого раздела;
- Previous/Next и auto-next через границу сезона;
- natural end фильма/последней серии и возврат в details;
- фактический buffering overlay и white timeline marker в соответствующих состояниях.

## Текущие технические границы

- Каталог зависит от server-rendered DLE HTML и stateful xSort. POST может вернуть document
  или fragment; динамические sort/collection/year/country берутся из ответа.
- Категории никогда не принимаются как arbitrary href. Непустой server subset сохраняется,
  а при отсутствии sidebar используется только точный fallback из 28
  `CatalogCategory.entries`.
- xSort-сессия общая для лент и сериализована mutex. Числовой cookie-session epoch
  инвалидирует applied query после login/reconnect; ответ должен подтвердить явно выбранное
  состояние до append. Конкурирующая смена epoch повторяет весь transaction ограниченное
  число раз, не смешивая выдачи и не требуя ручного retry при обычном старте.
- `CatalogItem` хранит stable ID и relative path без домена. Home/Catalog/Search имеют
  независимые generation/query/items/nextPage и не смешивают страницы разных выдач.
- Нативные адаптеры разбирают browser-visible Cinemar/Collaps contracts. Неизвестный,
  DRM- или JavaScript-only источник нельзя маскировать под Media3.
- Web fallback остаётся явным выбором пользователя; приложение не переключается в него
  молча.
- Exact playback position хранится локально. С сайтом синхронизируются account bookmarks и
  statuses, но не Media3 checkpoint.
- Runtime проверяет встроенные и ручные зеркала и может принять безопасную конечную цель
  redirect. Интернет-wide поиск и подписанный remote manifest пока не подключены.
- CI ещё не настроен. Clean-clone unit/lint/debug требует Android SDK и JDK 17; обновление
  пользовательской установки дополнительно требует стабильный signing key вне Git.

## Активный фокус

Следующий шаг — продолжительное пользовательское тестирование C-004 / `0.4.2-dev`:

- проверить все реальные category/xSort combinations и длинные Home/Catalog/Search ленты;
- проверить overscan, плотность и возврат фокуса на всех разделах;
- повторить полный native playback regression, включая cross-season, buffering и natural
  completion;
- после подтверждения назначить новый known-good baseline/tag.

Подробная очередь — в [`ROADMAP.md`](ROADMAP.md).
