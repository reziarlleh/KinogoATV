# Текущее состояние проекта

Последнее обновление: **15 августа 2026 года**.

## Краткий итог

В рабочем дереве интегрируется `0.5.0`: регистрация через same-origin DLE-форму и image
CAPTCHA без обхода, quarantined remote bootstrap зеркал, проверяемый GitHub Release updater
с обязательным системным подтверждением, единая resume-policy, bounded refresh истёкшего
playback source, исправление перехода через границу сезона и обновлённый TV focus/settings
контракт. Добавлены About/disclaimer, exact GitHub/Donate.Stream actions и CI workflow.

Это **validation candidate C-006**, а не новый полный playback baseline. Полный локальный
unit/lint/build pass завершён, финальный stable-signed Release APK проверен и установлен
поверх существующей установки на X96Max Plus Ultra Android TV 14 с сохранением данных.
Debug-smoke на KIVI ранее подтвердил focus/settings/About и короткий playback/resume-return.
Application source зафиксирован commit `6567088`; GitHub Actions/публичный Release,
updater-live и расширенный playback pass ещё не зафиксированы. B-001 остаётся последним
полным playback baseline.

## Текущий validation candidate

| Поле | Значение |
| --- | --- |
| Candidate | **C-006 / 0.5.0 validation** |
| Application source commit | `6567088` (`Prepare KinogoATV 0.5.0`) |
| Application ID | `com.kinogo.atv` |
| Version code | `14` |
| Version name | `0.5.0` |
| Минимальная версия | Android TV 9 / API 28 |
| Compile / target SDK | 37 / 37 |
| UI | Kotlin + Jetpack Compose, landscape TV-only |
| Плеер | AndroidX Media3 / ExoPlayer |
| Подпись APK | стабильный локальный ключ; APK Signature Scheme v2 verified |
| Baseline/release tag | не создавался; validation pending |

Проверенный финальный artifact: `dist/KinogoATV-0.5.0-code14.apk`, **38 140 638 bytes**.

SHA-256:
`3650C44B40A7AC066F98B597E0831BB800512CA5695EBD554DDD5620E15ED52B`.

Certificate SHA-256:
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.

Metadata: `com.kinogo.atv`, code 14 / `0.5.0`, minSdk 28, targetSdk 37, label
`KinogoATV`, LEANBACK launcher/banner. Zipalign успешно, v2 signature true. Application
source — `6567088`; GitHub Release/CI всё ещё PENDING.

## Known-good baseline и откат

Текущий полностью подтверждённый playback baseline: **B-001 / 0.3.3-dev**.

- Runtime evidence: 28 июля 2026 года.
- Source baseline tag: `baseline-0.3.3-dev`.
- Локальный rollback artifact: `dist/KinogoTV-0.3.3-dev.apk`.
- Artifact SHA-256:
  `931253976140D5A76276AB4F30E7A709600CD61EABFE1FD8A36C29F38B454A77`.
- Signature certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.

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
| Запуск | C-006 final Release TV smoke passed | X96 cold 1023 ms, initial Home rail focus, no FATAL/ANR |
| Android TV launcher | Работает | Label `KinogoATV`, LEANBACK launcher/banner и ATV icon проверены в artifact |
| Навигация | C-006 debug TV smoke passed | Cold start фокусирует rail; focused/selected состояния различимы; подтверждение выхода |
| Главная | Работает; все 7 sorts прошли TV smoke | Без hero/history/title; live xSort, минимум 18 уникальных карточек при старте и ранний append |
| Каталог | Работает; все 7 sorts прошли TV smoke | Default `Новинки`, 28 allowlisted категорий, xSort dropdowns, отдельные `↑`/`↓` и append |
| Поиск | Работает; long append pending | Debounce 750 ms, immediate submit, keyboard hide, retry и paged query |
| Общая сетка | Работает; focused smoke passed | Шесть колонок, stable IDs, exact neighbours, no wrap, preload при остатке менее двух строк |
| Карточка | Работает | Крупный постер, полный текст, основные/status/favorite actions |
| Постеры | Работает | HTTPS-only загрузка, memory/disk cache, безопасная заглушка |
| Зеркала | Existing flow verified; bootstrap live activation pending | Built-in/ручные + bounded unsigned 4-origin remote candidates; все discovery origins quarantined до health check |
| Аккаунт | Login verified; registration rules UI verified; live submit pending | Двухшаговый DLE rules gate, same-origin form/image CAPTCHA, Keystore login после success |
| Закладки | Работает | Статусы сайта, независимое избранное, sync и локальный outbox |
| История | C-006 basic resume-return TV smoke passed | Newest unfinished checkpoint; после ~14 с Back вернул Details с focused `Продолжить с 0:14` |
| Выбор источника | Работает | Source/voice/season/episode/quality sparse-матрица до запуска |
| Нативный плеер | C-006 short TV smoke passed; recovery/cross-season pending | Home → Details → selector → ~14 с playback → Back/Details; bounded refresh и cross-season покрыты tests |
| Web fallback | Работает | Явный provider-only WebView с origin boundary, TV HUD и виртуальным курсором |
| Настройки | C-006 debug TV smoke passed | Switch и D-pad dropdown проверены; focus return работает |
| Обновления | Artifact policy verified; live updater pending | Exact stable GitHub Release contract; APK metadata/hash/signer verified вручную, installer flow не проверен |
| About | C-006 debug TV smoke passed | D-pad reachability, QR/dialog; Donate.Stream/GitHub открылись в Yandex TV browser |
| CI | Workflow добавлен; first remote run pending | GitHub Actions: JDK 17, SDK 37, unit/lint/assembleDebug |

## Проверка C-006 и final artifact

Локально на текущем рабочем дереве успешно завершена команда с задачами:

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
- Runtime проверяет встроенные/ручные зеркала и безопасные redirect targets. Remote
  `config/mirrors.json` ограничен exact GitHub raw path/schema/size/count/expiry, но не
  подписан и только добавляет четыре quarantined discovery candidates, включая
  `kinogo.family`; internet-wide crawler нет.
- Updater доверяет не одному URL: перед Android Package Installer проверяются release
  metadata, SHA-256, package/version и signing identity. Silent install нет, системное
  подтверждение обязательно.
- GitHub Actions workflow настроен для clean-clone unit/lint/assembleDebug, но его первый
  remote run для C-006 ещё не записан. CI не имеет stable signing key и не доказывает TV UX.
- Registration отдельно показывает DLE rules gate с default decline; sensitive fields
  remember-only, late responses защищены generation+origin, image CAPTCHA имеет bounded
  transport/decode. Интерактивные reCAPTCHA/hCaptcha/Turnstile не обходятся и явно
  помечаются unsupported.

## Активный фокус

Следующий шаг — зафиксировать и проверить C-006 / `0.5.0`:

- завершить integration commit и получить первый GitHub Actions result на той же ревизии;
- опубликовать проверенный artifact как exact GitHub Release asset и проверить updater
  digest/API contract;
- проверить live registration submit и безопасный update-to-OS-confirmation flow с реально
  более новой версией;
- проверить resume реального многосерийного материала, one-shot source refresh,
  Previous/Next/auto-next через
  границу сезона и natural completion;
- только после полного evidence решить, становится ли C-006 новым baseline/release tag.

Подробная очередь — в [`ROADMAP.md`](ROADMAP.md).
