# Текущее состояние проекта

Последнее обновление: **1 августа 2026 года**.

## Краткий итог

Версия `0.4.3-dev` исправляет сбои сортировки на Главной и в Каталоге. Stateful DLE/xSort
сессия использует HTTP/1.1, а неоднозначная сетевая ошибка перезапускает один раз всю
транзакцию `clear + apply`, не повторяя отдельную toggle-команду. Устаревшие reset-запросы
той же ленты отменяются, текущие карточки сохраняются при transient reset failure, а
невидимый прогрев Каталога удалён. Прямой вход в Каталог по-прежнему загружает `Новинки`.

Stable-signed APK установлен поверх пользовательской установки на KIVI 4K Android TV 14
через `adb install -r`; account/history/DataStore не очищались. Пройдены автоматическая
проверка, запуск и аппаратный прогон всех семи видов сортировки на Главной и в Каталоге.
Комбинации подборки/года/страны, длительная пагинация и player regression не завершены,
поэтому B-001 остаётся единственным полностью подтверждённым playback baseline, а новая
сборка — validation candidate C-005.

## Текущий validation candidate

| Поле | Значение |
| --- | --- |
| Candidate | **C-005 / 0.4.3-dev** |
| Application source commit | `15efacc` |
| Application ID | `com.kinogo.atv` |
| Version code | `13` |
| Version name | `0.4.3-dev` |
| Минимальная версия | Android TV 9 / API 28 |
| Compile / target SDK | 37 / 37 |
| UI | Kotlin + Jetpack Compose, landscape TV-only |
| Плеер | AndroidX Media3 / ExoPlayer |
| Подпись APK | стабильный локальный ключ, APK Signature Scheme v2 |
| Baseline tag | не создавался: полный catalog/player runtime pass pending |

Проверенный локальный APK: `dist/KinogoTV-0.4.3-dev.apk`. APK не коммитится в Git; в
`dist/SHA256SUMS.txt` хранится его контрольная сумма.

SHA-256:
`5A3EAAF4A23663AE73FE987CFDCEE6F311ED4AFD3A48B29833C44C5DAB5F67E9`.

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

Предыдущие C-004 / `0.4.2-dev` и C-003 / `0.4.1-dev` сохраняются как исторические
catalog/xSort candidates. C-002 / `0.4.0-dev` остаётся историческим UI/player candidate. Ни
один из них не получил baseline tag из-за незакрытого полного player pass.
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
| Главная | Работает; все 7 sorts прошли TV smoke | Без hero/history/title; live xSort, минимум 18 уникальных карточек при старте и ранний append |
| Каталог | Работает; все 7 sorts прошли TV smoke | Default `Новинки`, 28 allowlisted категорий, xSort dropdowns, отдельные `↑`/`↓` и append |
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

## Последняя проверка C-005

Автоматическая проверка application source commit `15efacc`:

- 68 test suites, **309 unit tests**, 0 failures, 0 errors, 0 skipped;
- Android Lint: 0 errors, 7 warnings и 2 hints;
- `assembleDebug`: успешно;
- ZIP alignment: успешно;
- v2 signature: успешно, certificate digest совпал;
- metadata: `com.kinogo.atv`, version code 13, `0.4.3-dev`, minSdk 28, targetSdk 37,
  LEANBACK launcher/banner присутствуют;
- APK SHA-256:
  `5A3EAAF4A23663AE73FE987CFDCEE6F311ED4AFD3A48B29833C44C5DAB5F67E9`.

Контролируемый smoke на KIVI 4K Android TV 14:

- `adb install -r` завершён успешно; `firstInstallTime` сохранился
  (`2026-07-26 16:42:18`), версия стала code 13 / `0.4.3-dev`;
- финальный cold launch: `Status: ok`, `LaunchState: COLD`, `TotalTime: 2504 ms`;
- на Главной и в Каталоге без ошибки загрузились все семь server sort values: дата,
  рейтинг, топ за 3 дня, просмотры, комментарии, год и рейтинг Кинопоиска;
- для рейтинга отдельно проверены направления ASC и DESC: состав/порядок выдачи изменился;
- после финального прогона в logcat нет catalog error, fatal exception или ANR.

Автоматически подтверждены дополнительные инварианты: неоднозначный timeout после
изменяющего POST перезапускает всю xSort-транзакцию от `clearallfields`, а повторный timeout
останавливается после одной полной попытки восстановления. При cancel/error applied cache
инвалидируется. Общая сетка сохраняет раннюю дозагрузку; Главная набирает минимум 18
уникальных карточек или достигает terminal pager. Скрытого Catalog warmup больше нет —
Каталог загружается при прямом входе.

Не считать этот focused smoke полным доказательством всех сетевых и playback-сценариев.
Для C-005 ещё pending:

- combinations подборки/года/страны и пустые результаты на live mirror;
- длинная Home/Catalog/Search-пагинация и смена cookie-сессии непосредственно во время
  live append;
- полный overscan/focus pass каждого раздела;
- Previous/Next и auto-next через границу сезона;
- natural end фильма/последней серии и возврат в details;
- фактический buffering overlay и white timeline marker в соответствующих состояниях.

## Текущие технические границы

- Каталог зависит от server-rendered DLE HTML и stateful xSort. POST может вернуть document
  или fragment; динамические sort/collection/year/country берутся из ответа. Сессионный
  DLE-транспорт закреплён на HTTP/1.1; playback использует отдельные клиенты.
- Категории никогда не принимаются как arbitrary href. Непустой server subset сохраняется,
  а при отсутствии sidebar используется только точный fallback из 28
  `CatalogCategory.entries`.
- xSort-сессия общая для лент и сериализована mutex. Числовой cookie-session epoch
  инвалидирует applied query после login/reconnect; ответ должен подтвердить явно выбранное
  состояние до append. Конкурирующая смена epoch и одна сетевая ошибка повторяют весь
  transaction ограниченное число раз. Отдельный xSort POST не повторяется, потому что та же
  команда может переключить направление. Неоднозначная ошибка или cancel инвалидируют
  applied query.
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

Следующий шаг — продолжительное пользовательское тестирование C-005 / `0.4.3-dev`:

- проверить category и combinations подборки/года/страны, а также длинные
  Home/Catalog/Search ленты;
- проверить overscan, плотность и возврат фокуса на всех разделах;
- повторить полный native playback regression, включая cross-season, buffering и natural
  completion;
- после подтверждения назначить новый known-good baseline/tag.

Подробная очередь — в [`ROADMAP.md`](ROADMAP.md).
