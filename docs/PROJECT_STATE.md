# Текущее состояние проекта

Последнее обновление: **5 сентября 2026 года**.

## Краткий итог

Текущий application source выпускает **0.5.5** (code 19), validation candidate **C-011**.
Исправлена потеря видимой позиции возле конца серии: approximate 90%-completion больше не
подавляет exact checkpoint после `Back`, а реальный end определяется только явным Media3
сигналом. Fresh source plan сохраняет season/episode независимо от provider; если completed
leaf исчезла, выбирается следующая доступная coordinate вместо S01E01.

Natural end теперь перед выходом последовательно пишет completed текущей серии и activation
следующей S/E с position 0, включая выключенный auto-next. Durable writes принадлежат
process scope `KinogoApplication`, а не lifecycle Compose host. Финальная серия без successor
не показывает ложное «Продолжить». Единый контракт действует для Details из Главной,
Каталога, Поиска, Истории, Закладок и после возврата из player.

Серверная синхронизация ограничена `STATUS` и `FAVORITE`. История и exact playback
progress остаются в локальном `PlaybackProgressStore`; account endpoint сайта для них нет.
Local canonical рабочего дерева и exact post-commit stable-signed artifact зелёные; remote CI,
regular Release и signed manifest C-011 — **PENDING**.
TV/ADB не использовались; hardware cold-restart/source-refresh resume остаётся **PENDING**.
C-010 / `0.5.4` — предыдущий published validation rollback candidate, C-007 — integration
point, B-001 — полный playback baseline.

## Текущий validation candidate

| Поле | Значение |
| --- | --- |
| Candidate | **C-011 / 0.5.5 validation** |
| Application source commit | `5223d81eefdc1b50b377cdcf74ced5174d553776` |
| Application ID | `com.kinogo.atv` |
| Version code | `19` |
| Version name | `0.5.5` |
| Минимальная версия | Android TV 9 / API 28 |
| Compile / target SDK | 37 / 37 |
| UI | Kotlin + Jetpack Compose, landscape TV-only |
| Плеер | AndroidX Media3 / ExoPlayer |
| Подпись APK | Проверено: v2 true; ровно один сертификат, SHA-256 `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9` |
| Release tag | `v0.5.5` ещё не опубликован; baseline tag не создаётся до hardware evidence |

Exact artifact C-011: `dist/KinogoATV-0.5.5-code19.apk`, **38 419 162 bytes**, SHA-256
`8A9DDDDF61DF4A7814E47B92A26B89FCBAFEFEFD6CDEB85B2203B124803E9AE9`. Package
`com.kinogo.atv`, code/name `19/0.5.5`, min/target SDK `28/37`, zipalign PASS, v2 true,
один signer; embedded revision совпадает с application source. Предыдущий exact published C-010:
`dist/KinogoATV-0.5.4-code18.apk`, **38 402 782 bytes**, SHA-256
`541941C081136854D17FB7258E92149D98F1292A56DAD02724BC1DCAA9F543AC`.

## Known-good baseline и откат

Текущий полностью подтверждённый playback baseline: **B-001 / 0.3.3-dev**.

- Runtime evidence: 28 июля 2026 года.
- Source baseline tag: `baseline-0.3.3-dev`.
- Локальный rollback artifact: `dist/KinogoTV-0.3.3-dev.apk`.
- Artifact SHA-256:
  `931253976140D5A76276AB4F30E7A709600CD61EABFE1FD8A36C29F38B454A77`.
- Signature certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.

C-007 / `0.5.1`, source `8b0be72`, сохраняется как предыдущая проверенная
интеграционная точка. Её exact APK/hash и KIVI evidence описаны в разделе
«Предыдущая проверка C-007» и не являются evidence для C-009.

Последний аппаратно проверенный catalog candidate — **C-005 / `0.4.3-dev`**, source
`15efacc`, artifact SHA-256
`5A3EAAF4A23663AE73FE987CFDCEE6F311ED4AFD3A48B29833C44C5DAB5F67E9`. Предыдущие C-004 /
`0.4.2-dev` и C-003 / `0.4.1-dev` сохраняются как исторические
catalog/xSort candidates. C-002 / `0.4.0-dev` остаётся историческим UI/player candidate. Ни
один из них не получил baseline tag из-за незакрытого полного player pass.
Подробные evidence и точки отката находятся в [`REGRESSION_LOG.md`](REGRESSION_LOG.md).

Rollback APK допустим только с совместимой подписью и разрешённым Android versionCode. Для
отката source использовать tag/commit, пересобрать тем же signing key и назначить новый
увеличенный versionCode; обычный downgrade Android может запретить.

## Состояние подсистем

| Подсистема | Статус | Реализованный контракт / evidence |
| --- | --- | --- |
| Запуск | C-011 source/build PASS; hardware **PENDING** | Startup title `KinogoATV`; TV/ADB не использовались |
| Android TV launcher | C-011 exact APK PASS; publication pending | Package/code/name/min/target, zipalign, stable signing и embedded revision проверены |
| Навигация | History/Search non-first verified | Player → Details → source destination прошёл; вторая History card и второй Search result восстановили exact focus |
| Главная | Работает; все 7 sorts прошли TV smoke | Без hero/history/title; live xSort, минимум 18 уникальных карточек при старте и ранний append |
| Каталог | Работает; все 7 sorts прошли TV smoke | Default `Новинки`, 28 allowlisted категорий, xSort dropdowns, отдельные `↑`/`↓` и append |
| Поиск | C-007 state/history + TV non-first verified | `Chris`, results и вторая карточка восстановлены после Details; recent-query row verified; long append pending |
| Общая сетка | Работает; focused smoke passed | Шесть колонок, stable IDs, exact neighbours, no wrap, preload при остатке менее двух строк |
| Карточка / resume | C-011 source/unit PASS; runtime pending | Exact near-end checkpoint, coordinate-first source remap и единая policy для Home/Catalog/Search/History/Bookmarks/player return |
| Постеры | Работает | HTTPS-only загрузка, memory/disk cache, безопасная заглушка |
| Зеркала | Existing flow verified; bootstrap live activation pending | Built-in/ручные + bounded unsigned 4-origin remote candidates; все discovery origins quarantined до health check |
| Аккаунт | Login verified; registration rules UI verified; live submit pending | Двухшаговый DLE rules gate, same-origin form/image CAPTCHA, Keystore login после success |
| Закладки | Работает | Статусы сайта, независимое избранное, sync и локальный outbox |
| История | C-011 source/unit PASS; runtime pending | Process-owned serialized checkpoints, Details-first click, long-OK delete/clear, content-level removal и codec v3 source ID |
| Выбор источника | C-011 source/unit PASS; runtime pending | Saved S/E ищется во всех свежих source/voice branches; position не переносится на другую unit |
| Нативный плеер | C-011 source/unit/build PASS; hardware **PENDING** | Near-end Back остаётся resumable; natural exit пишет completed → next activation; buffer recovery/quality policy сохранены |
| Web fallback | C-007 launch/Back smoke passed; resume pending | D-pad выбрал original Cinemar WebView, fullscreen открылся и Back вернул Details → History; actual playlist/position reopen не доказан |
| Настройки | C-010 source/unit PASS; runtime pending | Async update/mirror/account controls сохраняют focusable node; update action имеет Compose focus test |
| Обновления | C-010 Release/manifest/Pages PASS; C-011 pending | До публикации C-011 updater продолжает безопасно видеть exact code 18 manifest |
| About | C-007 placement/logo source fix; TV pending | Первая крупная Settings card и focusable rail logo; C-006 QR/external-link smoke исторический |
| CI | C-011 local PASS; remote pending | Canonical 91 suites / 476 tests, lint 0 errors; C-011 PR/main/Pages ещё не запускались |

## Проверка C-011

Canonical run рабочего дерева
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease` завершён
**SUCCESS за 7 мин 52 с**: **91 suites / 476 tests**, 0 failures/errors/skips; lint —
**0 errors / 24 warnings / 2 hints**. `assembleDebugAndroidTest` только собрал test APK;
instrumentation не запускалась.

Защитные проверки C-011: `WatchProgressTest`, `PlaybackProgressStoreTest`,
`PlaybackSourceSelectionModelTest`, `PlaybackCompletionPolicyTest` и
`KinogoAppRootResumeTest`. Они покрывают near-end exact resume, store/codec reload,
source-independent key, remap той же S/E в другой provider branch, disappeared completed
leaf, ordered completed → next S/E@0 и terminal episode без ложного Continue.

Application source `5223d81eefdc1b50b377cdcf74ced5174d553776`; exact post-commit
`assembleRelease --rerun-tasks` завершён **SUCCESS за 10 мин 28 с**, 50 tasks. Exact APK
metadata/signature/revision/size/hash приведены выше. PR/main CI, Release и signed manifest
записываются после публикации. ADB/TV не использовались;
реальное сохранение на устройстве после process restart остаётся **PENDING**.

## Проверка C-010

Canonical `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
assembleRelease` завершён **SUCCESS за 4 мин 58 с**: **91 suites / 462 tests**, 0
failures/errors/skips; lint — **0 errors / 22 warnings / 2 hints**. Exact post-commit
`assembleRelease --rerun-tasks` для `b6b2d379dad90bd33ba35725cc9d329166d365e8` завершён
**SUCCESS за 3 мин 38 с**, 50 tasks. APK metadata, min/target SDK, zipalign, v2,
one-signer certificate, SHA-256 и embedded revision проверены локально.

Защитные проверки C-010: `StartupViewsTest`, `HistoryPosterTest`,
`TvPosterGridTest`, `AppUpdateActionPresentationTest`, `SettingsScreenDpadTest`,
`PendingDetailsPosterTest` и `KinogoAppRootResumeTest`. Compose/Dialog event propagation на
конкретном OEM-пульте, native playback и in-app updater остаются **PENDING**.

### Публикация C-010

- Application/docs [PR #8](https://github.com/reziarlleh/KinogoATV/pull/8) прошёл CI
  [32970169960](https://github.com/reziarlleh/KinogoATV/actions/runs/32970169960) и вошёл merge
  `e472ca610abf7ddf762fc5f298295524c614ef95`; main Android CI
  [32970708245](https://github.com/reziarlleh/KinogoATV/actions/runs/32970708245) — SUCCESS.
- Annotated tag `v0.5.4` указывает на `e472ca610abf7ddf762fc5f298295524c614ef95`.
  [Regular latest Release](https://github.com/reziarlleh/KinogoATV/releases/tag/v0.5.4)
  опубликован `2026-08-26T12:50:57Z`, `draft=false`, `prerelease=false`.
- Exact [APK asset](https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.4/KinogoATV-0.5.4-code18.apk)
  — 38 402 782 bytes; GitHub digest точно
  `sha256:541941c081136854d17fb7258e92149d98f1292a56dad02724bc1dcaa9f543ac`.
- Signed manifest source `8e0a8aa66c90e7fe745d5e7ad4fb3d0d5371bc20`,
  [PR #9](https://github.com/reziarlleh/KinogoATV/pull/9), merge
  `b5697408482a59f8bc6e4855508345d05667ef0a`. PR CI
  [32971109889](https://github.com/reziarlleh/KinogoATV/actions/runs/32971109889) — SUCCESS.
- Final signed code 18 `update/manifest.json`: 1 273 bytes, SHA-256
  `CD947D90D92E54727111C1FF2EABC77BA9D7A93F6DCA7815035E4A194FC82EBE`, issued
  `2026-08-26T12:52:04Z`, expires `2026-09-25T12:52:04Z`. Final main Android CI
  [32971237800](https://github.com/reziarlleh/KinogoATV/actions/runs/32971237800) и Pages
  [32971237767](https://github.com/reziarlleh/KinogoATV/actions/runs/32971237767) — SUCCESS.
- Live exact bytes: Pages manifest+APK и ghfast APK — PASS. ghproxy/direct GitHub HEAD
  вернули HTTP 200 и exact Content-Length; GitHub Release digest подтверждает direct asset.
  Full ghproxy GET не завершился за 90 секунд. jsDelivr `@main` после targeted purge всё ещё
  отдавал signed code 17; это безопасный stale fallback, пока primary Pages уже отдаёт exact
  signed code 18. Все эти пути зависят от GitHub assets и не являются operator-owned host.
- Baseline tag не создавался: hardware/TV/ADB, OEM long-OK, Settings focus, real resume/player
  и in-app installer runtime остаются **PENDING**.

## Проверка C-009

Canonical `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
assembleRelease` завершён **SUCCESS за 7 мин 12 с**: **89 suites / 455 tests**, 0
failures/errors/skips; lint — **0 errors / 22 warnings / 2 hints**. Проверяются
backward-compatible codec v1/v2/v3, source round-trip,
content-level delete, сохранность unrelated preferences, checkpoint/delete ordering,
History focus/long-press reducer и automatic update policy. После source commit
`777c8a0528f24db67402536631257d6cdc91f148` exact `assembleRelease --rerun-tasks`
завершён **SUCCESS за 4 мин 04 с**; получен и проверен APK с метаданными выше.

По прямому указанию владельца ADB, установка на KIVI/X96MAX и hardware smoke не
выполнялись. Работа D-pad long press на конкретном OEM, реальный resume после process
restart и startup update dialog остаются **PENDING**.

### Публикация C-009

- Application source: `777c8a0528f24db67402536631257d6cdc91f148`.
- Application/docs [PR #5](https://github.com/reziarlleh/KinogoATV/pull/5) прошёл CI
  [32920452170](https://github.com/reziarlleh/KinogoATV/actions/runs/32920452170) и вошёл merge
  `0473a820eefedea16ce2f393df568c90e5b30bbe`; main Android CI
  [32920746857](https://github.com/reziarlleh/KinogoATV/actions/runs/32920746857) — SUCCESS за 4 мин 22 с.
- Annotated tag `v0.5.3` указывает на `0473a820eefedea16ce2f393df568c90e5b30bbe`.
  [Regular latest Release](https://github.com/reziarlleh/KinogoATV/releases/tag/v0.5.3)
  опубликован `2026-08-26T01:59:56Z`, `draft=false`, `prerelease=false`.
- Exact [APK asset](https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.3/KinogoATV-0.5.3-code17.apk)
  — 38 386 398 bytes; GitHub digest точно
  `sha256:3c88df356a9815865db02f7821da53be3c6e25f03fe493516fccaf0f48f0c17a`.
- Signed manifest source `7faebbba8d305a0c339f6966e7759ec7c7f96b90`,
  [PR #6](https://github.com/reziarlleh/KinogoATV/pull/6), merge
  `ff7f5f8eea9776ef626010fe57993dc1906f5d4a`. PR CI
  [32921520976](https://github.com/reziarlleh/KinogoATV/actions/runs/32921520976) — SUCCESS.
- Final signed code 17 `update/manifest.json`: 1 273 bytes, SHA-256
  `860D90C22D9F404A38E783BD313A9E9A0FDEFC5BC870F933A819D35145489977`, issued
  `2026-08-26T02:04:54Z`, expires `2026-09-25T02:04:54Z`. Final main Android CI
  [32921627748](https://github.com/reziarlleh/KinogoATV/actions/runs/32921627748) — SUCCESS за 1 мин 04 с;
  Pages [32921627746](https://github.com/reziarlleh/KinogoATV/actions/runs/32921627746) —
  SUCCESS за 45 с.
- Live exact bytes: Pages manifest+APK PASS; jsDelivr manifest PASS после точечной
  purge; ghfast, ghproxy и direct GitHub APK совпали по size/SHA-256. Это
  transport diversity, а не независимая от GitHub инфраструктура.
- Baseline tag не создавался: hardware/TV/ADB, long-OK, restart-resume и
  in-app installer runtime остаются **PENDING**.

## Предыдущая проверка C-008

Для application source
`4cfa7ac8ebd48b70c7b172e54a0716fec09669a1` canonical команда
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease`
завершена **SUCCESS за 5 мин 20 с**: **87 suites / 441 tests**, 0 failures, 0 errors,
0 skipped; lint — **0 errors / 22 warnings / 2 hints**. После commit выполнен независимый
`assembleRelease --rerun-tasks` — **SUCCESS за 5 мин 29 с**. Exact APK и его
metadata/alignment/signature/hash приведены в `TESTING.md` и `RELEASE_PROCESS.md`. CI,
PR/merge, Release, final signed
manifest и publication evidence приведены ниже. Hardware playback и in-app updater runtime
остаются **PENDING**.

### Публикация C-008

- Public repository: [reziarlleh/KinogoATV](https://github.com/reziarlleh/KinogoATV).
- Application/docs merge: `08c90c9`; application source внутри APK — exact
  `4cfa7ac8ebd48b70c7b172e54a0716fec09669a1`.
- Tag `v0.5.2`; regular latest Release с `draft=false`, `prerelease=false`:
  [KinogoATV 0.5.2](https://github.com/reziarlleh/KinogoATV/releases/tag/v0.5.2).
- Exact asset:
  [KinogoATV-0.5.2-code16.apk](https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.2/KinogoATV-0.5.2-code16.apk),
  38 353 630 bytes, SHA-256 из таблицы выше; GitHub asset digest совпал побайтно.
- Signed `update/manifest.json`: 1 273 bytes, SHA-256
  `BCB6699708CC2C6FF4A71F8379032F709742AC714440622F179130D5AFA80E94`, issued
  `2026-08-22T21:02:03Z`, expires `2026-09-21T21:02:03Z`, четыре URLs: Pages, ghfast,
  ghproxy и direct GitHub. Manifest/main merge —
  `367bcf288dd5b3ad729af94d9b21308e5c96354c`.
- Android CI [run 32598900494](https://github.com/reziarlleh/KinogoATV/actions/runs/32598900494)
  — SUCCESS, `2026-08-22T21:12:08Z`–`21:13:28Z`; Pages
  [run 32598900503](https://github.com/reziarlleh/KinogoATV/actions/runs/32598900503) —
  SUCCESS, `21:12:09Z`–`21:12:57Z`, оба на `367bcf2`.
- Live exact bytes подтверждены для Pages manifest+APK и jsDelivr manifest; APK через
  ghfast, ghproxy и direct GitHub совпал с exact size/SHA-256. Это transport diversity,
  а не независимая от GitHub инфраструктура; operator-owned host остаётся **PENDING**.

По решению владельца агент не подключался к TV по ADB, не устанавливал C-008
и не выполнял hardware smoke. Автовосстановление playback, правила качества,
resume и updater runtime остаются **PENDING** для ручной приёмки владельцем. Любая
агентская hardware-проверка требует нового явного разрешения на узкий сценарий.

## Предыдущая проверка C-007

Для application source `8b0be72` canonical команда
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease`
завершена **SUCCESS за 4 мин 27 с**: **82 suites / 393 tests**, 0 failures, 0 errors,
0 skipped; lint — **0 errors / 22 warnings / 2 hints**. Exact release APK и final signed
manifest проверены локально с указанными выше size/hash/metadata/signature.

Аппаратный C-007 pass выполнен на KIVI `192.168.1.112`, Android TV 14. `adb install -r`
сохранил `firstInstallTime=2026-07-26 16:42:18`. Из Истории материал «Далеко во Вселенной»
открыл native selector Cinemar с озвучками, сезонами 1–4 и сериями; resume был 10:48.
Media3 запустил S2E5, позиция продвинулась 11:01 → 11:39, то есть воспроизведение шло
более 15 секунд. `OK` показал HUD без немедленной паузы; Back вернул Player → Details →
History. Отдельный non-first test вернул вторую карточку «История его служанки» с
`focused=true` после source/details chain и двойного Back.

Web fallback bounded smoke дошёл D-pad-пультом до `Оригинальный web-плеер`
(`Смотреть онлайн · cinemar`), открыл fullscreen WebView и чисто вернулся Back → Details →
History. Provider playlist/position недоступны accessibility и безопасным логам, поэтому
фактический PlayerJS resume после повторного открытия остаётся **PENDING**.

Случайно изменённое при проверке состояние «Spider-Man» восстановлено адресно: кнопка снова
`В избранное`, а после `Не смотрел` материал отсутствует в серверном разделе «Все» (10/10).
Broad clear/uninstall не выполнялись. Search `Chris`/results и второй result
«Рождественская неделя» после physical Back восстановлены с `focused=true`; recent-query row
также подтверждена.

CI, GitHub Release/Pages/jsDelivr publication и live updater остаются **PENDING**. До их
закрытия не считать default Pages/jsDelivr URLs живыми каналами.

## Предыдущая проверка C-006 и final artifact

На application source C-006 / commit `6567088` была успешно завершена команда с задачами:

- `testDebugUnitTest`;
- `lintDebug`;
- `assembleDebug`;
- `assembleDebugAndroidTest`;
- `assembleRelease`.

Результат: **75 suites / 348 unit tests**, 0 failures, 0 errors, 0 skipped; Android Lint —
0 errors, 19 warnings и 2 hints. Application source и этот локальный evidence зафиксированы
commit `6567088`; Debug, AndroidTest и Release APK успешно собраны.

Финальный Release artifact прошёл metadata/size/hash/zipalign/v2/certificate verification:
`dist/KinogoATV-0.5.0-code14.apk`, 38 140 638 bytes,
SHA-256 `3650C44B40A7AC066F98B597E0831BB800512CA5695EBD554DDD5620E15ED52B`.

Контролируемый debug smoke на KIVI 4K Android TV 14 подтвердил:

- cold launch оставляет focus на выбранном rail item;
- Settings Switch и D-pad dropdown работают;
- About полностью достижим D-pad, QR/dialog видимы, Donate.Stream и GitHub открываются в
  Yandex TV browser;
- путь Home → Details → source selector → native playback (~14 секунд) → Back вернул
  Details с focused действием `Продолжить с 0:14`;
- `RegistrationDialogDpadTest` на устройстве подтвердил безопасный первый фокус на
  `Не принимаю`; rules POST возможен только после явного выбора `Принимаю и продолжить`.

Не проверены: live submit новой учётной записи, реальная ошибка/expiry источника и
automatic refresh, natural end с переходом через сезон, newer-version download/Android
installer.

Финальный Release APK установлен через `adb install -r` на
X96Max Plus Ultra (`10.173.44.46`), Android TV 14:

- `firstInstallTime` сохранился: `2026-08-14 08:34:38`;
- installed base APK имеет точные artifact size/hash;
- cold launch: `Status: ok`, `TotalTime: 1023 ms`;
- первый focus — Home в rail; каталог и постеры загрузились;
- в итоговом logcat нет FATAL/ANR.

На этом устройстве final `RegistrationDialogDpadTest` прошёл: `OK (1)`. Кроме default
decline/explicit accept он подтвердил, что Down на нижней границе rules scroll явно
возвращает focus на безопасное `Не принимаю`. Test package после проверки удалён.

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

### Локальный индекс RepoWise

25 августа 2026 года checkout `D:\_codex\KinogoATV` проиндексирован RepoWise 0.45.0 командой
`repowise init --codex --no-prose --yes`. Начальный снимок application/docs commit
`134ae00` дал 246 файлов, 4 700 символов и 524 структурные страницы без provider/model.
`repowise doctor` завершился `All checks passed`, `status` не показал stale pages или
SQL/vector/FTS drift, а реальный `context` для `KinogoAppRoot.kt` вернул структурную карточку.
Контрольный commit подтвердил реальный фоновый post-commit update до нового HEAD; точные
текущие commit/размер/health всегда запрашиваются через `repowise status`, а не дублируются
как быстро устаревающий tracked snapshot.

`.repowise/`, `.mcp.json`, `.claude/`, локальные `.vscode` RepoWise-файлы и уже игнорируемая
`.codex/` являются машинно-зависимыми: часть конфигураций содержит абсолютный путь checkout,
а индекс состоит из производных SQLite/LanceDB/cache данных. В Git сохраняются только общая
политика игнорирования и документация. Автогенерация managed-блока `AGENTS.md` локально
отключена (`editor_files.agents_md: false`), потому что post-commit snapshot иначе оставляет
tracked-файл грязным после каждого commit. Исходный обязательный `AGENTS.md` сохранён.
Локальный `.git/hooks/post-commit` обновляет индекс в фоне, но не является tracked-файлом.

Динамические health scores и static findings RepoWise — только сигнал для выбора области
дальнейшего аудита. Они не считаются подтверждёнными дефектами, не меняют C-008 validation
evidence и не разрешают массовую чистку без проверки call sites, Git history и защитных
тестов.

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
- Нативные адаптеры разбирают browser-visible Cinemar/Collaps contracts. Cinemar discovery
  остаётся strict exact-host `/embed/...`, но already discovered authenticated player
  document может иметь non-root/non-`/api/` runtime path exact `cinemar.cc`; query, fragment,
  userinfo, non-443 и subdomains запрещены. Текущий leaf может хранить только opaque `data`;
  exact selected leaf лениво обменивается на HLS через отдельно построенный fixed same-origin
  `/api/playlist/load` без cookies/redirect/retry. Session-owned registry не выносит token,
  iframe и media URL в MediaItem/log/persistence. Неизвестный, DRM- или JavaScript-only
  источник нельзя маскировать под Media3.
- Web fallback остаётся явным выбором пользователя; приложение не переключается в него
  молча. First-party PlayerJS state и cookies живут только в изолированном WebView profile;
  это не серверная/межустройственная синхронизация и не native checkpoint.
- Exact playback position хранится локально. С сайтом синхронизируются account bookmarks и
  statuses, но не Media3 checkpoint.
- Checkpoint writes для активной playback-сессии идут через очередь и принимаются
  только от текущей generation с неубывающим timestamp. Активация unit на `0` и
  newest completed checkpoint входят в resume policy: нельзя возвращаться к более
  старой unfinished-записи.
- Fixed quality — это сохраняемое пожелание/cap, а не гарантия одинакового
  фактического variant у всех серий. После manifest track discovery общая политика
  сравнивает adaptive tracks и fixed variants: exact, затем highest `<=` cap, иначе
  lowest above cap.
- Buffer target `S` берётся из allowlisted 5/10/15/20/30 с с default/fallback 15 с.
  Media3 получает `minBuffer=maxBuffer=S`, playback-start `clamp(S/3, 1–2,5 с)`,
  rebuffer-start `clamp(S/2, 2–5 с)` и `prioritizeTimeOverSizeThresholds=true`.
- Stall recovery имеет one-shot budget на content/season/episode. Production пороги
  инжектируются из buffer policy: initial `max(20 с, S)`, rebuffer `clamp(S, 5–10 с)`,
  `READY` without progress 15 с. Пауза, suppression и ended state не триггерят retry;
  fresh plan всегда получает новую player generation. Статические class defaults не
  являются production-конфигурацией `TvPlayerScreen`.
- `PlaybackMediaPlan.episodeCoordinatesFor` разворачивает все совместимые сезоны в один
  Media3 playlist. Preload включается только для episodic playback с auto-next при активном
  несупрессированном воспроизведении, когда осталось не больше `S` и конец уже buffered как
  минимум до `duration - 500 ms`. In-memory `ExoPlayer.PreloadConfiguration` ограничен
  immediate-next item: target 2,5 с для `S=5`, иначе 5 с, включая границу сезона. Pause,
  suppression, close/transition и seek назад отключают ранний open; disk media cache и
  отдельного token/resolver warmup нет.
- Runtime проверяет встроенные/ручные зеркала и безопасные redirect targets. Remote
  `config/mirrors.json` ограничен exact GitHub raw path/schema/size/count/expiry, но не
  подписан и только добавляет четыре quarantined discovery candidates, включая
  `kinogo.family`; internet-wide crawler нет.
- Updater доверяет не host, а signed payload, проверенному public key installed APK
  signer. До четырёх manifest/download URLs дают transport redundancy; GitHub Release API —
  fallback. Перед Android Package Installer повторно проверяются SHA-256, size,
  package/version и signing identity. Silent install нет, системное подтверждение
  обязательно. Pages/jsDelivr metadata и Pages/ghfast/ghproxy/direct APK transport проверены
  на exact bytes, но ни один host не даёт trust без signed size/SHA и final APK checks.
- GitHub Actions clean-clone unit/lint/assembleDebug и Pages publish прошли на final
  C-009 merge `ff7f5f8`; CI не имеет stable signing key и не доказывает TV UX.
- Registration отдельно показывает DLE rules gate с default decline; sensitive fields
  remember-only, late responses защищены generation+origin, image CAPTCHA имеет bounded
  transport/decode. Интерактивные reCAPTCHA/hCaptcha/Turnstile не обходятся и явно
  помечаются unsupported.

## Активный фокус

Следующий шаг — ручная приёмка владельцем уже опубликованного C-009 / `0.5.3`:

- проверить playback/updater приёмку; не подключаться к TV
  по ADB без нового явного разрешения на конкретный узкий сценарий;
- проверить встроенный updater от обнаружения версии до передачи exact APK системному
  Package Installer; системное подтверждение установки остаётся ручным;
- добавить действительно operator-owned non-GitHub metadata+APK host, если потребуется
  независимость от блокировки всей GitHub-инфраструктуры;
- не назначать C-009 baseline, пока не закрыты playback stall/recovery, exact
  resume, quality persistence/fallback и updater runtime-сценарии.

Подробная очередь — в [`ROADMAP.md`](ROADMAP.md).
