# Стратегия тестирования

Последнее обновление: **1 августа 2026 года**.

## Принцип доказательств

Для KinogoATV существуют четыре разных уровня:

1. Unit/contract tests доказывают модели, parsers, reducers и политики.
2. Lint/build доказывают корректность Android-проекта и создание APK.
3. Instrumentation доказывает Android-specific API и Compose focus в контролируемой среде.
4. Реальный TV доказывает запуск, D-pad, media keys, сеть и фактическое воспроизведение.

Успешный `assembleDebug` не равен успешному просмотру фильма.

## Unit и contract tests

```powershell
.\gradlew.bat testDebugUnitTest `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Покрываются:

- catalog category/search routes, HTML/xSort parser, origin-session commands, paging/preload
  и safe GET/POST transport;
- auth/library codecs, HTML parsers и status/favorite semantics;
- mirror normalization, trust, redirect и health;
- DNS/public destination policy;
- Cinemar/Collaps/direct/gateway playback discovery;
- media plan mapping, dependent choices и cross-season episode coordinates;
- history codec, legacy resolver, resume/completion;
- TV preferences;
- player reducer, key mapper, focus retry, HUD routing и completion policy;
- UI mappers, отдельное направление сортировки и ключевые pure focus/back/grid decisions.

Live HTML не должен быть единственным тестом parser. Сначала redacted fixture, затем
необязательная read-only live-проверка.

Последний unit run для `0.4.3-dev` от 1 августа 2026 года: **68 suites,
309 unit tests**, 0 failures/errors/skipped. Полный canonical lint/build указан в
`PROJECT_STATE.md` после завершения сборки кандидата.

## Lint и сборка

```powershell
.\gradlew.bat lintDebug assembleDebug `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Текущий C-005: lint 0 errors, 7 warnings и 2 hints; debug APK успешно создан. APK прошёл
zipalign и v2 verification с ожидаемым certificate SHA-256; точный artifact hash указан в
`PROJECT_STATE.md`.

После изменения signing/build logic дополнительно проверить clean clone без `.signing`:
unit/lint/debug должны работать со стандартной debug signature, release — завершаться ясной
ошибкой.

## Instrumentation

Имеются Android tests для DNS/regex initialization и D-pad Settings UI.

### Запрет на пользовательском TV

Не запускать:

```text
connectedDebugAndroidTest
```

на телевизоре с реальным аккаунтом и историей. Managed Gradle workflow может установить,
удалить или заменить target application и стереть его данные.

### Точечный безопасный запуск

Только если Android-specific test действительно нужен:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb shell am instrument -w -r `
  -e class com.kinogo.atv.package.ExactTestClass `
  com.kinogo.atv.test/androidx.test.runner.AndroidJUnitRunner

adb shell pm uninstall com.kinogo.atv.test
```

Нельзя удалять `com.kinogo.atv`. Перед test убедиться, что main/test APK подписаны
совместимыми ключами.

## Реальный Android TV

Подключение по Ethernet/Wi-Fi ADB возможно после включения network debugging:

```powershell
adb connect <TV_IP>:5555
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`install -r` сохраняет данные только если applicationId и подпись совпадают.

### Smoke matrix

| Область | Проверка |
| --- | --- |
| Cold launch | Плитка открывает native first frame и Compose; app остаётся focused |
| Rail | Все разделы достижимы D-pad, текущий раздел подсвечен |
| Главная | Нет истории/заголовка; все 7 sorts; стартовый резерв 18 unique; ранний append без focus jump |
| Каталог | Default «Новинки»; 28 категорий; все 7 sorts/direction; direct-entry load и append |
| Поиск | Text/voice query; keyboard hide; retry и append того же encoded query |
| Details | Полное описание, status/favorite actions, Play |
| Mirrors | Check, details action, manual HTTPS input, selection |
| Account | Login, process restart, expired-session reconnect |
| Library | Status/favorite mutation, pending indicator, sync |
| History | Correct title/poster snapshot, resume exact episode and position |
| Selection | Source/voice/season/episode/quality dependencies |
| Native player | Start, pause, seek, timeline focus, selectors, subtitles |
| Remote | Simple D-pad and available media/digit keys |
| Lifecycle | Home/back/reopen; checkpoint not lost |
| Exit | Root Back asks for confirmation, default focus is Stay |
| Crash diagnostics | Controlled debug fault only on disposable data/device |

### Player regression matrix

- hidden HUD `OK` shows controls without immediate pause;
- hidden HUD Left/Right performs one seek and focuses timeline;
- repeated Left/Right continues seek after HUD composition;
- drawer Back restores invoking selector focus;
- media Play/Pause/Stop works while drawer owns Compose focus;
- season/episode change starts correct playback unit;
- source/voice/quality change preserves position only when compatible;
- episode row shows only variants compatible with selected voice;
- Previous с первой доступной серии сезона выбирает последнюю совместимую серию предыдущего
  сезона;
- Next с последней доступной серии сезона выбирает первую совместимую серию следующего
  сезона, пропуская отсутствующие координаты;
- auto-next использует тот же cross-season порядок для выбранных source/voiceover;
- естественное окончание фильма, последней доступной серии или любого эпизода при
  отключённом auto-next возвращает в details;
- exit/player error writes checkpoint without transient URL.

### Catalog/focus regression matrix 0.4.3-dev

- повторный выбор sort option не меняет направление; отдельная `↑`/`↓` меняет;
- category dropdown группирует фильмы/сериалы и восстанавливает focus trigger после
  выбора/Back;
- xSort fragment без sidebar получает все 28 allowlisted categories, а непустой server
  subset не расширяется;
- смена категории очищает page-specific browse filters и запускает новую generation;
- reset временно отключает controls предыдущей страницы до получения новой;
- Home/Catalog/Search append используют собственные query identity и next page;
- cookie-session epoch требует повторного apply после login/reconnect, а postcondition не
  допускает append при неприменённом сервером фильтре; конкурентная смена epoch проходит
  bounded automatic retry;
- timeout после mutating xSort POST не повторяет отдельную toggle-команду: applied cache
  сбрасывается, и один retry начинает полную транзакцию заново с `clearallfields`;
- второй timeout завершает запрос без бесконечного retry; cancellation также инвалидирует
  applied cache;
- общая сетка инициирует один preload для данного query-aware boundary, когда ниже фокуса
  остаётся меньше двух загруженных строк;
- append не запрашивает первый focus повторно и не отменяет in-flight move к оставшейся
  карточке;
- offscreen Up/Down прокручивает одну строку, Left/Right не выполняет wrap;
- ошибка search page имеет retry и не теряет уже загруженные карточки.
- начальная Главная последовательно получает страницы до 18 уникальных карточек либо
  terminal/non-advancing pager;
- невидимый Catalog warmup отсутствует; прямой вход в Catalog запускает его загрузку.

Pure guards присутствуют в `TvPosterGridTest`, `KinogoAppRootPreloadTest` и
`HtmlCatalogRepositoryXSortTest`. На KIVI C-005 после `install -r` сохранил
`firstInstallTime` `2026-07-26 16:42:18`; финальный cold launch занял 2504 ms. Все семь
server sorts — дата, рейтинг, топ за 3 дня, просмотры, комментарии, год и рейтинг
Кинопоиска — загрузились без ошибки на Главной и в Каталоге. Rating ASC/DESC изменил
выдачу. В финальном logcat нет catalog error, fatal exception или ANR. Combinations
подборки/года/страны и длинная Home/Catalog/Search-пагинация требуют дальнейшего TV smoke.

### Evidence для границы сезона и natural end

| Сценарий | Unit/contract evidence | Текущий C-005 |
| --- | --- | --- |
| Previous через границу сезона | `PlaybackMediaPlanTest` | Pending |
| Next через границу сезона | `PlaybackMediaPlanTest` | Pending |
| Auto-next в первую совместимую серию следующего сезона | `PlaybackCompletionPolicyTest` | Pending |
| Natural end фильма возвращает в details | `PlaybackCompletionPolicyTest` | Pending |
| Natural end последней серии возвращает в details | `PlaybackCompletionPolicyTest` | **Pending: полное окончание серии на TV ещё не дождались** |
| Отключённый auto-next возвращает в details после серии | `PlaybackCompletionPolicyTest` | Pending |

Базовый старт/seek/HUD реального playback был проверен на B-001, но это не переносится
автоматически на новый cross-season/completion flow. Для закрытия pending нужен реальный
сигнал естественного окончания Media3 (`STATE_ENDED`, automatic transition или
`PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM` по сценарию), а не ручной Back, seek почти
к концу или вызов unit policy.

## Работа с пользовательскими данными

Запрещены без явного разрешения:

```text
adb uninstall com.kinogo.atv
adb shell pm clear com.kinogo.atv
```

Не считывать целиком DataStore для диагностики. Если runtime test создал запись:

1. остановить app;
2. удалить точный key через store API внутри target process;
3. проверить только требуемые поля assertion-ами;
4. удалить временный test source;
5. удалить только `com.kinogo.atv.test`;
6. не открывать playback снова после cleanup.

## Сетевая проверка

Live domains и provider documents изменчивы:

- проверять read-only;
- не публиковать cookies/tokens;
- записывать дату и final origin;
- не называть зеркало официальным только по HTML/logo;
- отделять service outage от parser regression;
- после изменения parser создавать fixture, чтобы результат воспроизводился офлайн.

## Формат evidence

В `PROJECT_STATE.md` или handoff записывать:

```text
Дата:
Commit:
Variant/version:
Unit:
Lint:
APK hash/signature:
Устройство/Android:
Проверенные сценарии:
Непроверенные сценарии:
Изменения данных/cleanup:
```

Не переносить старый результат на новый commit без повторного запуска соответствующей
проверки.
