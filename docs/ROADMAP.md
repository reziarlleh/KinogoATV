# Roadmap

Последнее обновление: **23 августа 2026 года**.

Roadmap задаёт направление, а не обещание даты. Приоритет меняется после пользовательского
тестирования. Реализованный пункт переносится в `CHANGELOG.md` и удаляется из активного
списка либо отмечается завершённым.

## Сейчас: финализация 0.5.2 и ручная приёмка владельцем

C-008 добавляет buffer-aware one-shot recovery, новую player generation при
fresh source, устойчивые checkpoint/resume writes, сохраняемый quality cap, реальную
настройку Media3 buffer 5/10/15/20/30 с и in-memory preload ближайшей серии через
границу сезона. Ручная refresh-кнопка и obsolete settings cycle удалены;
updater-пункты собраны в конце Settings. Exact commit, final local Gradle evidence и
stable-signed APK уже зафиксированы; signed manifest, CI/PR/Release/Pages/jsDelivr/live
publication и runtime-приёмка ещё **PENDING**. C-007 остаётся
исторической integration point, B-001 — полным playback rollback baseline.

### P0 — final evidence и приёмка

- После появления exact Release asset создать и проверить final signed code 16
  `update/manifest.json`. Старый code 15 manifest до первого merge C-008 намеренно удалён,
  чтобы Pages workflow не развернул устаревший payload. При любом последующем production
  change повторить полный canonical pass и artifact verification.
- Получить GitHub Actions result на exact commit, опубликовать exact Release asset и
  доказать live Pages/jsDelivr metadata и каждый заявленный APK transport. До этого все
  endpoints считаются pending.
- Владелец вручную проверяет выход из player в Details с активной «Смотреть»,
  exact серию/позицию после restart, переход через границу сезона и immediate-next
  preload. Для quality проверяется exact/ниже cap/lowest-above и сохранение между
  сериями.
- Владелец проверяет все пять buffer values и один контролируемый stall/error:
  должна сработать ровно одна fresh recovery без retry loop. Точная позиция сохраняется,
  только если fresh normalization оставила тот же фильм/сезон/эпизод. Если попытка не
  помогла, явный retry идёт только через Back → Details → «Смотреть».
- Владелец проверяет signed-manifest update при недоступном GitHub API: metadata,
  fallback download, APK checks и передачу Package Installer с обязательным OS confirmation.
- Агент не подключается к TV по ADB, не устанавливает APK и не запускает hardware smoke
  без нового явного разрешения владельца на конкретный узкий сценарий.
- Не назначать C-008 playback baseline, пока не закрыта эта runtime-матрица.

### P1 — оставшиеся integration-регрессии

- Доказать actual Web fallback resume в тот же playlist item/position; исторически
  подтверждены только fullscreen launch и Back → Details → History. Не считать это
  cross-device/native sync.
- Проверить About card/rail logo, cold focus без focus steal, category/filter/search flows,
  длинную Home/Catalog/Search pagination и live combinations подборки/года/страны.
- Проверить registration form/CAPTCHA refresh/rejection и live submit; rules
  default-decline/explicit-accept уже подтвержден instrumentation.
- Проверить remote mirror bootstrap failure/expiry и запрет активации candidate без
  fingerprint; один manifest не делает live origin trusted.
- Проверить timeline marker/buffering overlay, Previous/Next/auto-next и возврат в Details
  после natural end последней unit.

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

Пункты C-008 в этом списке не становятся verified runtime автоматически; актуальный уровень
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
- Local episode/position history and legacy ID recovery; checkpoint queue, generation guard,
  monotonic timestamps, unit activation at `0` и newest-completed suppression защищают
  exact resume от late writes и возврата к старой серии.
- Native Media3 player and selection matrix; desired quality сохраняется между
  сериями и выбирает exact, затем highest `<=` cap, иначе lowest above cap.
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
  dropdown settings. Fresh detail cache оставляет «Смотреть» доступной после player Back.
- Persistent TV settings and exit confirmation.
- Буфер 5/10/15/20/30 с с default/fallback 15 с подключён к `DefaultLoadControl`:
  `min=max=target`, start `clamp(target/3, 1–2,5 с)`, rebuffer
  `clamp(target/2, 2–5 с)`, time-priority.
- Все совместимые сезоны развёрнуты в один Media3 playlist; in-memory
  `PreloadConfiguration` загружает immediate-next item на 2–5 с, включая границу
  сезона, без disk cache и отдельного resolver warmup.
- Startup crash/stall diagnostics.
- API 28 minimum and stable update signing.
- Two-step same-origin DLE registration: explicit rules acceptance, remember-only sensitive
  input, bounded user-solved image CAPTCHA и явный отказ от обхода interactive challenges.
- Unsigned bounded remote mirror bootstrap, который добавляет только quarantined discovery
  candidates; текущий snapshot содержит четыре origin, включая `kinogo.family`.
- Проверяемый GitHub Release updater с exact signer validation и обязательным Android OS
  confirmation.
- Initial rail focus, Switch/dropdown Settings и newest applicable resume policy;
  updater controls собраны в конце, obsolete arrow-cycle settings path удалён.
- Buffer-aware one-shot fresh playback source recovery с cross-screen loop guard, forced
  new player generation и порогами initial `max(20,target)`, rebuffer `clamp(target,5–10)`,
  `READY` without progress 15 с. Ручная native refresh-кнопка удалена; повтор идёт
  через Details → «Смотреть».
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
