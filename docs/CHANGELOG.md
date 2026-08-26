# История изменений

Формат близок к Keep a Changelog, но ранние версии являются development milestones, а не
стабильными SemVer releases.

Важно: до 29 июля 2026 года локальная ветка не имела commit history. Ранние записи ниже
честно реконструированы по APK в `dist/SHA256SUMS.txt`, датам файлов, тестам и
пользовательскому циклу проверки. Это milestone history, не точный список коммитов.

## [0.5.3] — 2026-08-26 (validation release)

### История и навигация

- Короткое нажатие `OK` на постере Истории теперь открывает обычную карточку материала,
  как Главная, Каталог, Поиск и Закладки. Запуск/продолжение остаётся отдельным действием
  внутри карточки, а `Back` возвращает в Историю с тем же фокусом.
- Долгое нажатие `OK`/`Enter` или long tap открывает TV-диалог управления записью. Можно
  удалить выбранный фильм/сериал либо очистить всю историю; начальный фокус диалога всегда
  безопасно стоит на `Отмена`, а `Back` ничего не удаляет.
- Одна карточка сериала удаляет все его episode checkpoints, а не только новейшую серию.
  Операции удаления проходят после уже поставленных в очередь checkpoint writes, поэтому
  запоздалая запись не восстанавливает удалённый материал. Полная очистка удаляет только
  playback history и не затрагивает настройки, аккаунт или закладки.
- После удаления фокус переходит на следующий постер в прежней визуальной позиции, иначе
  на предыдущий; после очистки/удаления последнего элемента — на текущий пункт rail.

### Сохранение прогресса

- Найден и исправлен путь ложного «сброса»: выбранный stable playback `sourceId` раньше
  терялся при преобразовании UI selection в сохраняемый domain checkpoint. После нового
  запуска мог выбираться другой provider plan, а несовпавшая серия корректно стартовала с
  нуля, хотя запись позиции оставалась в DataStore.
- Формат истории обновлён до v3 и сохраняет безопасный source adapter ID вместе с
  сезоном, серией, озвучкой, качеством и позицией. V1/V2 читаются обратно совместимо;
  source ID не входит в ключ episode history и поэтому смена источника не дробит запись.
- Direct-media checkpoint сохраняет только внутренний `direct-media`, а не недоверенный
  HTML provider label, hostname зеркала или параметры краткоживущего URL.
- Имя Preferences DataStore и ключ истории не менялись. Обычное обновление APK с той же
  подписью не очищает позиции; уже случившуюся потерю без старого состояния устройства
  нельзя достоверно отличить от uninstall/clear data или иной очистки данных.

### Автообновление

- Результат автоматической проверки при запуске больше не скрыт внутри Настроек: найденная
  новая версия показывает глобальный D-pad-диалог `Позже`/`Загрузить` поверх текущего
  раздела. Загрузка, проверка APK и установка используют прежний защищённый pipeline;
  silent install не добавлялся.
- Для автоматической проверки добавлена ровно одна отложенная повторная попытка после
  временной стартовой ошибки. Ручная проверка остаётся одноразовой и немедленной.
- Signed manifest запрашивается с `Cache-Control: no-cache`, при этом URL policy,
  криптографическая подпись manifest и повторная проверка APK не ослаблены.

### Validation status

- Application source: `777c8a0528f24db67402536631257d6cdc91f148`; code 17 / `0.5.3`,
  minSdk 28, targetSdk 37.
- Canonical `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
  assembleRelease` завершён **SUCCESS за 7 мин 12 с**: **89 suites / 455 tests**, 0
  failures/errors/skips; lint — **0 errors / 22 warnings / 2 hints**. Post-commit
  `assembleRelease --rerun-tasks` завершён **SUCCESS за 4 мин 04 с**.
- Exact stable-signed APK `dist/KinogoATV-0.5.3-code17.apk`: 38 386 398 bytes, SHA-256
  `3C88DF356A9815865DB02F7821DA53BE3C6E25F03FE493516FCCAF0F48F0C17A`; package
  `com.kinogo.atv`, code 17 / `0.5.3`, minSdk 28, targetSdk 37, LEANBACK launcher/banner,
  zipalign PASS, v2 true, ровно один сертификат SHA-256
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`; embedded revision
  точно совпадает с application source.
- Application/docs [PR #5](https://github.com/reziarlleh/KinogoATV/pull/5), merge
  `0473a820eefedea16ce2f393df568c90e5b30bbe`; PR CI `32920452170` и main Android CI
  `32920746857` завершены SUCCESS.
- Annotated tag `v0.5.3` указывает на application/docs merge. Regular latest
  [Release 0.5.3](https://github.com/reziarlleh/KinogoATV/releases/tag/v0.5.3)
  опубликован `2026-08-26T01:59:56Z` с `draft=false`, `prerelease=false`;
  exact APK asset имеет GitHub digest
  `sha256:3c88df356a9815865db02f7821da53be3c6e25f03fe493516fccaf0f48f0c17a`.
- Signed manifest source `7faebbba8d305a0c339f6966e7759ec7c7f96b90`,
  [PR #6](https://github.com/reziarlleh/KinogoATV/pull/6), merge
  `ff7f5f8eea9776ef626010fe57993dc1906f5d4a`; PR CI `32921520976` — SUCCESS.
  Final code 17 manifest — 1 273 bytes, SHA-256
  `860D90C22D9F404A38E783BD313A9E9A0FDEFC5BC870F933A819D35145489977`, issued
  `2026-08-26T02:04:54Z`, expires `2026-09-25T02:04:54Z`.
- Final main Android CI `32921627748` — SUCCESS за 1 мин 04 с; Pages
  `32921627746` — SUCCESS за 45 с. Live exact bytes: Pages manifest+APK PASS,
  jsDelivr manifest PASS после точечной purge, ghfast/ghproxy/direct GitHub APK
  совпали по size/SHA-256.
- ADB и реальный TV не использовались по прямому указанию владельца. D-pad long-press,
  сохранение реальной позиции после обновления и in-app updater runtime остаются ручной
  приёмкой владельца; baseline tag не создавался.

### Инструменты разработки — 25 августа 2026 года

- Для checkout `D:\_codex\KinogoATV` локально инициализирован RepoWise 0.45.0 в режиме
  `--no-prose`: 246 файлов, 4 700 символов, 524 структурные страницы, без LLM-вызовов,
  API-ключа и token spend. Реальные `status`, `context`, `health` и `doctor` завершились
  успешно.
- Установлен неблокирующий post-commit hook для инкрементального обновления индекса.
  Автогенерация managed-блока `AGENTS.md` затем отключена: практический post-commit test
  показал, что commit/health snapshot меняет tracked-файл после каждого commit. Исходные
  обязательные правила KinogoATV сохранены без генерируемого churn; Codex получает RepoWise
  через локальные SessionStart/MCP hooks.
- Производная `.repowise/` и editor/MCP-файлы с абсолютным локальным путём исключены из Git.
  Они сохраняются в текущем checkout, а после clone/move восстанавливаются повторным `init`.
  Это изменение developer tooling не меняет APK или playback baseline.

## [0.5.2] — 2026-08-23 (validation candidate)

### Автоматическое восстановление плеера

- Добавлен one-shot watchdog зависания, связанный с выбранным target buffer: initial
  timeout равен `max(20 с, target)`, rebuffer timeout — `clamp(target, 5–10 с)`, а `READY` без
  продвижения — 15 с.
  Попытка не запускается при паузе, suppression или состоянии `ENDED`; близость позиции к
  известному duration не маскирует реальный near-end `READY`/`BUFFERING` stall.
- Ошибка или watchdog запрашивают свежий source plan и увеличивают generation сессии,
  поэтому Media3 точно создаётся заново даже при структурно том же media plan.
  Восстановление сохраняет exact unit и позицию и имеет ограничение в одну попытку.
- Удалена ручная кнопка «Обновить источник» из native HUD. Если одна автопопытка
  не помогла, явный повтор выполняется через Back → Details → «Смотреть».
- `PlaybackMediaPlan.episodeCoordinatesFor` разворачивает все совместимые сезоны в один
  Media3 playlist. Preload immediate-next включается только для сериала с auto-next при
  активном несупрессированном воспроизведении: до конца осталось не больше target buffer, а
  конец уже загружен как минимум до `duration - 500 ms`. Target равен 2,5 с при buffer 5 с и
  5 с при 10–30 с, в том числе на границе сезонов. Pause/suppression/close/transition и seek
  назад отключают ранний open; disk media cache и отдельный token/resolver warmup не добавлялись.
- Нефатальная ошибка предзагрузки будущей серии лишь отключает её раннюю загрузку и не
  расходует recovery текущей. Terminal failure обрабатывается только когда exact следующая
  window действительно стала текущей; события прежней playlist generation игнорируются.

### Resume, checkpoint и карточка

- Записи checkpoint сериализованы очередью. Session generation и монотонное время
  не дают запоздалой записи старого player instance перетереть более новый progress.
- Переход к новой серии или сезону сразу фиксирует активную unit даже на позиции
  `0`, чтобы при возврате не выбиралась ранее просмотренная серия. Более новый completed
  checkpoint подавляет устаревшую unfinished-запись.
- Локальные history и progress сливаются по newest timestamp вместо перезаписи одного
  множества другим.
- Свежая detail page сохраняется в root cache, поэтому после выхода из плеера
  кнопка «Смотреть» остаётся доступной без повторного выхода из карточки.

### Качество и настройки

- Desired quality отделено от фактического media variant и сохраняется при смене серии.
  Выбор: exact quality, иначе highest available `<=` заданного, а если все варианты выше —
  lowest available above. Политика одинаково сравнивает адаптивные треки и отдельные fixed URLs.
- При смене quality intent episodic playlist пересчитывает fixed variants будущих серий до
  preload/перехода. Текущие variant, индекс, позиция и play state сохраняются; opaque grant
  текущей серии не запрашивается повторно.
- Добавлен dropdown «Буфер воспроизведения»: 5/10/15/20/30 с, default 15 с. Media3
  `DefaultLoadControl` получает `minBuffer=maxBuffer=target`, playback start — `target/3`
  с clamp 1–2,5 с, rebuffer start — `target/2` с clamp 2–5 с; включён time-priority.
- Пункты проверки и автопроверки обновлений собраны в конце Settings.
- Удалён неиспользуемый arrow-cycle path настроек; рабочий контракт остаётся Switch либо
  D-pad dropdown с выбором по `OK`.

### Validation status

- Source metadata: code 16 / `0.5.2`, minSdk 28, targetSdk 37.
- Application source:
  `4cfa7ac8ebd48b70c7b172e54a0716fec09669a1`. Canonical
  `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease` —
  SUCCESS за 5 мин 20 с: 87 suites / 441 tests, 0 failures/errors/skips; lint 0 errors /
  22 warnings / 2 hints. Post-commit `assembleRelease --rerun-tasks` — SUCCESS за 5 мин 29 с.
- Exact stable-signed APK `dist/KinogoATV-0.5.2-code16.apk`: 38 353 630 bytes, SHA-256
  `FC70D02A2BC7A3F9E5E2F04A1A7B139037AC215C85166E72E9842D0DB3CB4B38`; package
  `com.kinogo.atv`, code 16 / `0.5.2`, minSdk 28, target/compile SDK 37, LEANBACK
  launcher/banner, zipalign OK, v2 true, embedded revision `4cfa7ac`, certificate SHA-256
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.
- Репозиторий переведён в public. Application/docs merged как `08c90c9`; tag `v0.5.2`
  опубликован regular latest Release (`draft=false`, `prerelease=false`) с exact APK и
  lowercase GitHub digest, совпадающим с SHA-256 выше.
- Final signed code 16 `update/manifest.json` опубликован merge
  `367bcf288dd5b3ad729af94d9b21308e5c96354c`: 1 273 bytes, SHA-256
  `BCB6699708CC2C6FF4A71F8379032F709742AC714440622F179130D5AFA80E94`, issued
  `2026-08-22T21:02:03Z`, expires `2026-09-21T21:02:03Z`, четыре download URLs.
- Android CI run `32598900494` и Pages run `32598900503` завершены SUCCESS. Live exact bytes
  подтверждены для Pages manifest+APK, jsDelivr manifest и APK через ghfast, ghproxy,
  direct GitHub. Эти каналы дают transport diversity, но operator-owned независимый host
  остаётся **PENDING**.
- APK 0.5.2 на TV не устанавливался. Hardware playback и runtime updater validation
  **PENDING** по выбору владельца; C-007 / `0.5.1` evidence к C-008 не переносится.

## [0.5.1] — 2026-08-21 (validation candidate)

### Native playback и Web fallback

- Адаптер Cinemar обновлён под текущий browser-visible контракт: playlist leaf с
  непрозрачным `data` лениво обменивается same-origin JSON-string POST на
  `/api/playlist/load` только при запуске выбранной единицы.
- Введён session-owned bounded grant registry с локальными случайными Media3 URI,
  single-flight/memoization одного leaf и fail-closed HLS-only resolution. Grant token,
  iframe и transient media URL не попадают в логи и persistence; grant transport не переносит
  cookies и не следует redirect.
- Исправлен актуальный authenticated Cinemar flow: карточка может вернуть exact-host
  `cinemar.cc` player document на непрозрачном runtime route, а не публичный `/embed/...`.
  Для уже найденного player document введена отдельная строгая проверка exact HTTPS host:
  запрещены root, `/api/`, query, fragment, userinfo и нестандартный порт; discovery новых
  предложений по-прежнему допускает только `/embed/...`.
- Deferred grant всегда строит отдельно фиксированный same-origin `/api/playlist/load` и
  выполняется без cookies, redirect и retry. Прежний общий `/embed/` validator отклонял
  текущий runtime route как `INVALID_EMBED_ADDRESS`, из-за чего оставался только web fallback.
- Explicit Cinemar Web fallback сохраняет first-party PlayerJS state в своём
  WebView profile. Back теперь отправляет `pause` и ждёт callback перед dispose;
  после чего вызывает `CookieManager.flush()` только для internal WebView profile.
  `stop`, сбрасывающий provider checkpoint, не используется. Это не межустройственная
  синхронизация и не перенос native checkpoint.

### Back, поиск и About

- Player Back по-прежнему ведёт в Details, а Details Back теперь возвращает в
  исходный Home/Catalog/Search/Library/History, а не всегда в Home.
- Root хранит stable ID последней карточки отдельно для Home, Catalog, Search, Bookmarks и
  History; при возврате сетка восстанавливает non-first item, если он ещё есть в той же
  feed identity. Смена category/filter/query адресно сбрасывает только соответствующий ID.
- Search query, уже загруженная выдача и stable ID фокуса живут на root-уровне,
  поэтому возврат из карточки восстанавливает прежний запрос, результаты и
  последнюю активную карточку.
- До десяти последних подтверждённых поисков дедуплицируются, сохраняются локально
  и показываются в одной горизонтальной строке. Запись делается только после
  OK/Enter, голосового результата или выбора chip; debounce-выдача не засоряет history.
- Карточка «О программе» перенесена наверх Settings и увеличена. Логотип в
  navigation rail стал отдельным D-pad action для открытия того же About dialog.

### Подписанный многоканальный updater

- Updater теперь в первую очередь читает до четырёх signed manifest endpoints.
  Envelope проверяется public key установленного APK; payload связывает
  version/name/code, exact asset size/SHA-256, срок не более 90 дней и набор HTTPS
  download mirrors.
- Default metadata URLs настроены на GitHub Pages и jsDelivr CDN; дополнительные
  endpoints можно зашить в APK через `KINOGO_UPDATE_MANIFEST_URLS`. Если подписанные endpoints
  недоступны, остаётся strict GitHub Release API fallback.
- Для C-007 планируются четыре signed download URLs: Pages, best-effort
  `ghfast.top`, best-effort `ghproxy.net` и direct GitHub Release. Прокси не являются
  trusted: их ответ принимается только после signed size/SHA-256 и повторных
  package/version/signer checks. Operator-owned non-GitHub storage ещё не настроено.
- Даже после верной manifest-подписи APK повторно проходит existing
  package/version/code/size/SHA/signer checks, а установка остаётся явным
  системным диалогом Android.

### Validation status

- Source metadata: code 15 / `0.5.1`, minSdk 28, targetSdk 37.
- Добавлены fixtures и unit/contract guards для deferred Cinemar parser/grant transport/
  session registry, истории поиска, Back/focus и signed/fallback updater policy.
- Final local canonical run
  `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease`:
  **SUCCESS за 4 мин 27 с**, **82 suites / 393 tests**, 0 failures, 0 errors, 0 skipped;
  lint **0 errors / 22 warnings / 2 hints**.
- Exact local release APK `dist/KinogoATV-0.5.1-code15.apk`: **38 304 478 bytes**,
  SHA-256 `3166898FDFA882DB9A637ECDA6CDA612A5AF0B5F70D30580FD1449A906EBF875`;
  package `com.kinogo.atv`, code 15 / `0.5.1`,
  minSdk 28, targetSdk 37, LEANBACK launcher/label `KinogoATV`, zipalign OK, v2 true,
  certificate SHA-256
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.
- Final local `update/manifest.json`: **1 273 bytes**, SHA-256
  `3C167F87208077E6EC4717F202F968AD555B800C76043CFCF69B941627323070`, code 15 /
  `0.5.1`, `issuedAtEpochSeconds=1787294465`, `expiresAtEpochSeconds=1794984054`
  (18 ноября 2026 года, 06:40:54 UTC) и четыре download URLs.
- Stable-signed APK установлен через `adb install -r` на KIVI Android TV 14
  (`192.168.1.112`) с сохранением `firstInstallTime=2026-07-26 16:42:18`. Из Истории
  «Далеко во Вселенной» открыл native selector Cinemar с озвучками, сезонами 1–4 и
  сериями; `Продолжить` показал 10:48, Media3 воспроизвёл S2E5 с продвижением 11:01 → 11:39.
  `OK` показал HUD без паузы; Back вернул Player → Details → History.
- Отдельно подтверждён non-first History focus: вторая карточка «История его служанки»
  после source/details chain и двойного Back снова получила `focused=true`. В Search запрос
  `Chris`, результаты и вторая карточка «Рождественская неделя» также восстановлены с
  `focused=true` после physical Back.
- Адресно восстановлено случайно изменённое состояние «Spider-Man»: кнопка снова
  `В избранное`, а после `Не смотрел` материал отсутствует в серверном разделе «Все» (10/10).
- Application source commit: `8b0be72cf32d6807f0dc4ff5c5e21da95e847874`. CI, GitHub
  Release и Pages/jsDelivr deployment — **PENDING**; local и TV evidence не объявляют
  публикацию updater channel.
- B-001 / `0.3.3-dev` остаётся последним полным playback rollback baseline; C-006 /
  `0.5.0` сохраняет свой ранее записанный build/device evidence.

## [0.5.0] — 2026-08-15 (validation candidate)

### TV-интерфейс и продолжение просмотра

- Cold start теперь передаёт начальный фокус выбранному пункту постоянного rail; разделы не
  перехватывают его до явной активации контента. Focused rail получил яркую бирюзовую
  заливку и белый левый маркер, а active-unfocused остаётся различимым.
- Усилен фокус общих action/chip/icon-кнопок и строк настроек; poster focus не менялся.
- После открытия Details и возврата из плеера primary playback action получает ограниченный
  пятикадровый focus retry.
- Boolean-настройки показываются Switch, а качество, шаг перемотки и субтитры выбираются
  D-pad dropdown с возвратом фокуса. Left/Right остаются навигацией; добавлен включённый по
  умолчанию Switch автопроверки обновлений.
- History, Catalog и Search используют одну policy: выбирается newest unfinished checkpoint
  материала. Завершённая default-серия больше не маскирует более новую незавершённую, а
  Details показывает `Продолжить SxxExx с mm:ss`.

### Playback reliability

- При Media3 error приложение может один раз на `content/season/episode` заново загрузить
  details и provider plan, нормализовать выбор и восстановить позицию. Attempt set
  переносится в replacement player, поэтому inter-screen retry loop невозможен; transient
  URL не сохраняются. Автоматическое продолжение позиции разрешено только если свежий plan
  сохранил точную исходную единицу content/season/episode; иначе открывается обычный selector
  с позицией `0`, без незаметного запуска другой серии.
- Consumed attempted-unit budget записывается в active session до закрытия failing Media3.
  Если recovery останавливается до подготовки из-за Back, отсутствующей карточки или
  проверенного зеркала, dead player не может воскреснуть: показывается явная ошибка, а Back
  возвращает в Details. Добавлены три pure guards для budget persistence, early prerequisite
  errors и exact same-unit position.
- End-of-item pause при включённом auto-next больше не завершает flow до решения completion
  policy. Первая совместимая серия следующего сезона явно запускается даже после сброса
  `playWhenReady`; при отключённом auto-next возврат в Details сохранён.

### Аккаунт, зеркала и обновления

- Добавлена двухшаговая регистрация через browser-visible same-origin DLE flow. Если сервер
  сначала показывает правила, безопасное действие по умолчанию — `Не принимаю`, а POST
  `dle_rules_accept` выполняется только после явного выбора пользователя. Затем image CAPTCHA
  загружается той же cookie-сессией с лимитом 512 KiB и проверкой типа; bitmap decode
  ограничен 4096 px на сторону/8 млн pixels и downsample до 840×256. Sensitive input живёт
  только в `remember`, а generation+origin guard отбрасывает устаревшие ответы после
  retry/dismiss/смены зеркала. Код CAPTCHA вводит пользователь. Refresh перезагружает форму целиком, а
  reCAPTCHA/hCaptcha/Turnstile явно unsupported и не обходятся.
- Добавлен bounded remote bootstrap `config/mirrors.json`: exact-schema manifest с
  operator-controlled GitHub raw path может только добавить `DISCOVERY + QUARANTINED`
  origins. Текущий snapshot содержит `w.kinogo.solar`, `kinogo.parts`, `kinogo.online` и
  `kinogo.family`. Manifest не подписан и не заменяет HTTPS/public-DNS/service-fingerprint
  check.
- Добавлен updater stable GitHub Release: exact asset name/digest/size, package/version и
  signing certificate проверяются до передачи APK через non-exported FileProvider.
  Разрешение unknown sources и финальная установка остаются системными экранами Android с
  обязательным подтверждением пользователя; silent install отсутствует.

### About, публикация и документация

- Название приложения приведено к `KinogoATV`; в Настройки добавлен About dialog с версией,
  non-affiliation/no-hosting disclaimer и exact allowlisted ссылками на GitHub и
  [Donate.Stream](https://donate.stream/donate_6a60559cd9e35).
- `donate_qr.png` предоставлен непосредственно владельцем репозитория и добавлен без
  изменений; SHA-256:
  `C8DCA7846A344DC83563BA338AB6691286C482A3E612C3083F0CB2D6D042BEEE`.
- README подготовлен для публичного репозитория: явно указаны неофициальный статус,
  отсутствие аффилиации и отсутствие hosting видео.
- Добавлен GitHub Actions workflow для JDK 17 / SDK 37 и canonical
  `testDebugUnitTest lintDebug assembleDebug` clean-clone проверки. Official Actions
  закреплены полными commit SHA актуальных Node 24-релизов.

### Validation status

- Source version: application commit `6567088`, code 14 / `0.5.0`, minSdk 28, targetSdk 37.
- Добавлены/обновлены protective tests для resume, source refresh, completion, registration,
  mirror bootstrap, updater, preferences, initial rail focus и Settings D-pad contract.
- Локальные `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
  assembleRelease` завершены успешно: **75 suites / 348 unit tests**,
  0 failures/errors/skips; lint — 0 errors, 19 warnings и 2 hints.
- На KIVI debug smoke подтвердил cold rail focus, Settings Switch/dropdown, About/QR и exact
  links через Yandex TV browser, а также короткий native playback с возвратом в Details и
  focused `Продолжить с 0:14`. D-pad instrumentation rules gate подтвердил default decline
  и accept только явным OK.
- Stable-signed `dist/KinogoATV-0.5.0-code14.apk` (38 140 638 bytes, SHA-256
  `3650C44B40A7AC066F98B597E0831BB800512CA5695EBD554DDD5620E15ED52B`) прошёл metadata,
  zipalign, v2 и certificate verification. Certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.
  Metadata: `com.kinogo.atv`, code 14 / `0.5.0`, minSdk 28, targetSdk 37, label
  `KinogoATV`, LEANBACK banner.
- Final Release APK установлен через `adb install -r` на X96Max Plus Ultra Android TV 14;
  `firstInstallTime` сохранён, installed base hash/size совпали, cold launch занял 1023 ms,
  Home rail получил initial focus, каталог/постеры загрузились, FATAL/ANR нет.
- Final hardware `RegistrationDialogDpadTest` — `OK (1)`: rules scroll boundary возвращает
  focus на безопасное `Не принимаю`; test package удалён.
- First GitHub Actions run и публичный GitHub Release **pending**. Live
  registration submit, реальный expired-source refresh, natural cross-season end и
  newer-version installer **pending**. B-001 остаётся playback rollback baseline.

## [0.4.3-dev] — 2026-08-01

### Надёжность сортировки

- Исправлена ошибка `Не удалось загрузить каталог`, возникавшая на Главной при сортировке
  по рейтингу и на части сортировок Каталога. На KIVI причиной был timeout ожидания
  HTTP/2 response headers внутри stateful DLE/xSort-последовательности, а не отсутствие
  соответствующих server sort values.
- Origin-scoped DLE session transport переведён на HTTP/1.1. Изменение изолировано от
  playback-клиентов.
- После неоднозначного network failure репозиторий один раз повторяет всю транзакцию от
  `clearallfields`. Отдельный меняющий xSort POST намеренно не повторяется: одна и та же
  команда может переключить направление и исказить результат.
- Applied-query cache инвалидируется после неоднозначной ошибки или cancellation.
  Устаревший reset той же ленты отменяется, а уже показанная Home/Catalog-выдача остаётся
  видимой при transient reset failure.
- Удалён невидимый прогрев Каталога, который создавал лишнюю конкурирующую xSort-транзакцию.
  Каталог загружается непосредственно при переходе в раздел; стартовый резерв Главной и
  ранняя дозагрузка сеток сохранены.
- Добавлены unit guards для timeout после уже применённого mutating POST и ограниченного
  единственной полной повторной транзакцией восстановления.

### Validation

- Application source commit: `15efacc`; version code 13, `0.4.3-dev`, minSdk 28,
  targetSdk 37.
- Каноническая команда завершена успешно: **68 suites / 309 unit tests**, 0 failures,
  0 errors и 0 skipped; lint — 0 errors, 7 warnings и 2 hints; `assembleDebug` успешен.
- Stable-signed APK `dist/KinogoTV-0.4.3-dev.apk` прошёл zipalign и v2 verification;
  certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`;
  artifact SHA-256:
  `5A3EAAF4A23663AE73FE987CFDCEE6F311ED4AFD3A48B29833C44C5DAB5F67E9`.
- `adb install -r` на KIVI 4K Android TV 14 сохранил `firstInstallTime`
  (`2026-07-26 16:42:18`). Финальный cold launch занял 2504 ms.
- На Главной и в Каталоге без ошибки загрузились все семь server sorts: дата, рейтинг, топ
  за 3 дня, просмотры, комментарии, год и рейтинг Кинопоиска. Для рейтинга проверены ASC и
  DESC, выдача изменилась в соответствии с направлением.
- Финальный logcat не содержит catalog error, fatal exception или ANR.

Кандидат C-005 закрывает аппаратную проверку всех видов сортировки, но не заменяет playback
baseline B-001: combinations подборки/года/страны, длинная пагинация и полный player
regression для этого APK остаются pending.

## [0.4.2-dev] — 2026-08-01

### Ленты и запуск

- Общая poster grid теперь начинает дозагрузку, когда ниже текущего фокуса остаётся меньше
  двух уже загруженных строк. Главная, Каталог и Поиск используют один и тот же ранний
  preload-контракт.
- После запуска Главная последовательно получает страницы, пока не накопит минимум 18
  уникальных карточек либо сервер не завершит пагинацию. Это предотвращает видимый обрыв
  ленты до того, как пользователь дойдёт до нижних рядов.
- Начальная загрузка Главной имеет приоритет над невидимым прогревом Каталога. Каталог
  прогревается после заполнения резерва Главной, а прямой переход в него немедленно запускает
  собственную загрузку.
- Каталог по-прежнему открывается в категории `Новинки`; существующие category/xSort query и
  независимые состояния Главной, Каталога и Поиска сохранены.
- Добавлены guards для порога ранней дозагрузки, минимального резерва Главной, остановки на
  неувеличивающейся следующей странице и отложенного прогрева Каталога.

### Validation

- Application source commit: `6f5fd7a`; version code 12, `0.4.2-dev`, minSdk 28,
  targetSdk 37.
- Каноническая команда завершена успешно: **68 suites / 307 unit tests**, 0 failures,
  0 errors и 0 skipped; lint — 0 errors, 7 warnings и 2 hints; `assembleDebug` успешен.
- Stable-signed APK `dist/KinogoTV-0.4.2-dev.apk` прошёл zipalign и v2 verification;
  certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`;
  artifact SHA-256:
  `1FFCD5C90F2BCC93268727ACB5D500E326A749FE6A336A8E60AE4698F595F741`.
- `adb install -r` на KIVI 4K Android TV 14 сохранил `firstInstallTime`
  (`2026-07-26 16:42:18`). Финальный cold launch занял 2616 ms.
- На Главной видны 12 реальных названий без состояния загрузки; последовательность
  `Down`, `Down` и пять `Right` достигла шестой карточки третьего ряда без произвольного
  перескока. Прямой вход в Каталог показал 20+ карточек в `Новинках`.
- В финальных проверках не обнаружены fatal exception, ANR либо ошибки Главной/Каталога.
  Единичная ошибка mirror-health перед smoke исчезла после явной повторной проверки адресов
  и не классифицирована как регрессия кода.

Кандидат C-004 расширяет hardware evidence каталогов, но не заменяет playback baseline
B-001: полный player regression для этого APK не выполнялся.

## [0.4.1-dev] — 2026-08-01

### Каталог и фильтры

- Удалены устаревшие локальная сортировка и одиночные GET-фильтры. Главная и Каталог теперь
  используют реальный stateful xSort-контракт сайта с полями `defaultsort`, `podborki`,
  `year` и `country` через form-urlencoded POST и origin-scoped cookie session.
- Реализованы все семь текущих server sort values; поле сортировки и направление разделены.
  Повторный выбор текущего пункта dropdown не меняет порядок, направление переключается
  только отдельной кнопкой `↑`/`↓`.
- Подборки, годы и страны разбираются из HTML текущего зеркала. Повреждённые quote-entries
  пропускаются поэлементно; неизвестные значения не превращаются в запросы.
- Каталог получил единый dropdown реальных категорий с группами `Фильмы` и `Сериалы` и
  открывается на `Новинки`. Пути категорий allowlisted и остаются origin-independent;
  xSort fragment без sidebar получает empty-only fallback из всех 28 проверенных путей.
- Главная больше не дублирует историю и не показывает заголовок `Новинки`: над общей сеткой
  размещена компактная строка реальных сортировки и фильтров сайта.
- Главная, Каталог и Поиск переведены на общий шестиколоночный grid: следующая page-route
  загружается из последней видимой строки, stable IDs сохраняют текущий фокус, а явная
  D-pad-навигация исключает wrap и произвольные скачки на неполной строке.
- Preload boundary учитывает identity выдачи, focus job отменяется при смене выдачи или
  удалении target, но не при обычном append, а выход за viewport прокручивает ровно одну
  строку. Поиск получил явный retry первой/следующей страницы.
- Dropdown фокусирует выбранный пункт и после выбора либо Back возвращает фокус на свою
  кнопку, не требуя аэромыши; focus request ограниченно повторяется на следующих кадрах.
- При reset категории или фильтра старые page-specific controls временно отключаются до
  ответа новой страницы, поэтому нельзя отправить option от предыдущей категории.

### Контракт и проверка

- Read-only live snapshot от 1 августа 2026 года зафиксировал актуальные категории,
  xSort selectors/wire values, HTML document/fragment POST и page routes для главной,
  категории и поиска.
- Добавлены offline fixtures и unit/contract guards для route generation, xSort session
  commands, parser controls, раннего preload и детерминированной D-pad-навигации.
- Cookie-session epoch инвалидирует applied xSort после login/reconnect; перед append
  repository проверяет, что сервер подтвердил явно выбранные фильтры и направление.
  Конкурирующая смена сессии автоматически повторяет transaction с bounded retry и не
  требует ручного нажатия `Повторить` при старте.
- На KIVI пройдены установка/запуск и точечные Home/Catalog/D-pad сценарии. Полный перебор
  xSort-комбинаций, длинная search-пагинация и playback regression остаются pending.

### Documentation

- Переработан корневой README в пользовательское описание только реализованных функций.
- Создан полный комплект agent/developer docs: state, architecture, service, playback,
  security, development, testing, release, decisions, regression memory, roadmap и handoff.
- Добавлен корневой `AGENTS.md` с обязательной актуализацией документации в каждой задаче.
- Исторические APK/decompiled research отделены от clean-room Markdown-выводов.

### Build and repository

- Подготовлен безопасный первый Git snapshot: расширены `.gitignore` и `.gitattributes`.
- Clean clone теперь может выполнять unit/lint/debug со стандартной Android debug signature.
- Stable signing автоматически применяется при наличии key; release без него запрещён.
- Добавлена настройка внешнего пути `KINOGO_SIGNING_STORE_FILE`.

### Validation

- Каноническая команда `testDebugUnitTest lintDebug assembleDebug` завершена успешно:
  **304 unit tests**, lint 0 errors / 7 warnings / 2 hints.
- Stable-signed debug APK code 11 / `0.4.1-dev` прошёл zipalign, v2 verification и сверку
  certificate SHA-256; artifact SHA-256:
  `ECF7BEADF8606987D19F663E352D72FCB7E1D1D30A8D3FD7A4B1476CE7A1B56B`.
- `adb install -r` сохранил `firstInstallTime` и данные приложения; cold launch оставил
  `MainActivity` foreground без fatal exception/ANR.

## [0.4.0-dev] — 2026-07-29

### Branding and shell

- TV banner детерминированно собран из официальной иконки, надписи `KINOGO` и подписи
  `for Android TV` на почти чёрном steel-фоне; декоративная нижняя полоса удалена.
- Legacy/adaptive launcher icon использует ту же исходную иконку с меткой `ATV`.
- Фирменная PNG-иконка добавлена как явно одобренное узкое branding-исключение с
  документированной provenance и SHA-256; код, UI и decompiled output официального APK не
  заимствуются.
- Приложение переведено на edge-to-edge steel/cyan тему: фиксированный компактный rail без
  рамки, светлый steel content frame и единый бирюзовый active/focus color.

### Catalog UI

- Общие каталожные, поисковые, закладочные и исторические сетки унифицированы до шести
  постеров в строке; quality badge показывает только значение без `Качество/Якість`.
- С главной удалён hero-баннер: история занимает одну строку, новинки показываются
  многострочной сеткой без отдельного горизонтального скролла.
- Каталог получил компактную строку `Все / Фильмы / Сериалы / Мультфильмы`, dropdown
  локальной сортировки и диалог `Фильтр`.
- `CatalogQuery` и `KinogoRoutes` получили одиночные детерминированные GET-фильтры новинок,
  года, страны и allowlisted жанра; неподтверждённые комбинации фильтров не имитируются.
- Search debounce увеличен до 750 ms; явный Enter/Search немедленно отправляет запрос,
  скрывает клавиатуру и переводит focus в результаты. Голосовое действие стало
  графической кнопкой микрофона.

### Details, settings and source selection

- Details получил крупный постер, компактные status actions сразу под основными кнопками и
  полный текст описания без пустого блока выбора просмотра.
- Source selection уплотнён; строка серии явно доступна до запуска, а зависимые
  source/voiceover/season/episode/quality нормализуются по реальной sparse-матрице.
- Настройки стали компактнее и изменяются только по OK; Left/Right освобождены для D-pad
  навигации, включая возврат в rail.

### Player

- Timeline focus теперь обозначается белой точкой текущей позиции без прямоугольной рамки.
- Для Media3 buffering добавлен центральный индикатор с `reduceMotion`-вариантом.
- Previous/Next и auto-next используют реальные координаты выбранных source/voiceover:
  последняя серия сезона переходит в первую доступную серию следующего совместимого сезона.
- После естественного окончания фильма, последней доступной серии либо любого эпизода при
  отключённом auto-next fullscreen player возвращает пользователя в карточку материала.
- Добавлены unit guards для visual HUD state, cross-season navigation и completion policy.

### Validation

- 67 test suites, 281 unit tests, 0 failures/errors/skipped.
- Android Lint: 0 errors, 6 warnings; `assembleDebug`, ZIP alignment и v2 signature прошли.
- Stable-signed APK `0.4.0-dev` установлен через `adb install -r` на KIVI 4K Android TV 14:
  старый `firstInstallTime`, история и checkpoint сохранились; cold launch и foreground
  подтверждены, crash/ANR не обнаружены.
- На устройстве проверены новый steel/cyan shell, карточка и source selection с зависимыми
  озвучкой, сезоном и сериями. Полный runtime-сценарий перехода через границу сезона и
  естественные сигналы окончания Media3 остаются отдельной аппаратной проверкой.
- SHA-256 APK:
  `188A2CF14226C1541B2E0D5822F9CD445E09EF1E2FCE1B41483C5CC2E093EFFE`.

## [0.3.3-dev] — 2026-07-28

### UI

- Navigation rail стал постоянно развернутым и подсвечивает текущий раздел.
- Каталожная сетка стала adaptive; исправлена ранняя подгрузка.
- Из quality badge удалены префиксы «Качество/Якість».
- В details убраны лишние заголовки и пустой выбор просмотра, описание показывается полностью.

### History

- `WatchProgress` получил snapshot карточки, чтобы search/history записи не превращались в
  числовой ID.
- Snapshot enrichment стал атомарным и не перезаписывает свежий checkpoint.
- Добавлен strict legacy resolver для numeric-only history с allowlist путей и обработкой
  terminal same-origin redirect.

### Player

- HUD уплотнён: graphical previous/play/next и selectors находятся в одной строке.
- Episode row перенесён вниз, отдельная кнопка «Серии» убрана.
- Первый OK показывает HUD без немедленной паузы.
- Hidden-HUD seek открывает HUD с timeline focus.
- Добавлен retry focus request на следующих Compose frames.
- Сезоны и серии зависят от выбранной озвучки.

### Validation

- 257 unit tests, 0 failures/errors/skipped.
- Lint 0 errors.
- Stable-signed debug APK прошёл alignment/signature/metadata check.
- Cold launch, каталог, account/library/history и реальный native playback проверены на KIVI
  Android TV 14.

SHA-256 APK:
`931253976140D5A76276AB4F30E7A709600CD61EABFE1FD8A36C29F38B454A77`.

## [0.3.0-dev – 0.3.2-dev] — 2026-07-26

- Создан единый native playback plan и предварительный TV-экран source/voice/season/episode/
  quality.
- Реализованы direct HLS/DASH/MP4, Cinemar и Collaps adapters.
- Добавлен fullscreen Media3 player с D-pad reducer, MediaSession, subtitles и resume.
- Добавлен explicit provider Web fallback с TV HUD и virtual cursor.
- Исправлены unavailable source/404 flows и fresh playback preparation.
- Проведено clean-room UX-исследование LazyMedia Deluxe; код и assets не заимствованы.

## [0.2.1-dev – 0.2.3-dev] — 2026-07-22

- Исправлен запуск приложения на реальном Android TV.
- Добавлены полноценные Android TV icon/banner и LEANBACK launcher metadata.
- Реализован нативный startup frame, crash/stall report и кнопки восстановления.
- Исправлены mirror selection/details actions и redirect handling.
- Реализована HTML-авторизация, постоянное encrypted хранение credentials и re-login.
- Реализованы server status bookmarks, independent favorite и sync/outbox.
- Настройки quality/seek/subtitles/auto-next стали сохраняемыми и D-pad-editable.
- Исследован официальный APK Kinogo и optional playback gateway.

## [0.1.0-dev – 0.2.0-dev] — 2026-07-16

- Создан Kotlin/Compose Android TV проект с minSdk 28.
- Реализованы TV shell, left navigation, home, catalog, search, details, bookmarks, history и
  settings.
- Добавлены live server-rendered catalog parser, posters и automatic page preload.
- Созданы replaceable mirror models, health/fingerprint checks, quarantine и manual input.
- Добавлены public-DNS/SSRF boundaries и safe HTML transport.
- Созданы local history/resume models и первые Media3/player contracts.

Полный список исторических artifact hashes хранится в `dist/SHA256SUMS.txt`.
