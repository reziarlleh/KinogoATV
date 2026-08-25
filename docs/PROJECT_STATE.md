# Текущее состояние проекта

Последнее обновление: **23 августа 2026 года**.

## Краткий итог

Текущий application source выпускает **0.5.2** (code 16), validation candidate
**C-008**. Основное изменение — автоматическое one-shot восстановление native playback:
production watchdog получает пороги из той же политики, что и Media3 buffer:
initial `max(20 с, target)`, rebuffer `clamp(target, 5–10 с)` и 15 с для `READY` без
прогресса. При срабатывании запрашивается fresh source plan,
увеличивается session generation и создаётся новый Media3 player с той же unit/позицией.
Ручная native-кнопка обновления удалена; после исчерпания автопопытки явный retry
выполняется через Back → Details → «Смотреть».

Resume state усилен сериализованной checkpoint-очередью, generation guard и
монотонным timestamp. Новая серия фиксируется даже на позиции `0`; более новый
completed checkpoint подавляет старую unfinished-запись. Детали материала остаются в
root cache, поэтому «Смотреть» доступна сразу после выхода из плеера. Desired quality
хранится отдельно от actual variant и применяется между сериями по правилу exact →
highest available `<=` cap → lowest available above cap; смена intent до preload пересчитывает
fixed MediaItems будущих серий, не меняя текущую позицию/reference. Пункты updater собраны в конце
Settings; неиспользуемый arrow-cycle settings path удалён. Новый dropdown
«Буфер воспроизведения» даёт 5/10/15/20/30 с (default/fallback 15 с) и напрямую
настраивает Media3 `LoadControl`. Immediate-next episode ограниченно заготавливается в
памяти через `ExoPlayer.PreloadConfiguration`; все совместимые сезоны уже сведены в один
playlist, поэтому граница сезона не требует отдельного resolver warmup.

C-008 имеет зафиксированный application source, зелёные local/CI-проверки, exact
stable-signed APK, regular latest GitHub Release и опубликованный signed update manifest,
но не является новым playback baseline. Репозиторий публичный; Release, Pages, jsDelivr и
все четыре APK transport проверены на exact bytes. По выбору владельца APK 0.5.2 на TV не
устанавливался;
hardware playback и updater runtime validation остаются **PENDING**. Доказательства C-007 /
`0.5.1` ниже сохранены как исторические и к C-008 не переносятся. B-001 остаётся
последним полным playback baseline.

## Текущий validation candidate

| Поле | Значение |
| --- | --- |
| Candidate | **C-008 / 0.5.2 validation** |
| Application source commit | `4cfa7ac8ebd48b70c7b172e54a0716fec09669a1` |
| Application ID | `com.kinogo.atv` |
| Version code | `16` |
| Version name | `0.5.2` |
| Минимальная версия | Android TV 9 / API 28 |
| Compile / target SDK | 37 / 37 |
| UI | Kotlin + Jetpack Compose, landscape TV-only |
| Плеер | AndroidX Media3 / ExoPlayer |
| Подпись APK | v2 true; certificate SHA-256 `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9` |
| Release tag | `v0.5.2`; regular latest Release, не baseline |

Exact artifact C-008: `dist/KinogoATV-0.5.2-code16.apk`, **38 353 630 bytes**,
SHA-256
`FC70D02A2BC7A3F9E5E2F04A1A7B139037AC215C85166E72E9842D0DB3CB4B38`; package
`com.kinogo.atv`, code 16 / `0.5.2`, minSdk 28, target/compile SDK 37, Android TV
LEANBACK launcher/banner, zipalign OK, v2 true. Embedded revision — `4cfa7ac`, то есть exact
application source выше. GitHub asset содержит тот же lowercase digest
`sha256:fc70d02a2bc7a3f9e5e2f04a1a7b139037ac215c85166e72e9842d0db3cb4b38`.
APK/hash/TV results C-007 сюда не переносятся.

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
«Предыдущая проверка C-007» и не являются evidence для C-008.

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
| Запуск | C-008 hardware **PENDING** | APK 0.5.2 на TV не устанавливался; C-007 KIVI install/launch evidence остаётся историческим |
| Android TV launcher | C-008 local artifact verified | Exact package/code/name/min/target, LEANBACK launcher/banner, zipalign, v2 signature и certificate проверены локально |
| Навигация | History/Search non-first verified | Player → Details → source destination прошёл; вторая History card и второй Search result восстановили exact focus |
| Главная | Работает; все 7 sorts прошли TV smoke | Без hero/history/title; live xSort, минимум 18 уникальных карточек при старте и ранний append |
| Каталог | Работает; все 7 sorts прошли TV smoke | Default `Новинки`, 28 allowlisted категорий, xSort dropdowns, отдельные `↑`/`↓` и append |
| Поиск | C-007 state/history + TV non-first verified | `Chris`, results и вторая карточка восстановлены после Details; recent-query row verified; long append pending |
| Общая сетка | Работает; focused smoke passed | Шесть колонок, stable IDs, exact neighbours, no wrap, preload при остатке менее двух строк |
| Карточка | C-008 source implemented; runtime pending | Fresh details cache оставляет «Смотреть» доступной после возврата из player; крупный постер, полный текст и status actions сохранены |
| Постеры | Работает | HTTPS-only загрузка, memory/disk cache, безопасная заглушка |
| Зеркала | Existing flow verified; bootstrap live activation pending | Built-in/ручные + bounded unsigned 4-origin remote candidates; все discovery origins quarantined до health check |
| Аккаунт | Login verified; registration rules UI verified; live submit pending | Двухшаговый DLE rules gate, same-origin form/image CAPTCHA, Keystore login после success |
| Закладки | Работает | Статусы сайта, независимое избранное, sync и локальный outbox |
| История | C-008 source implemented; runtime pending | Serialized/generation-guarded/monotonic checkpoint writes, unit activation at position `0`, newest-completed suppression и merge history+progress; C-007 Back/focus evidence историческое |
| Выбор источника | C-008 source implemented; runtime pending | Source/voice/season/episode sparse-матрица; desired quality сохраняется между сериями как cap |
| Нативный плеер | C-008 source implemented; hardware **PENDING** | Buffer-aware watchdog, one fresh source attempt, forced new player generation, same-unit exact-position resume и quality exact/≤/lowest-above; ручная refresh-кнопка удалена |
| Web fallback | C-007 launch/Back smoke passed; resume pending | D-pad выбрал original Cinemar WebView, fullscreen открылся и Back вернул Details → History; actual playlist/position reopen не доказан |
| Настройки | C-008 source implemented; runtime pending | Updater controls собраны в конце; Switch/OK-dropdown contract сохранён, obsolete arrow-cycle path удалён; buffer dropdown 5/10/15/20/30 с |
| Обновления | Publication verified; runtime **PENDING** | Signed manifest, Release asset, Pages/jsDelivr metadata и четыре APK transport проверены; in-app flow до OS confirmation владелец проверит вручную |
| About | C-007 placement/logo source fix; TV pending | Первая крупная Settings card и focusable rail logo; C-006 QR/external-link smoke исторический |
| CI | **PASS** | Android run `32598900494` и Pages run `32598900503` зелёные на manifest/main merge `367bcf2` |

## Проверка C-008

Для application source
`4cfa7ac8ebd48b70c7b172e54a0716fec09669a1` canonical команда
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease`
завершена **SUCCESS за 5 мин 20 с**: **87 suites / 441 tests**, 0 failures, 0 errors,
0 skipped; lint — **0 errors / 22 warnings / 2 hints**. После commit выполнен независимый
`assembleRelease --rerun-tasks` — **SUCCESS за 5 мин 29 с**. Exact APK и его
metadata/alignment/signature/hash приведены выше. CI, PR/merge, Release, final signed
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
`repowise init --codex --no-prose --yes`. Текущий снимок: commit `134ae00`, 246 файлов,
4 700 символов, 524 структурные страницы и 42 MB локального индекса; provider/model не
использовались. `repowise doctor` завершился `All checks passed`, `status` не показал stale
pages или SQL/vector/FTS drift, а реальный `context` для `KinogoAppRoot.kt` вернул
структурную карточку.

`.repowise/`, `.mcp.json`, `.claude/`, локальные `.vscode` RepoWise-файлы и уже игнорируемая
`.codex/` являются машинно-зависимыми: часть конфигураций содержит абсолютный путь checkout,
а индекс состоит из производных SQLite/LanceDB/cache данных. В Git сохраняются только общая
политика игнорирования и документация. Автогенерация managed-блока `AGENTS.md` локально
отключена (`editor_files.agents_md: false`), потому что post-commit snapshot иначе оставляет
tracked-файл грязным после каждого commit. Исходный обязательный `AGENTS.md` сохранён.
Локальный `.git/hooks/post-commit` обновляет индекс в фоне, но не является tracked-файлом.

Health score `7.87/10` average и `4.83/10` для hotspots, а также 456 static findings — только
сигнал для выбора области дальнейшего аудита. Они не считаются подтверждёнными дефектами,
не меняют C-008 validation evidence и не разрешают массовую чистку без проверки call sites,
Git history и защитных тестов.

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
- GitHub Actions clean-clone unit/lint/assembleDebug и Pages publish прошли на merge
  `367bcf2`. CI не имеет stable signing key и не доказывает TV UX.
- Registration отдельно показывает DLE rules gate с default decline; sensitive fields
  remember-only, late responses защищены generation+origin, image CAPTCHA имеет bounded
  transport/decode. Интерактивные reCAPTCHA/hCaptcha/Turnstile не обходятся и явно
  помечаются unsupported.

## Активный фокус

Следующий шаг — вручную принять опубликованный C-008 / `0.5.2`:

- передать владельцу APK для ручной playback/updater приёмки; не подключаться к TV
  по ADB без нового явного разрешения на конкретный узкий сценарий;
- проверить встроенный updater от обнаружения версии до передачи exact APK системному
  Package Installer; системное подтверждение установки остаётся ручным;
- добавить действительно operator-owned non-GitHub metadata+APK host, если потребуется
  независимость от блокировки всей GitHub-инфраструктуры;
- не назначать C-008 baseline, пока не закрыты playback stall/recovery, exact
  resume, quality persistence/fallback и updater runtime-сценарии.

Подробная очередь — в [`ROADMAP.md`](ROADMAP.md).
