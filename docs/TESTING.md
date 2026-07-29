# Стратегия тестирования

Последнее обновление: **29 июля 2026 года**.

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

- catalog routes, HTML parser, paging/preload и safe transport;
- auth/library codecs, HTML parsers и status/favorite semantics;
- mirror normalization, trust, redirect и health;
- DNS/public destination policy;
- Cinemar/Collaps/direct/gateway playback discovery;
- media plan mapping и dependent choices;
- history codec, legacy resolver, resume/completion;
- TV preferences;
- player reducer, key mapper, focus retry и HUD routing;
- UI mappers и ключевые pure focus/back decisions.

Live HTML не должен быть единственным тестом parser. Сначала redacted fixture, затем
необязательная read-only live-проверка.

Baseline 28 июля 2026 года: 60 suites, 257 tests, 0 failures/errors/skipped.

## Lint и сборка

```powershell
.\gradlew.bat lintDebug assembleDebug `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Baseline: lint 0 errors, 6 warnings; debug APK успешно создан.

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
| Каталог | Постеры, сортировка, разделы и preload за две строки до конца |
| Поиск | Text query; voice query при наличии system recognizer |
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
- exit/player error writes checkpoint without transient URL.

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
