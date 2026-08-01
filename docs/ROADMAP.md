# Roadmap

Последнее обновление: **1 августа 2026 года**.

Roadmap задаёт направление, а не обещание даты. Приоритет меняется после пользовательского
тестирования. Реализованный пункт переносится в `CHANGELOG.md` и удаляется из активного
списка либо отмечается завершённым.

## Сейчас: аппаратная приёмка и точечная доводка 0.4.2-dev

Большая переработка интерфейса реализована: steel/cyan visual system, фиксированный rail,
шестиколоночные сетки, новая главная, серверный xSort и paged search flow, компактные
settings/details/source selection и уточнённый HUD. В 0.4.2-dev добавлены ранняя дозагрузка
общих сеток, начальный резерв Главной и приоритетная последовательность прогрева
Home/Catalog. До дальнейшей графической полировки нужен полный TV regression pass;
автоматическая сборка сама по себе не считается подтверждением focus/playback UX. Установка,
запуск и точечный Home/Catalog/D-pad smoke кандидата пройдены, но сборка остаётся validation
candidate, а не новым playback baseline.

### P0 — TV regression pass

- Проверить overscan, читаемость, плотность шести колонок и полный focus graph на 1080p/4K
  с реального расстояния.
- Проверить переход Left из каждого первого столбца/управляющей строки в rail и Right
  обратно в content.
- Проверить все category/xSort dropdown, возврат фокуса, search submit/keyboard hide и
  Settings OK-only обычным пультом.
- Проверить на живом зеркале все xSort dropdown, отдельную кнопку направления, смену
  категорий и длинную непрерывную дозагрузку Home/Catalog/Search без перескоков фокуса.
- Проверить крупный poster/details, достижимость всех status actions и episode row на source
  selection.
- Проверить timeline marker и buffering overlay в реальном воспроизведении.
- Проверить Previous/Next и auto-next на границе сезонов, а также возврат в details после
  естественного окончания последнего материала.

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

### Зеркала

- Подключить подписанный remote manifest с provenance, expiry и rollback.
- Добавить контролируемое обновление bootstrap list без нового APK.
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
- Улучшить bounded retry/failover и отображение причины unavailable source.
- Решить, какие validated Web player capabilities можно безопасно подключить к единому HUD.
- Добавить subtitle style и playback speed только после стабилизации основного HUD.

### Архитектура и качество

- Разделить `KinogoAppRoot` на state holders/use cases без одномоментной миграции всех flow.
- Удалить дублирование ручной пагинации/PagingSource после выбора одного production пути.
- Добавить CI для unit/lint/assembleDebug на clean clone.
- Добавить dependency verification metadata и SHA-256 Gradle distribution.
- Добавить API 28 emulator/device smoke; текущая аппаратная проверка выполнялась на Android TV
  14.
- Расширить Compose D-pad tests критических focus graphs.

### Выпуск

- Перейти от локального `dist` к GitHub Release assets.
- Настроить protected release environment и безопасную передачу signing secrets в CI, если
  автоматический release действительно понадобится.
- Перед возможной публичностью выбрать лицензию, проверить trademark/disclaimer и исключить
  все private research/data.

## Выполненный baseline

- Native Android TV shell и launcher tile.
- Детерминированный TV branding с одобренной официальной иконкой, надписями
  `KINOGO / for Android TV` и `ATV` launcher badge.
- Edge-to-edge steel/cyan каркас, фиксированный rail и общая шестиколоночная poster grid.
- Главная без hero и без дублирующей истории: серверная лента и xSort-управление.
- Объединённый category dropdown фильмов/сериалов; каталог по умолчанию открывает `Новинки`.
- Серверные xSort dropdown сортировки/подборки/года/страны и отдельная кнопка направления.
- Сессионное xSort-состояние сериализовано и восстанавливается при переключении лент.
- Независимые Home/Catalog/Search feed states, общая стабильная D-pad grid и динамическая
  подгрузка следующих страниц во всех трёх лентах.
- Общая сетка начинает preload при остатке менее двух загруженных строк; Главная при старте
  набирает минимум 18 уникальных карточек, затем прогревает Каталог. Прямой переход в
  Каталог запускает его feed независимо от фонового прогрева.
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
- Compact details/source/settings: крупный постер, видимая серия до старта и OK-only settings.
- Persistent TV settings and exit confirmation.
- Startup crash/stall diagnostics.
- API 28 minimum and stable update signing.

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
