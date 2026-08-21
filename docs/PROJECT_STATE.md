# Текущее состояние проекта

Последнее обновление: **21 августа 2026 года**.

## Краткий итог

В рабочем дереве готовится `0.5.1` (code 15). Native Cinemar flow адаптирован к текущему
authenticated contract: already discovered player document может находиться на exact-host
runtime route вместо `/embed/...`, а конечный HLS лениво запрашивается только для выбранного
leaf fixed same-origin POST-ом на `/api/playlist/load`. Добавлены истинный Back в исходный раздел, восстановление
Search query/results/focus, строка до 10 последних запросов, вход в About из
первой карточки Settings и focusable rail logo, а также PlayerJS pause-before-dispose
для same-profile Web resume.

Updater переведён на первичный signed multi-endpoint manifest с проверкой публичным
ключом installed APK signer; strict GitHub Release API остался fallback. Default
metadata transports — GitHub Pages и jsDelivr; планируемые APK transports — Pages,
best-effort `ghfast.top`, best-effort `ghproxy.net` и direct Release. Пока не завершены
release/deployment/live checks, ни один из них не считается подтверждённым. Operator-owned
non-GitHub storage ещё нет.

Это **validation candidate C-007**, а не rollback baseline. Для текущего рабочего дерева
завершены локальный canonical Gradle pass, проверка stable-signed APK/подписанного manifest
и аппаратный native Cinemar/History pass на KIVI Android TV 14. Final source commit, CI,
GitHub Release/Pages/jsDelivr publication и оставшиеся Search/Web/updater/player сценарии
ещё **PENDING**. C-006 / `0.5.0` сохраняет ранее записанный build/device evidence; B-001
остаётся последним полным playback baseline.

## Текущий validation candidate

| Поле | Значение |
| --- | --- |
| Candidate | **C-007 / 0.5.1 validation** |
| Application source commit | **PENDING**; рабочее дерево ещё не является rollback point |
| Application ID | `com.kinogo.atv` |
| Version code | `15` |
| Version name | `0.5.1` |
| Минимальная версия | Android TV 9 / API 28 |
| Compile / target SDK | 37 / 37 |
| UI | Kotlin + Jetpack Compose, landscape TV-only |
| Плеер | AndroidX Media3 / ExoPlayer |
| Подпись APK | stable signing identity; локально v2 true, certificate SHA-256 `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9` |
| Baseline/release tag | не создавался; validation pending |

Локально проверен exact release artifact `dist/KinogoATV-0.5.1-code15.apk`:
**38 304 478 bytes**, SHA-256
`3166898FDFA882DB9A637ECDA6CDA612A5AF0B5F70D30580FD1449A906EBF875`.
Metadata: package `com.kinogo.atv`, code 15 / `0.5.1`, minSdk 28, targetSdk 37,
LEANBACK launcher/label `KinogoATV`; zipalign OK, v2 true, certificate SHA-256
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.
Это финальное локальное artifact evidence для текущего дерева, но ещё не commit-bound,
не CI/publication evidence и не installed-TV match. Не переносить в C-007 hash или runtime
evidence артефакта `0.5.0`.

Финальный локальный `update/manifest.json`: **1 273 bytes**, SHA-256
`3C167F87208077E6EC4717F202F968AD555B800C76043CFCF69B941627323070`; payload code 15 /
`0.5.1`, `issuedAtEpochSeconds=1787294465`, `expiresAtEpochSeconds=1794984054`
(18 ноября 2026 года, 06:40:54 UTC), четыре download URLs и exact APK size/hash выше.
Envelope/contents проверены локально; commit, Release asset и live Pages/jsDelivr deployment
ещё **PENDING**.

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
| Запуск | C-007 KIVI install/launch passed | `adb install -r` на Android TV 14 сохранил пользовательские данные и `firstInstallTime` |
| Android TV launcher | C-007 artifact metadata verified; installed runtime passed | Package/code/name/min/target/LEANBACK label, zipalign/v2/certificate проверены |
| Навигация | History/Search non-first verified | Player → Details → source destination прошёл; вторая History card и второй Search result восстановили exact focus |
| Главная | Работает; все 7 sorts прошли TV smoke | Без hero/history/title; live xSort, минимум 18 уникальных карточек при старте и ранний append |
| Каталог | Работает; все 7 sorts прошли TV smoke | Default `Новинки`, 28 allowlisted категорий, xSort dropdowns, отдельные `↑`/`↓` и append |
| Поиск | C-007 state/history + TV non-first verified | `Chris`, results и вторая карточка восстановлены после Details; recent-query row verified; long append pending |
| Общая сетка | Работает; focused smoke passed | Шесть колонок, stable IDs, exact neighbours, no wrap, preload при остатке менее двух строк |
| Карточка | Работает | Крупный постер, полный текст, основные/status/favorite actions |
| Постеры | Работает | HTTPS-only загрузка, memory/disk cache, безопасная заглушка |
| Зеркала | Existing flow verified; bootstrap live activation pending | Built-in/ручные + bounded unsigned 4-origin remote candidates; все discovery origins quarantined до health check |
| Аккаунт | Login verified; registration rules UI verified; live submit pending | Двухшаговый DLE rules gate, same-origin form/image CAPTCHA, Keystore login после success |
| Закладки | Работает | Статусы сайта, независимое избранное, sync и локальный outbox |
| История | C-007 true-Back/non-first focus verified | «Далеко во Вселенной» вернулся Player → Details → History; вторая «История его служанки» восстановила `focused=true` |
| Выбор источника | Работает | Source/voice/season/episode/quality sparse-матрица до запуска |
| Нативный плеер | C-007 current Cinemar runtime verified on TV | Exact-host runtime player document → native selector → lazy selected-leaf `/api/playlist/load` → Media3 S2E5 >15 с; cross-season/natural-end ещё pending |
| Web fallback | C-007 launch/Back smoke passed; resume pending | D-pad выбрал original Cinemar WebView, fullscreen открылся и Back вернул Details → History; actual playlist/position reopen не доказан |
| Настройки | C-006 debug TV smoke passed | Switch и D-pad dropdown проверены; focus return работает |
| Обновления | C-007 signed manifest verified locally; live pending | Final local envelope содержит Pages + 2 best-effort proxies + direct Release downloads; installed-signer trust, GitHub API fallback; operator-owned host pending |
| About | C-007 placement/logo source fix; TV pending | Первая крупная Settings card и focusable rail logo; C-006 QR/external-link smoke исторический |
| CI | **PENDING** | Android workflow и Pages update workflow требуют remote evidence на финальном commit |

## Проверка C-007

Для текущего рабочего дерева canonical команда
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

Final source commit, CI, GitHub Release/Pages/jsDelivr publication и live updater остаются
**PENDING**. До их закрытия не считать default Pages/jsDelivr URLs живыми каналами.

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
- Runtime проверяет встроенные/ручные зеркала и безопасные redirect targets. Remote
  `config/mirrors.json` ограничен exact GitHub raw path/schema/size/count/expiry, но не
  подписан и только добавляет четыре quarantined discovery candidates, включая
  `kinogo.family`; internet-wide crawler нет.
- Updater доверяет не host, а signed payload, проверенному public key installed APK
  signer. До четырёх manifest/download URLs дают transport redundancy; GitHub Release API —
  fallback. Перед Android Package Installer повторно проверяются SHA-256, size,
  package/version и signing identity. Silent install нет, системное подтверждение
  обязательно. Pages/jsDelivr/proxy transports до live-проверки считаются pending;
  ни один из них не даёт trust без signed size/SHA и final APK checks.
- GitHub Actions workflow настроен для clean-clone unit/lint/assembleDebug, но exact
  remote run для C-007 ещё не записан. CI не имеет stable signing key и не доказывает TV UX.
- Registration отдельно показывает DLE rules gate с default decline; sensitive fields
  remember-only, late responses защищены generation+origin, image CAPTCHA имеет bounded
  transport/decode. Интерактивные reCAPTCHA/hCaptcha/Turnstile не обходятся и явно
  помечаются unsupported.

## Активный фокус

Следующий шаг — зафиксировать и проверить C-007 / `0.5.1`:

- связать локально подтверждённые canonical results, exact APK и signed manifest с final
  source commit;
- получить CI для exact commit, опубликовать exact Release asset, развернуть
  Pages/jsDelivr и проверить metadata/APK URLs и update до OS confirmation;
- на реальном TV завершить About и доказать actual web-to-web PlayerJS resume; запуск WebView
  и чистый Back уже проверены, но playlist/position недоступны safe evidence;
- не назначать C-007 baseline, пока не закрыты обычные playback/recovery/cross-season
  регрессии и новые сценарии.

Подробная очередь — в [`ROADMAP.md`](ROADMAP.md).
