# Архитектура KinogoATV

Последнее обновление: **1 августа 2026 года**.

## Цели архитектуры

KinogoATV — TV-only приложение, которое:

- показывает нативный каталог вместо оболочки сайта;
- работает обычным D-pad-пультом;
- не привязывает данные пользователя к одному изменчивому домену;
- использует единый нативный Media3-плеер для явно разобранных источников;
- сохраняет web-плеер только как изолированный явный fallback;
- не ослабляет сетевую защиту ради доступности контента.

## Путь запуска

```mermaid
flowchart TD
    A["KinogoApplication"] --> B["StartupDiagnostics"]
    B --> C["MainActivity: нативный первый кадр"]
    C --> D["ComposeHost"]
    D --> E["KinogoAppRoot: composition root"]
    E --> F["KinogoTvApp: TV navigation"]
    E --> G["Playback preparation"]
    G --> H["TvPlayerScreen: Media3"]
    G --> I["ProviderEmbedPlayerScreen: explicit Web fallback"]
```

Ключевые файлы:

- `KinogoApplication.kt` — ранняя установка crash diagnostics и debug-настройка WebView;
- `MainActivity.kt` — нативный первый кадр, immersive mode, stall/error report и подключение
  Compose только после первого draw;
- `ComposeHost.kt` — жизненный цикл `ComposeView`;
- `KinogoAppRoot.kt` — ручная сборка зависимостей, orchestration и корневое состояние;
- `ui/KinogoTvApp.kt` — destinations, navigation rail, карточка и подтверждение выхода.

Нативный стартовый слой принципиален: ошибка Compose, storage или сети не должна выглядеть
как «пустое нажатие» на плитку Android TV.

## Текущая композиция

Проект состоит из одного Android-модуля `:app`. Hilt/Koin, Navigation Component и ViewModel
слой сейчас не используются. Объекты DataStore, repositories, parsers и coordinators
создаются через `remember` в `KinogoAppRoot`.

Это означает:

- lifecycle UI-состояния в основном принадлежит composition root;
- новый flow нельзя автоматически «положить во ViewModel», которой пока нет;
- перенос состояния из `KinogoAppRoot` должен быть отдельным контролируемым рефакторингом,
  а не побочным эффектом интерфейсной правки.

## Слои

| Слой | Каталоги | Ответственность |
| --- | --- | --- |
| Platform/bootstrap | корень package, `diagnostics/` | Activity, ранний draw, crash/stall recovery |
| UI | `ui/`, `player/ui/`, `player/web/` | Compose screens, TV focus, HUD, provider WebView |
| Orchestration | `KinogoAppRoot.kt` | Связывает repositories, storage, session и fullscreen flows |
| Domain | `domain/` | Host-independent модели и инварианты |
| Data | `data/*` | HTML/JSON parsers, repositories, DataStore, DNS, mirrors, adapters |
| Player core | `player/` | Reducer, key mapping, Media3 controller и safe data sources |

Зависимости должны идти к domain-моделям, а UI не должен разбирать HTML, provider JavaScript
или сетевые URL.

Визуальный контракт UI вынесен в [`UI_DESIGN.md`](UI_DESIGN.md). Текущая реализация
использует фиксированный тёмно-стальной navigation rail, более светлый стальной content
frame, бирюзовый focus/active color и общую шестиколоночную сетку постеров. Эти параметры
являются частью TV focus graph, а не только декоративной темой: произвольная замена размеров
или плавающая панель может сделать D-pad-переходы неоднозначными.

## Основные потоки данных

### Каталог

```mermaid
flowchart LR
    A["MirrorRegistry"] --> B["active HTTPS origin"]
    B --> C["Home / Catalog / Search feed state"]
    C --> D["CatalogQuery + KinogoRoutes"]
    D --> E["HtmlCatalogRepository: serialized xSort"]
    E --> F["KinogoSessionHttpClient + safe HTML policy"]
    F --> G["KinogoHtmlParser"]
    G --> H["CatalogItem / CatalogControls"]
    H --> I["Shared TvPosterGrid"]
```

`CatalogItem.relativePath` не содержит домен. При смене зеркала тот же объект можно запросить
у нового origin. Асинхронные запросы защищены generation counters: старый ответ не может
заменить данные нового зеркала, раздела или поискового запроса.

`CatalogQuery` содержит optional allowlisted `CatalogCategory`, комбинируемый
`CatalogBrowseFilters`, optional `searchTerm` и `page`. Поиск остаётся отдельным
режимом и не смешивается с category/xSort-фильтрами. Категории фильмов и
сериалов хранят точные host-independent routes, а случайные href из HTML не
попадают в domain allowlist. Каталог по умолчанию открывает категорию `Новинки`.
Если xSort fragment не содержит sidebar, UI подставляет ровно полный allowlist из 28
`CatalogCategory.entries`; непустой разобранный subset используется без расширения.

Списки сортировки, подборки, года и страны разбираются из xSort-элементов
конкретной страницы. Выбор применяется сервером через POST xSort; направление
сортировки передаётся повторным выбором того же поля и имеет отдельную
кнопку в TV UI. xSort-состояние является session-wide, поэтому `HtmlCatalogRepository`
сериализует все browse/search-запросы через `Mutex`, очищает и восстанавливает
нужную выборку при переключении между лентами. Числовой cookie-session epoch инвалидирует
applied-query cache после login/reconnect, а финальное активное xSort-состояние сверяется с
явно запрошенными полями до append. Конкурентная смена epoch запускает bounded retry всего
catalog transaction.

`KinogoAppRoot` владеет тремя независимыми `CatalogFeedState`: Home, Catalog и Search.
Каждая лента хранит собственные query, items, controls, `nextPage`, loading/error,
origin и generation. Дозагрузка главной, каталога и поиска сохраняет identity и
делает GET точного page-route в той же cookie-сессии. Общий `TvPosterGrid` задаёт
одинаковую шестиколоночную раскладку, стабильные D-pad-переходы и ранний
query-aware preload для всех трёх лент; focus coroutine отменяется при смене identity либо
удалении её target, но не при обычном append, а offscreen-переход сдвигает viewport на одну
строку. Production-пагинация по-прежнему координируется
вручную в `KinogoAppRoot`; `HtmlCatalogPagingSource` в этот flow не подключён.

### Авторизация и библиотека

```mermaid
flowchart LR
    A["CredentialStore + Android Keystore"] --> B["KinogoSessionManager"]
    B --> C["origin-scoped cookie session"]
    C --> D["KinogoLibraryApi"]
    D --> E["KinogoLibraryRepository"]
    F["LibraryStateStore + pending outbox"] --> E
    E --> G["LibraryRecord"]
    G --> H["Bookmarks / Details actions"]
```

Credentials постоянны, cookie memory-only. Статус и independent favorite имеют отдельные
coalescing mutations, чтобы последняя команда переживала временную недоступность сервера.

### Воспроизведение

```mermaid
flowchart TD
    A["Play / Continue"] --> B["Fresh detail page"]
    B --> C["Direct media + iframe candidates"]
    C --> D["ProviderEmbedDocumentClient"]
    D --> E["Cinemar / Collaps native adapters"]
    E --> F["PlaybackMediaPlan"]
    C --> G["Validated web fallbacks"]
    D --> H["Optional official-gateway recovery"]
    H --> E
    H --> G
    F --> I["Playback source selection"]
    G --> I
    I --> J["Media3 TvPlayerScreen"]
    I --> K["Explicit provider WebView"]
```

Все media/embed/subtitle URL живут только в памяти подготовленной сессии. История сохраняет
стабильный выбор и позицию, но не URL.

`PlaybackMediaPlan` также владеет последовательностью эпизодических координат. Переходы
Previous/Next сортируют реально существующие варианты по сезону и серии для текущих
источника и озвучки, пропускают отсутствующие сезоны/серии и тем самым одинаково работают
внутри сезона и на его границе. Runtime различает автоматический playlist transition,
`PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM` при отключённом auto-next и финальный
`STATE_ENDED`; завершённый checkpoint сохраняется до смены выбранной серии.
`PlaybackCompletionPolicy` выбирает следующую совместимую координату либо завершает
fullscreen flow. Корневой callback этого flow возвращает пользователя в открытую карточку
материала.

### История

`PlaybackProgressStore` хранит `WatchProgress` по ключу:

```text
contentId + seasonId? + episodeId?
```

Запись содержит selection, position, duration, timestamp, ended и snapshot карточки.
`PlaybackProgressCodec` читает v1 и записывает v2. Snapshot обогащается атомарно, не
перезаписывая более новый checkpoint. `LegacyHistoryDetailsResolver` восстанавливает старые
numeric-only записи через строго ограниченные относительные пути.

## Владение состоянием

| Данные | Где живут | Срок |
| --- | --- | --- |
| Credentials | DataStore `kinogo_auth`, AES/GCM + Keystore | До явного удаления пользователем |
| История, библиотека, зеркала, TV-настройки | DataStore `kinogo_tv_state` | Между запусками |
| Cookies | `KinogoSessionHttpClient`, раздельно по origin | Текущий процесс/сессия |
| Каталог, details, UI selection | Compose state в `KinogoAppRoot` | Текущая composition |
| Prepared media/embed URLs | Redacted in-memory session | Только до закрытия/refresh плеера |
| Серверные статусы/избранное | HTML-сервис + локальный outbox | Синхронизируемые |

Auth DataStore исключён из Android backup и device transfer, потому что Keystore key
device-bound.

## Domain-инварианты

- `CatalogItem.id` стабилен в пределах адаптера; `relativePath` host-independent.
- Film и episodic variants не смешиваются в одном `PlaybackMediaPlan`.
- Комбинация source/season/episode/voiceover/quality уникальна.
- Для series сезон и серия либо присутствуют вместе, либо отсутствуют вместе.
- Сезоны и серии вычисляются из реально доступных вариантов выбранной озвучки.
- Previous/Next для series идут по упорядоченным реальным координатам выбранных source и
  voiceover и могут пересекать границу сезона.
- Естественное окончание последнего доступного варианта завершает player flow и возвращает
  в details; auto-next не создаёт несуществующую серию.
- `WatchProgress` не содержит transient URL.
- `WatchStatus? = null` — отсутствие статусной закладки, а не коллекция всех непросмотренных.
- `favorite` не зависит от status.
- Пользовательский manual mirror не становится trusted до fingerprint/health проверки.

## Точки расширения

### Новая категория каталога

1. Подтвердить маршрут в актуальном server-rendered HTML.
2. Добавить host-independent route в allowlist `CatalogCategory`.
3. Добавить parser fixture, route/parser test и focus/preload test dropdown/grid.
4. Обновить `SERVICE_INTEGRATION`, `PROJECT_STATE`, `CHANGELOG`.

### Новый provider adapter

1. Создать `data/playback/<provider>/` с models/parser/adapter.
2. Разбирать только browser-visible данные без выполнения произвольного JS.
3. Проверить каждое media/subtitle destination через существующую public-DNS boundary.
4. Преобразовать в `PlaybackMediaPlan` через отдельный mapper.
5. Подключить в `KinogoPlaybackPreparationService`.
6. Добавить redacted fixtures и negative security tests.
7. Сохранить явный web fallback там, где native plan построить нельзя.

### Новый экран

Главная TV-навигация сейчас реализована enum `TvDestination` и `when` в `KinogoTvApp`.
Добавление destination требует rail item, focus contract, Back behavior и unit/UI tests.

## Технический долг

- `KinogoAppRoot.kt` слишком велик и совмещает composition, orchestration и use cases.
- Нет ViewModel/state-holder слоя и формализованного DI.
- Production catalog использует ручную пагинацию при наличии отдельного PagingSource.
- xSort session-wide и потому требует общей сериализации даже при независимых UI feed
  states; разделение cookie sessions на browse/search пока не вводилось.
- `reduceMotion` применяется только к части Settings UI.
- Нет CI, dependency verification и автоматического clean-clone smoke test.

Рефакторинг этих пунктов не должен одновременно менять сетевой контракт или playback UX.
Сначала добавляется characterization test, затем переносится один поток, после чего
выполняется аппаратная проверка.
