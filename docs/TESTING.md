# Стратегия тестирования

Последнее обновление: **23 августа 2026 года**.

## Принцип доказательств

Для KinogoATV существуют четыре разных уровня:

1. Unit/contract tests доказывают модели, parsers, reducers и политики.
2. Lint/build доказывают корректность Android-проекта и создание APK.
3. Instrumentation доказывает Android-specific API и Compose focus в контролируемой среде.
4. Реальный TV доказывает запуск, D-pad, media keys, сеть и фактическое воспроизведение.

Успешный `assembleDebug` не равен успешному просмотру фильма.

Порядок проверки всегда начинается с code review, pure/unit/contract tests, lint и сборки.
Подключение к реальному TV через ADB, установка APK, instrumentation и аппаратный smoke не
являются автоматическим продолжением этой цепочки. Они допустимы только после
предварительного явного разрешения владельца на конкретный узкий сценарий, когда результат
нельзя надёжно предсказать по коду и автоматическим проверкам. Без такого разрешения
hardware-результат остаётся `PENDING`; агент не выполняет даже диагностическое ADB-подключение.

## Unit и contract tests

```powershell
.\gradlew.bat testDebugUnitTest `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Покрываются:

- catalog category/search routes, HTML/xSort parser, origin-session commands, paging/preload
  и safe GET/POST transport;
- auth/library codecs, login/registration HTML parsers, CAPTCHA transport и
  status/favorite semantics;
- mirror normalization, trust, redirect, health и bounded remote bootstrap manifest;
- DNS/public destination policy;
- Cinemar/Collaps/direct/gateway playback discovery, включая deferred Cinemar leaf,
  exact-origin grant transport, lazy session registry и Media3 resolver ownership;
- media plan mapping, dependent choices и cross-season episode coordinates;
- history codec, legacy resolver, newest-unfinished resume/completion;
- TV preferences;
- player reducer, key mapper, focus retry, HUD routing, one-shot source refresh и
  cross-season completion policy;
- signed multi-endpoint/GitHub-fallback update parsers и clients,
  asset/hash/package/version/signing policy;
- UI mappers, отдельное направление сортировки и ключевые pure focus/back/grid decisions.

Live HTML не должен быть единственным тестом parser. Сначала redacted fixture, затем
необязательная read-only live-проверка.

Исторический unit run C-005 / `0.4.3-dev` от 1 августа 2026 года: **68 suites,
309 unit tests**, 0 failures/errors/skipped. Его полный canonical lint/build сохранён в
`PROJECT_STATE.md`; актуальный C-007 result указан ниже.

Для C-006 / `0.5.0` в дереве присутствуют guards, включая
`KinogoAppRootResumeTest`, `PlaybackSourceRefreshTest`, `KinogoRegistrationApiTest`,
`RegistrationHtmlParserTest`, `MirrorBootstrapClientTest`, `GitHubReleaseParserTest`,
`ApkUpdatePolicyTest` и обновлённые preferences/completion tests. Финальный локальный
integration pass: **75 suites / 348 unit tests**, 0 failures, 0 errors, 0 skipped. До commit
этот результат относится к рабочему дереву, а не к неизменяемой Git-ревизии.

Для C-007 / `0.5.1` добавлены `CinemarNativeSourceAdapterTest`,
`CinemarGrantClientTest`, `CinemarDeferredGrantRegistryTest`,
`CinemarDeferredGrantPlaybackPlanTest`, `SearchHistoryStoreTest`,
`SignedUpdateManifestParserTest`, `SignedManifestUpdateClientTest`,
`FallbackAppUpdateClientTest`, а также обновлённые navigation/grid/WebView tests.
Final local canonical command
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease` дал
**SUCCESS за 4 мин 27 с**, **82 suites / 393 tests**, 0 failures, 0 errors, 0 skipped;
lint — **0 errors / 22 warnings / 2 hints**.
Результат привязан к application source commit
`8b0be72cf32d6807f0dc4ff5c5e21da95e847874`; remote CI ещё **PENDING**.

Для C-008 / `0.5.2` (code 16) добавлены или расширены следующие защитные контракты:

- `PlaybackSourceRefreshTest` — watchdog для длительного initial buffering, rebuffering и
  состояния READY без продвижения позиции, включая near-end stall; только реальный `ENDED`,
  pause и suppression исключаются, recovery остаётся одноразовым для playback unit;
- `KinogoAppRootResumeTest` — сериализация checkpoint-записей, generation guard, возврат
  свежей Details, newest-active resume с подавлением старого unfinished после более нового
  completed checkpoint, same-unit position guard и нулевая позиция новой episodic unit;
- `PlaybackProgressCodecTest` — поздняя более старая запись не перезаписывает новый
  checkpoint, а merge persistent history не теряет прогресс;
- `PlaybackQualityPolicyTest` — единая матрица выбора между adaptive tracks и отдельными
  fixed variants, распознавание 2160/4K и перенос пользовательского предела между сериями;
- `PlaybackBufferPolicyTest` — pure mapping пяти значений запаса в Media3 LoadControl и
  связанные с ним bounded recovery deadlines, exact gate и длительность preload следующей серии;
- `PlaybackMediaPlanTest` — упорядоченная sparse-матрица совместимых episode coordinates
  разворачивается в один playlist через границы сезонов;
- `PlaybackSourceSelectionModelTest` — нормализация dependent choices не заменяет
  фиксированное пожелание качества фактически выбранным вариантом;
- `PlaybackPlaylistNavigationTest` и `PlaybackQualitySwitchGuardTest` — cross-season playlist,
  checkpoint transition и rebuild будущих MediaItems при смене quality intent с сохранением
  текущих reference/index/position/play state;
- `PlaybackPreloadFailurePolicyTest` — только exact immediate-next window активной playlist
  generation может быть помечена failed; stale/unrelated event не запускает recovery;
- `TvPreferencesTest`, `TvPreferencesStoreTest` и `TvPreferencesUiMapperTest` — явные
  option IDs `5/10/15/20/30`, fallback `15`, сохранение buffer value после recreation и
  фактический `set(settingId, optionId)` вместо удалённого неиспользуемого cycle API.

Для application source
`4cfa7ac8ebd48b70c7b172e54a0716fec09669a1` final canonical
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease` —
**SUCCESS за 5 мин 20 с**, **87 suites / 441 tests**, 0 failures, 0 errors, 0 skipped;
lint — **0 errors / 22 warnings / 2 hints**. Post-commit `assembleRelease --rerun-tasks` —
**SUCCESS за 5 мин 29 с**. Remote CI остаётся **PENDING**.

### Buffer policy C-008

В source зафиксирована одна pure `PlaybackBufferPolicy`, чтобы UI, Media3 и stall recovery
не расходились. Значение — секунды, допустимы только `5`, `10`, `15`, `20`, `30`, default и
fallback — `15`. Для выбранного `S`:

```text
targetBufferMs = minBufferMs = maxBufferMs = S * 1000
bufferForPlaybackMs = clamp(targetBufferMs / 3, 1000, 2500)
bufferForPlaybackAfterRebufferMs = clamp(targetBufferMs / 2, 2000, 5000)
nextEpisodePreloadMs = clamp(targetBufferMs / 2, 2000, 5000)
prioritizeTimeOverSizeThresholds = true
initialBufferingRecoveryMs = max(20, S) * 1000
rebufferingRecoveryMs = clamp(S, 5, 10) * 1000
readyNoProgressRecoveryMs = 15000
```

Иными словами, target reserve точно равен выбранным 5/10/15/20/30 секундам; initial
watchdog составляет 20 секунд для 5–20 и 30 секунд для 30, rebuffer watchdog — 5 секунд
для 5 и 10 секунд для 10–30. Target preload непосредственно следующей Media3 playlist item
равен 2,5 секунды при запасе 5 и 5 секундам при запасе 10–30; миллисекунды переводятся в
microseconds только при создании `ExoPlayer.PreloadConfiguration`.

Preload gate требует одновременно: episodic media, `autoNextEpisode=true`, наличие
следующей item, `playWhenReady=true`, отсутствие suppression, remaining `<= S` и
`bufferedPosition >= duration - 500 ms`. Pause, suppression, close/transition и backward
seek disarm-ят preload; после seek назад minimum rearm position не позволяет сразу открыть
future leaf повторно. Нефатальная load error immediate-next item не вызывает recovery
current item. Terminal future error принимается только для exact generation/index/variant и
запускает recovery после фактического перехода на эту item; stale/unrelated events
игнорируются.

`PlaybackMediaPlan.episodeCoordinatesFor` разворачивает все реально совместимые координаты
выбранных source + voiceover, включая sparse следующие сезоны, в один упорядоченный Media3
playlist. Media3 держит in-memory preload только непосредственно следующей item, поэтому тот
же механизм работает на границе сезона, но не прогревает все серии, не создаёт disk cache и
не запускает отдельный resolver warmup. `PlaybackBufferPolicyTest` фиксирует exact targets,
fallback, start/recovery/preload mapping; `PlaybackMediaPlanTest` — flattened sparse season
coordinates; `PlaybackPreloadFailurePolicyTest` — future-error isolation;
preferences/store/UI tests — dropdown и persistence. Эти классы входят в зелёный final
canonical результат C-008 выше.

## Lint и сборка

```powershell
.\gradlew.bat lintDebug assembleDebug `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Исторический C-005: lint 0 errors, 7 warnings и 2 hints; debug APK успешно создан. APK прошёл
zipalign и v2 verification с ожидаемым certificate SHA-256; точный artifact hash указан в
`PROJECT_STATE.md`.

Для C-006 `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` и `assembleRelease`
завершены успешно; lint — 0 errors / 19 warnings / 2 hints. Финальный artifact
`dist/KinogoATV-0.5.0-code14.apk` прошёл metadata/size/SHA-256, zipalign, v2 signature и
certificate verification; значения находятся в `PROJECT_STATE.md`.

Для C-007 локально проверен exact release APK `dist/KinogoATV-0.5.1-code15.apk`:
38 304 478 bytes, SHA-256
`3166898FDFA882DB9A637ECDA6CDA612A5AF0B5F70D30580FD1449A906EBF875`; package
`com.kinogo.atv`, code 15 / `0.5.1`, minSdk 28, targetSdk 37, LEANBACK launcher/label
`KinogoATV`, zipalign OK, v2 true,
certificate SHA-256
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.

Для C-008 локально проверен exact release APK `dist/KinogoATV-0.5.2-code16.apk`:
38 353 630 bytes, SHA-256
`FC70D02A2BC7A3F9E5E2F04A1A7B139037AC215C85166E72E9842D0DB3CB4B38`; package
`com.kinogo.atv`, code 16 / `0.5.2`, minSdk 28, target/compile SDK 37, LEANBACK
launcher/banner, zipalign OK, v2 true, embedded revision `4cfa7ac`, certificate SHA-256
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`. Артефакт
связан с exact application source
`4cfa7ac8ebd48b70c7b172e54a0716fec09669a1`.

Final signed code 16 `update/manifest.json` ещё **PENDING**: старый code 15 manifest
намеренно удалён перед первым merge C-008, чтобы Pages workflow не развернул устаревший
payload. Release/Pages/jsDelivr/live updater и hardware runtime не проверены.

Исторический C-007 final local `update/manifest.json` имеет 1 273 bytes, SHA-256
`3C167F87208077E6EC4717F202F968AD555B800C76043CFCF69B941627323070`, code 15 /
`0.5.1`, `issuedAtEpochSeconds=1787294465`, `expiresAtEpochSeconds=1794984054`
(18 ноября 2026 года, 06:40:54 UTC), четыре URLs и exact APK size/hash. Локальная
проверка файла не доказывает commit, CI, publication, live endpoint или TV runtime.

После изменения signing/build logic дополнительно проверить clean clone без `.signing`:
unit/lint/debug должны работать со стандартной debug signature, release — завершаться ясной
ошибкой.

## Instrumentation

Имеются Android tests для DNS/regex initialization, D-pad Settings UI, initial navigation
rail focus и registration rules gate. AndroidTest APK C-006 успешно собран.
Финальный `RegistrationDialogDpadTest` точечно запущен на hardware: `OK (1)`. Начальный
focus — `Не принимаю`, callback accept срабатывает только после явного center press на
`Принимаю и продолжить`, а Down с нижней границы rules scroll возвращает focus на безопасный
decline вместо scroll trap. Пакет `com.kinogo.atv.test` после проверки удалён.
Settings Switch/dropdown и initial rail focus дополнительно проверены debug runtime-smoke.
Обновлённые C-007 AndroidTest sources успешно прошли `assembleDebugAndroidTest`;
точечный hardware run в этом кандидате ещё **PENDING**.

### Запрет на пользовательском TV

Не запускать:

```text
connectedDebugAndroidTest
```

на телевизоре с реальным аккаунтом и историей. Managed Gradle workflow может установить,
удалить или заменить target application и стереть его данные.

Кроме того, без предварительного явного разрешения владельца на конкретный узкий сценарий
запрещены любое ADB-подключение к реальному TV, `adb install -r`, ручная установка test APK,
instrumentation и runtime smoke. Сам факт, что устройство уже было авторизовано для этого
компьютера, не является новым разрешением. Сначала исчерпываются review и автоматические
тесты; разрешение запрашивается только для поведения, которое действительно нельзя надёжно
установить без аппаратуры.

### Точечный безопасный запуск

Команды ниже являются справочным шаблоном и выполняются только после отдельного явного
разрешения владельца на названный test class и устройство:

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

Раздел ниже описывает evidence, которое может понадобиться перед назначением baseline, но не
разрешает подключение. Подключение по Ethernet/Wi-Fi ADB возможно только после явного
разрешения владельца именно на текущий узкий сценарий и после включения network debugging:

```powershell
adb connect <TV_IP>:5555
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`install -r` сохраняет данные только если applicationId и подпись совпадают.

Для C-008 владелец выбрал ручную проверку updater. Поэтому агент не устанавливает кандидат
на TV и не выполняет smoke без нового отдельного разрешения; публикация validation release
может состояться на основании final automated/artifact evidence, но без baseline tag и без
утверждений о hardware validation.

### Smoke matrix

| Область | Проверка |
| --- | --- |
| Cold launch | Плитка открывает native first frame; selected rail item получает первый focus |
| Rail | Focused = cyan + white marker; selected-unfocused различим; Right/Left graph стабилен |
| Главная | Нет истории/заголовка; все 7 sorts; стартовый резерв 18 unique; ранний append без focus jump |
| Каталог | Default «Новинки»; 28 категорий; все 7 sorts/direction; direct-entry load и append |
| Поиск | Text/voice query; keyboard hide; retry и append того же encoded query |
| Details | Полное описание, status/favorite actions, Play |
| Mirrors | Check, details action, manual HTTPS input, selection |
| Account | Login, process restart, expired-session reconnect |
| Registration | Separate rules gate/default decline; same-origin form, bounded image CAPTCHA/refresh/rejection; unsupported interactive challenge |
| Library | Status/favorite mutation, pending indicator, sync |
| History | Correct title/poster; newest active unit/position совпадает с Catalog/Search/Details; completed newest не откатывается к старой unfinished |
| Selection | Source/voice/season/episode/quality dependencies |
| Native player | Start, pause, seek, timeline focus, selectors, subtitles |
| Remote | Simple D-pad and available media/digit keys |
| Lifecycle | Home/back/reopen; checkpoint not lost |
| Settings | Switch, dropdown/Back focus return, Left/Right navigation, auto-update toggle |
| Update | Check, verify/download, unknown-sources screen и mandatory OS installer confirmation |
| About | Initial Close focus, QR, exact Donate.Stream/GitHub actions, Back return |
| Exit | Root Back asks for confirmation, default focus is Stay |
| Crash diagnostics | Controlled debug fault only on disposable data/device |

### Focused TV evidence C-007

- Device: KIVI `192.168.1.112`, Android TV 14. Stable-signed release APK установлен через
  `adb install -r`; `firstInstallTime=2026-07-26 16:42:18` сохранился.
- Current provider contract: authenticated detail вернул exact `cinemar.cc` runtime player
  document, не `/embed/...`. Native selector «Далеко во Вселенной» показал Cinemar,
  озвучки, сезоны 1–4 и серии; `Продолжить` показал 10:48.
- Media3 S2E5 продвинулся 11:01 → 11:39 (>15 с). При скрытом HUD `OK` показал controls и
  не поставил видео на паузу. Back вернул Player → Details → History.
- History non-first: вторая карточка «История его служанки» после source/details chain и
  Back → Details → Back → History снова имела `focused=true`.
- Search non-first: запрос `Chris`, выдача и вторая карточка «Рождественская неделя»
  сохранились после Details → physical Back; ровно эта карточка снова имела `focused=true`.
  Горизонтальная recent-query row подтверждена ранее.
- Web fallback: D-pad selector дошёл до `Оригинальный web-плеер`
  (`Смотреть онлайн · cinemar`), fullscreen WebView запустился, Back чисто вернул Details,
  затем History. Playlist/position provider недоступны accessibility/safe logs, поэтому
  actual resume across reopen не подтверждён.
- Случайная mutation «Spider-Man» восстановлена адресно: кнопка `В избранное`, после
  `Не смотрел` материал отсутствует в серверном «Все» (10/10). `pm clear`, uninstall и
  broad DataStore clear не выполнялись.

Это focused evidence, а не полный release pass: Web fallback resume, cross-season/natural
end, live updater/Package Installer и остальные пункты матрицы остаются отдельными.

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
- все compatible episode coordinates выбранных source/voiceover образуют один ordered
  Media3 playlist через границы сезонов; in-memory preload ограничен только непосредственно
  следующей item и target duration 2–5 секунд из buffer policy, без disk cache и отдельного
  resolver warmup;
- естественное окончание фильма, последней доступной серии или любого эпизода при
  отключённом auto-next возвращает в details;
- exit/player error writes checkpoint without transient URL;
- initial buffering дольше `max(20, выбранный запас)` секунд, повторная буферизация дольше
  `clamp(запас, 5, 10)` секунд и READY с `playWhenReady=true` без продвижения позиции
  дольше 15 секунд запускают не более одного fresh-source recovery для текущей
  content/season/episode unit даже без Media3 error;
- пауза, suppression, завершившаяся единица и нормально продвигающаяся позиция не создают
  ложный watchdog recovery; near-end `READY`/`BUFFERING` без прогресса остаётся recoverable;
  во время refresh старый player больше не продолжает играть;
- после обычного Back и после failed recovery Details содержит свежую карточку и активное
  действие `Смотреть`/`Продолжить`, а повторный запуск создаёт новое поколение Media3 session;
- checkpoint writes сериализованы и generation-scoped: поздний callback предыдущего player
  не может перезаписать новую серию, ручной SEEK сохраняется перед выбором другой серии, а
  manual/automatic переход через сезон сначала фиксирует старую unit и затем активирует
  новую, включая позицию `0`;
- завершение и новая unit не восстанавливают случайную ранее просмотренную серию; история
  объединяет persistent progress со snapshot вместо замены одного другим;
- `Авто` оставляет адаптивный выбор Media3. Для фиксированного пожелания применяется точное
  качество, если оно доступно; иначе выбирается максимальное доступное не выше предела; если
  все варианты выше — минимальное доступное. Политика сравнивает adaptive manifest tracks и
  отдельные fixed variants вместе, распознаёт `4K` как 2160p и сохраняет исходный предел для
  следующих серий, не подменяя его фактическим fallback-вариантом. Отдельный
  `PlaybackPlaylistNavigationTest` фиксирует смену intent `1080p → 720p`: current E1 с
  единственным 720p остаётся тем же объектом, а future E2 меняется с 1080p на 720p до preload;
- native HUD не содержит ручной кнопки refresh: recovery автоматический, а после его
  исчерпания новый fresh attempt запускается из Details действием `Смотреть`/`Продолжить`.

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

### Integration matrix 0.5.2

| Контракт | Protective source evidence | Evidence C-008 |
| --- | --- | --- |
| Stall recovery без Media3 error | `PlaybackSourceRefreshTest` watchdog cases | Local canonical **PASS**; TV pending |
| Новый player generation после fresh source | `PlaybackSourceRefreshTest`, root session guards | Local canonical **PASS**; TV pending |
| Активное Play/Continue после exit/failed recovery | `KinogoAppRootResumeTest` + Details fresh-cache guard | Local canonical **PASS**; TV pending |
| Serialized/generation-scoped checkpoint | `KinogoAppRootResumeTest`, `PlaybackProgressCodecTest` | Local canonical **PASS**; TV pending |
| Manual SEEK, cross-season и zero-position activation | `KinogoAppRootResumeTest`, player transition guards | Local canonical **PASS**; TV pending |
| Fixed quality cap across adaptive/fixed candidates и future playlist rebuild | `PlaybackQualityPolicyTest`, `PlaybackSourceSelectionModelTest`, `PlaybackPlaylistNavigationTest`, `PlaybackQualitySwitchGuardTest` | Local canonical **PASS**; actual tracks on TV pending |
| Buffer dropdown/LoadControl/recovery mapping | `PlaybackBufferPolicyTest`, preferences/store/UI mapper tests | Local canonical **PASS**; TV pending |
| Next episode bounded cross-season preload + future-error isolation | `PlaybackMediaPlanTest`, `PlaybackBufferPolicyTest`, `PlaybackPreloadFailurePolicyTest` | Local canonical **PASS**; network/TV pending |
| Settings update controls grouped last | source/UI contract review | Source review **PASS**; TV focus pending |
| Native HUD manual refresh removed | source/UI contract review | Source review **PASS**; TV pending |
| Updater 0.5.1 → 0.5.2 до OS confirmation | signed manifest/artifact verification | Exact APK local **PASS**; signed manifest/publication/runtime **PENDING** |

C-008 имеет version code 16 / version name `0.5.2`; exact commit, local tests/lint и APK
зафиксированы выше. CI URL, signed manifest, publication status и hardware behavior ещё
неизвестны. Кандидат может быть опубликован как validation release для ручной проверки
updater владельцем, но это не создаёт baseline tag и не разрешает утверждать, что плеер или
TV UX аппаратно проверены.

### Historical integration matrix 0.5.1

| Контракт | Automated guard в source | Финальное/TV evidence C-007 |
| --- | --- | --- |
| Exact-host runtime player document | `CinemarEmbedResolverTest`, `KinogoPlaybackPreparationServiceTest` | KIVI current `cinemar.cc` runtime route → native selector verified |
| Deferred Cinemar parse | `CinemarNativeSourceAdapterTest` + fixture | KIVI selector voices/seasons 1–4/episodes verified |
| Exact selected-leaf grant POST | `CinemarGrantClientTest` | KIVI native Media3 S2E5 >15 с verified; token/media URL redacted |
| Lazy/session-owned/single-flight resolver | `CinemarDeferredGrantRegistryTest`, `CinemarDeferredGrantPlaybackPlanTest` | KIVI selected-unit Media3 open verified; concurrent-open runtime not isolated separately |
| True source-destination Back | `KinogoTvInitialFocusTest` + source navigation guards | Player → Details → History verified; History/Search non-first focus verified |
| Search query/results/focus + 10 recent | `SearchHistoryStoreTest`, `TvPosterGridTest` | `Chris` + second result restored; recent row verified |
| About first + focusable logo | `SettingsScreenDpadTest`, `KinogoNavigationRailTest` | Final local canonical run green; TV reachability pending |
| Web fallback pause/flush-before-dispose | `CinemarWebViewRecoveryStateTest` | Fullscreen launch + Back → Details → History passed; actual same-profile playlist item/position reopen pending |
| Signed manifest schema/signature/expiry | `SignedUpdateManifestParserTest` | Exact local manifest verified; commit/live endpoint pending |
| Multi-endpoint agreement/download fallback | `SignedManifestUpdateClientTest`, `FallbackAppUpdateClientTest` | Final local canonical run green; live outage scenario + OS confirmation pending |
| GitHub Release fallback | `GitHubReleaseParserTest`, `ApkUpdatePolicyTest` | Final local canonical run green; actual fallback release/installer pending |

Матрица опирается на записанный выше final local canonical pass, но не доказывает final
commit, CI, publication или runtime. Default Pages и jsDelivr metadata endpoints до
проверки успешного deployment/cache refresh
считаются pending, а не доказанным alternative channel. Планируемые `ghfast.top` и
`ghproxy.net` — best-effort transports без trust/SLA; их наличие в signed payload доказывает
только адрес, а не доступность или целостность байтов.

### Historical integration matrix 0.5.0

| Контракт | Automated guard в source | Финальное/TV evidence |
| --- | --- | --- |
| Cold initial rail focus | `KinogoTvInitialFocusTest` | KIVI debug smoke passed |
| Settings stable IDs/store/mapping | `TvPreferencesTest`, `TvPreferencesStoreTest`, `TvPreferencesUiMapperTest` | KIVI Switch/dropdown passed |
| Newest unfinished resume | `KinogoAppRootResumeTest` | Basic `Продолжить с 0:14` passed; multi-episode/restart pending |
| Bounded source refresh | `PlaybackSourceRefreshTest` + `KinogoAppRootResumeTest` safety guards | PENDING live expired/404 source |
| Cross-season force-play/end policy | `PlaybackCompletionPolicyTest` | PENDING natural end on TV |
| Registration rules/form/CAPTCHA | parser/API tests + `RegistrationDialogDpadTest` | `OK (1)`: default decline, scroll-boundary escape, explicit accept; live submit pending |
| Remote mirror bootstrap | `MirrorBootstrapClientTest` | PENDING network/expiry/health smoke |
| GitHub release/update policy | `GitHubReleaseParserTest`, `ApkUpdatePolicyTest` | PENDING actual Release asset + OS confirmation |
| About QR/external actions | resource/hash + exact URL source review | KIVI D-pad/QR + Yandex TV browser actions passed |

GitHub Actions workflow выполняет canonical clean-clone unit/lint/assembleDebug на push в
`main` и pull request с JDK 17 / SDK 37. Первый remote run для final C-006 commit должен быть
записан отдельно; локальный green run не доказывает GitHub environment и наоборот.

Registration security matrix дополнительно проверяет: rules и account form не смешиваются;
`dle_rules_accept` не отправляется до явного consent; sensitive fields не используют
`rememberSaveable`; late async result имеет generation+origin guard; CAPTCHA transport
ограничен 512 KiB, а UI decode — 4096 px на сторону, 8 млн pixels и downsample до
840×256 RGB_565.

Recovery-loop matrix дополнительно покрывает три pure safety guards: attempted-unit budget
объединяется и записывается до disposal failing player; ordinary preparation не меняет
budget/discard semantics; missing content либо active mirror во время recovery превращаются
в явную ошибку. Error/Back очищают dead session и возвращают в Details, поэтому late
preparation не может воскресить player. Отдельный exact-unit guard не переносит position на
другой episode.

### Final Release smoke 0.5.0

- Device: X96Max Plus Ultra, Android TV 14, `10.173.44.46`.
- `adb install -r` успешно; `firstInstallTime` сохранён: `2026-08-14 08:34:38`.
- Installed base APK: 38 140 638 bytes; SHA-256 точно совпал с staged artifact.
- Staged metadata: `com.kinogo.atv`, code 14 / `0.5.0`, minSdk 28, targetSdk 37, label
  `KinogoATV`, LEANBACK banner; zipalign OK, v2 true; certificate SHA-256
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.
- Cold launch: `Status: ok`, `TotalTime: 1023 ms`; initial focus — Home rail.
- Catalog/posters loaded; итоговый logcat не содержит FATAL/ANR.
- `RegistrationDialogDpadTest`: `OK (1)`; test package удалён, target app/data не очищались.

Этот smoke не включает live registration submit, actual expired-source refresh, natural
cross-season end или newer-version Package Installer.

### Evidence для границы сезона и natural end

| Сценарий | Unit/contract evidence | Текущий C-006 |
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

Для Cinemar C-007 отдельно проверять: discovery только через strict `/embed/...`, already
discovered player document через exact-host `validatedPlayerDocumentUri`, leaf с opaque
`data`, exact same-origin `/api/playlist/load`, один POST только для selected leaf,
отсутствие cookie/redirect/retry и HLS-only result. В evidence не включать grant token,
точный runtime path, iframe/media URLs и cookies. Начало реального Media3 воспроизведения
на KIVI подтверждено; это не заменяет проверки других материалов, TTL/error и cross-season.

Для signed updater проверка считается закрытой только если exact payload
проходит installed-signer signature, endpoint доступен из целевой сети, APK
совпадает по size/SHA/package/version/signer и Android показал системное
подтверждение. HTTP 200 manifest без этих шагов не доказывает live updater.

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
