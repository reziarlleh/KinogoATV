# Roadmap

Последнее обновление: **21 августа 2026 года**.

Roadmap задаёт направление, а не обещание даты. Приоритет меняется после пользовательского
тестирования. Реализованный пункт переносится в `CHANGELOG.md` и удаляется из активного
списка либо отмечается завершённым.

## Сейчас: финализация и аппаратная приёмка 0.5.1

C-007 адаптирует Cinemar к deferred `/api/playlist/load`, возвращает Back в
исходный раздел с Search state/focus, добавляет историю из 10 поисков,
делает About первой карточкой Settings/действием rail logo, сохраняет
first-party PlayerJS state при выходе и вводит signed multi-endpoint updater с GitHub API
fallback. Final local canonical pass (82 suites / 393 tests, 4 мин 27 с), exact
stable-signed APK и signed manifest подтверждены. KIVI прошёл current exact-host Cinemar
native playback, History/Search non-first Back/focus и WebView launch/Back smoke. Final
commit, CI, GitHub Release/Pages/jsDelivr deployment, actual Web resume и расширенные
player/update runtime-сценарии ещё **PENDING**. C-006 и B-001 сохраняются как предыдущий
integration evidence и полный playback rollback baseline.

### P0 — TV regression pass

- Связать final C-007 commit с локально подтверждёнными canonical counts/lint, exact
  `KinogoATV-0.5.1-code15.apk` и `update/manifest.json`; при любом production change
  повторить сборку и проверки.
- Получить GitHub Actions result на exact commit, опубликовать exact Release asset и
  доказать live Pages/jsDelivr metadata и каждый заявленный APK transport. До этого все
  endpoints считаются pending.
- Проверить достижимость крупной первой About card и focusable rail logo.
- Для Web fallback доказать actual повторный вход в тот же playlist item/position.
  Fullscreen launch и Back → Details → History уже прошли, но provider state недоступен
  accessibility/safe logs. Не считать это cross-device/native sync.
- Проверить signed-manifest update при недоступном GitHub API: metadata, fallback
  download, все APK checks и передачу системному Package Installer без silent install.
- Расширить cold focus smoke: Right/Left и отсутствие focus steal каждого раздела, затем
  проверить category/filter/search focus flows.
- Проверить на живом зеркале combinations подборки/года/страны, смену категорий и длинную
  непрерывную дозагрузку Home/Catalog/Search без перескоков фокуса.
- Проверить крупный poster/details, достижимость всех status actions и episode row на source
  selection; при возврате из player primary action должен снова получить focus.
- Проверить newest-unfinished resume на реальном многосерийном материале из
  History/Catalog/Search после restart; basic `Продолжить с 0:14` уже подтверждён.
- Проверить registration form/CAPTCHA refresh/rejection и live submit; rules
  default-decline/explicit-accept уже подтверждён instrumentation.
- Проверить remote bootstrap failure/expiry и то, что candidate не активируется без
  fingerprint; live `w.kinogo.solar` не считать trusted по одному manifest.
- Проверить updater check/download/verify и передачу системному Package Installer; не
  подтверждать фактическую замену APK без отдельного решения, но зафиксировать обязательный
  OS confirmation screen.
- При необходимости повторить About/QR/external actions на final Release APK; debug-smoke
  через Yandex TV browser уже пройден.
- Проверить timeline marker и buffering overlay в реальном воспроизведении.
- Проверить Previous/Next и auto-next на границе сезонов, а также возврат в details после
  естественного окончания последнего материала.
- Вызвать один контролируемый playback source failure: должен быть ровно один fresh
  reprepare без retry loop, затем понятная ручная ошибка/Back → Details. Pure guards уже
  покрывают persistence budget и missing content/mirror early returns.

### P1 — дальнейшая визуальная доводка

- Убирать оставшиеся локальные размеры/цвета в общий theme/components layer только после
  characterization tests.
- Улучшить loading/error/empty states без изменения установленного D-pad контракта.
- Проверить горизонтальное размещение transport/selectors на разных TV aspect ratios.
- Не менять принятый remote contract: первый OK показывает HUD, hidden seek фокусирует
  timeline, а окончание последнего материала возвращает в details.

### P1 — доступность

- Применить `reduceMotion` ко всем rail/card/drawer/player animations, а не только Settings.
- Проверить high-contrast focus на каждом экране.
- Добавить/проверить content descriptions и live-region announcements для player status.
- Проверить системные caption preferences и subtitle readability.

## Следующие функциональные улучшения

### Каталог

- Решить, нужен ли production Paging 3 flow вместо ручной пагинации.
- После TV smoke решить, нужно ли запоминать выбранные category/xSort-параметры между
  запусками приложения.
- Продолжить live-проверку combinations подборки/года/страны и пустых результатов без
  ослабления server postcondition.

### Зеркала

- Если remote bootstrap станет release-critical, добавить отдельную криптографическую
  подпись и rollback/revocation; текущий GitHub/TLS manifest намеренно unsigned и даёт
  только quarantined candidates.
- Добавить операторский процесс обновления `config/mirrors.json` с review, expiry и live
  evidence; наличие origin в файле не делает его trusted.
- Не превращать discovery в поиск/активацию случайных lookalike domains.

### История и синхронизация

- Добавить UI удаления одной записи/эпизода истории с подтверждением.
- Добавить управление локальной history retention.
- Исследовать отдельный opt-in sync service/companion для exact progress; не имитировать
  серверную функцию сайта, которой нет.

### Воспроизведение

- Добавлять provider adapters только по наблюдаемым browser-visible contracts и реальным
  failing examples.
- Реализовать визуальный countdown следующей серии с Cancel/Play now.
- После TV evidence решить, нужен ли второй provider failover поверх реализованной одной
  fresh-source попытки; не увеличивать лимит без loop guards.
- Решить, какие validated Web player capabilities можно безопасно подключить к единому HUD.
- Добавить subtitle style и playback speed только после стабилизации основного HUD.

### Архитектура и качество

- Разделить `KinogoAppRoot` на state holders/use cases без одномоментной миграции всех flow.
- Удалить дублирование ручной пагинации/PagingSource после выбора одного production пути.
- Получить первый зелёный remote run добавленного GitHub Actions workflow и затем считать
  clean-clone CI operational.
- Добавить dependency verification metadata и SHA-256 Gradle distribution.
- Добавить API 28 emulator/device smoke; текущая аппаратная проверка выполнялась на Android TV
  14.
- Расширить Compose D-pad tests критических focus graphs.

### Выпуск

- Выпустить первый stable GitHub Release с exact asset name
  `KinogoATV-<version>-code<code>.apk`, GitHub SHA-256 digest и release notes.
- После exact Release asset подписать bounded update payload тем же APK signing identity,
  развернуть Pages artifact и проверить его из целевой сети. При необходимости
  добавить в следующий APK ещё один не-GitHub HTTPS endpoint через
  `KINOGO_UPDATE_MANIFEST_URLS`; trust остаётся криптографическим, а не host-based.
- Настроить protected release environment и безопасную передачу signing secrets в CI, если
  автоматический release действительно понадобится.
- Перед сменой visibility повторно проверить disclaimer/repository hygiene. Лицензию
  выбрать только по явному решению владельца; пока `LICENSE` отсутствует, права не
  предоставлены автоматически.

## Реализовано в source

Пункты C-007 в этом списке не становятся verified runtime автоматически; актуальный уровень
evidence указан в `PROJECT_STATE.md`.

- Native Android TV shell и launcher tile.
- Детерминированный TV branding с одобренной официальной иконкой, надписями
  `KINOGO / for Android TV` и `ATV` launcher badge.
- Edge-to-edge steel/cyan каркас, фиксированный rail и общая шестиколоночная poster grid.
- Главная без hero и без дублирующей истории: серверная лента и xSort-управление.
- Объединённый category dropdown фильмов/сериалов; каталог по умолчанию открывает `Новинки`.
- Серверные xSort dropdown сортировки/подборки/года/страны и отдельная кнопка направления;
  все семь видов сортировки и rating ASC/DESC проверены на Главной и в Каталоге.
- Сессионное xSort-состояние сериализовано и восстанавливается при переключении лент.
- Независимые Home/Catalog/Search feed states, общая стабильная D-pad grid и динамическая
  подгрузка следующих страниц во всех трёх лентах.
- Общая сетка начинает preload при остатке менее двух загруженных строк; Главная при старте
  набирает минимум 18 уникальных карточек. Невидимый Catalog warmup удалён, прямой переход
  в Каталог запускает его feed.
- DLE/xSort session работает по HTTP/1.1; неоднозначный сетевой сбой допускает один
  безопасный полный retry от `clearallfields`, без повторения отдельной toggle-команды.
- Search debounce 750 ms, immediate submit с закрытием клавиатуры и graphical voice action.
- Search возвращает query/results/focused stable ID после Details; до 10 последних
  подтверждённых OK/voice/chip запросов хранятся локально и показываются одной
  TV-строкой; промежуточные debounce-строки в history не попадают.
- Live catalog, top-level sections, text/voice search и early preload.
- Replaceable mirror registry, manual HTTPS origin и safe redirect discovery.
- Account credentials + automatic re-login.
- Server statuses/favorite with local outbox.
- Local episode/position history and legacy ID recovery.
- Native Media3 player and selection matrix.
- Cinemar/Collaps native adapters, включая lazy selected-leaf Cinemar grant через
  `/api/playlist/load`, direct media и explicit provider Web fallback.
- Current authenticated exact-host Cinemar runtime player document отделён от strict
  `/embed/...` discovery; KIVI подтвердил selector и Media3 S2E5 >15 секунд.
- True Back и exact non-first focus подтверждены на KIVI для второй History card и второго
  Search result; query/results и recent-query row сохранились.
- Web fallback сохраняет first-party same-profile PlayerJS state: выход выполняет
  `pause`, фиксирует cookies только internal WebView profile и затем выполняет dispose,
  но не обещает native/cross-device sync.
- Compact HUD, bottom episode row и timeline focus after hidden seek.
- White timeline focus marker, центральный buffering state, cross-season Previous/Next и
  возврат в details после естественного окончания Media3.
- Compact details/source/settings: крупный постер, видимая серия до старта, Switch и D-pad
  dropdown settings.
- Persistent TV settings and exit confirmation.
- Startup crash/stall diagnostics.
- API 28 minimum and stable update signing.
- Two-step same-origin DLE registration: explicit rules acceptance, remember-only sensitive
  input, bounded user-solved image CAPTCHA и явный отказ от обхода interactive challenges.
- Unsigned bounded remote mirror bootstrap, который добавляет только quarantined discovery
  candidates; текущий snapshot содержит четыре origin, включая `kinogo.family`.
- Проверяемый GitHub Release updater с exact signer validation и обязательным Android OS
  confirmation.
- Initial rail focus, Switch/dropdown Settings и newest-unfinished resume policy.
- One-shot fresh playback source recovery с cross-screen loop guard.
- About dialog как первая крупная Settings card и focusable rail-logo action, unchanged
  owner-supplied donation QR, exact external URL allowlist и
  public-repository disclaimer.
- Signed multi-endpoint update manifest с installed-signer verification, bounded expiry/download
  mirrors и GitHub Release API fallback; Pages/jsDelivr deployment ещё pending.
- GitHub Actions clean-clone workflow (первый remote green run pending).
- Local C-006 integration pass: 75 suites / 348 tests, lint 0 errors / 19 warnings / 2 hints,
  debug/androidTest/release assembly; verified stable-signed artifact и X96 final release
  install/cold smoke.
- Recovery-loop guards: consumed budget до launch, discard dead player на early exit,
  explicit missing content/mirror error и exact same-unit position.

## Осознанно не делаем

Эти ограничения нельзя заново предлагать как быстрые решения:

- WebView-оболочка всего сайта.
- Portrait/smartphone UI.
- Копирование UI/code/assets LazyMedia либо UI/code/decompiled output официального APK.
  Единственное узкое исключение — явно одобренная официальная PNG-иконка Kinogo с
  документированной provenance; оно не распространяется на другие assets.
- Обход DRM, CAPTCHA, geo restrictions, 401/403 или provider protections.
- Отключение HTTPS/public-DNS/SSRF boundary ради доступности.
- Сохранение transient signed media URLs.
- Автоматическое объявление случайного похожего domain «официальным зеркалом».
- Молчаливый переход из native player в WebView.
