# Roadmap

Последнее обновление: **15 августа 2026 года**.

Roadmap задаёт направление, а не обещание даты. Приоритет меняется после пользовательского
тестирования. Реализованный пункт переносится в `CHANGELOG.md` и удаляется из активного
списка либо отмечается завершённым.

## Сейчас: финализация и аппаратная приёмка 0.5.0

C-006 объединяет регистрацию, remote mirror bootstrap, проверяемый updater, About/public
disclaimer, initial rail focus, новые Settings controls, единый resume и bounded playback
source refresh. Local 75-suite/348-test pass и debug+androidTest+release assembly зелёные.
Final stable-signed APK проверен и установлен через `adb install -r` на X96Max Plus Ultra с
сохранением данных; cold launch/catalog smoke пройден. KIVI debug ранее подтвердил Settings,
About и короткий playback/resume-return. Application source зафиксирован как `6567088`;
CI/public release и расширенные live/player сценарии ещё не закреплены. B-001 остаётся
playback rollback baseline.

### P0 — TV regression pass

- Получить первый GitHub Actions result и опубликовать проверенный artifact из application
  commit `6567088` как exact GitHub Release asset.
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
- Настроить protected release environment и безопасную передачу signing secrets в CI, если
  автоматический release действительно понадобится.
- Перед сменой visibility повторно проверить disclaimer/repository hygiene. Лицензию
  выбрать только по явному решению владельца; пока `LICENSE` отсутствует, права не
  предоставлены автоматически.

## Реализовано в source

Пункты C-006 в этом списке не становятся verified runtime автоматически; актуальный уровень
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
- Live catalog, top-level sections, text/voice search и early preload.
- Replaceable mirror registry, manual HTTPS origin и safe redirect discovery.
- Account credentials + automatic re-login.
- Server statuses/favorite with local outbox.
- Local episode/position history and legacy ID recovery.
- Native Media3 player and selection matrix.
- Cinemar/Collaps native adapters, direct media и explicit provider Web fallback.
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
- About dialog, unchanged owner-supplied donation QR, exact external URL allowlist и
  public-repository disclaimer.
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
