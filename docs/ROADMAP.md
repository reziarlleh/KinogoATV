# Roadmap

Последнее обновление: **29 июля 2026 года**.

Roadmap задаёт направление, а не обещание даты. Приоритет меняется после пользовательского
тестирования. Реализованный пункт переносится в `CHANGELOG.md` и удаляется из активного
списка либо отмечается завершённым.

## Сейчас: интерфейсная полировка

Функциональный baseline `0.3.3-dev` работает. До появления нового functional bug основная
работа идёт над UI.

### P0 — единая визуальная система

- Зафиксировать TV design tokens: safe area, сетка, размеры, радиусы, цвета, typography,
  focus border/glow и disabled states.
- Убрать локальные несогласованные размеры/цвета из экранов в общий theme/components layer.
- Проверить overscan и читаемость на 1080p и 4K TV с реального расстояния.
- Сохранить постоянный navigation rail и однозначную подсветку текущего раздела.

### P0 — экраны приложения

- Главная: выровнять featured area, ряды и иерархию заголовков.
- Каталог: улучшить filters/sort controls, card density, metadata badges и loading/error
  состояния.
- Поиск: привести keyboard/voice action/results к единому focus flow.
- Карточка: разработать полноценную композицию с постером/backdrop, полным описанием и
  действиями без пустых секций.
- Закладки/История: улучшить фильтры, progress indication и empty states.
- Настройки: уменьшить визуальный шум, сохранить доступность details action и D-pad cycling.

### P0 — плеер

- Довести размеры HUD ближе к компактному media-centre интерфейсу.
- Проверить горизонтальное размещение transport и selectors на разных ширинах.
- Улучшить timeline focus/feedback, buffer/error states и episode row.
- Не менять принятый remote contract: первый OK показывает HUD, hidden seek фокусирует
  timeline.

### P1 — доступность

- Применить `reduceMotion` ко всем rail/card/drawer/player animations, а не только Settings.
- Проверить high-contrast focus на каждом экране.
- Добавить/проверить content descriptions и live-region announcements для player status.
- Проверить системные caption preferences и subtitle readability.

## Следующие функциональные улучшения

### Каталог

- Формализовать безопасные server-side filters: жанр, год, страна.
- Исследовать deterministic server sorting вместо зависимости только от клиентской
  сортировки загруженной страницы.
- Добавить пагинацию server search.
- Решить, нужен ли production Paging 3 flow вместо ручной пагинации.

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
- Live catalog, top-level sections, text/voice search и early preload.
- Replaceable mirror registry, manual HTTPS origin и safe redirect discovery.
- Account credentials + automatic re-login.
- Server statuses/favorite with local outbox.
- Local episode/position history and legacy ID recovery.
- Native Media3 player and selection matrix.
- Cinemar/Collaps native adapters, direct media и explicit provider Web fallback.
- Compact HUD, bottom episode row и timeline focus after hidden seek.
- Persistent TV settings and exit confirmation.
- Startup crash/stall diagnostics.
- API 28 minimum and stable update signing.

## Осознанно не делаем

Эти ограничения нельзя заново предлагать как быстрые решения:

- WebView-оболочка всего сайта.
- Portrait/smartphone UI.
- Копирование UI/code/assets LazyMedia или официального APK.
- Обход DRM, CAPTCHA, geo restrictions, 401/403 или provider protections.
- Отключение HTTPS/public-DNS/SSRF boundary ради доступности.
- Сохранение transient signed media URLs.
- Автоматическое объявление случайного похожего domain «официальным зеркалом».
- Молчаливый переход из native player в WebView.
