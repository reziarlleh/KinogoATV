# Архитектура воспроизведения

Последнее обновление: **21 августа 2026 года**.

## Принцип

Нативный Media3-плеер — основной путь. Provider WebView — отдельный явный fallback, а не
оболочка приложения и не скрытая автоматическая подмена.

Playback layer отделён от каталога: каталог сообщает stable content identity и fresh page
candidates, а provider adapter возвращает унифицированный `PlaybackMediaPlan`.

## Production-flow

```mermaid
flowchart TD
    A["Нажатие Смотреть / Продолжить"] --> B["Fresh details request"]
    B --> C["Direct media candidate"]
    B --> D["До 4 iframe candidates"]
    D --> E["ProviderEmbedDocumentClient"]
    E --> F["Cinemar adapter"]
    E --> G["Collaps adapter"]
    C --> H["DirectMediaResolver"]
    F --> I["PlaybackMediaPlan"]
    G --> I
    H --> I
    D --> J["Validated Web fallback"]
    E --> K["Optional official gateway if page yielded nothing"]
    K --> F
    K --> G
    K --> J
    I --> L["TV selection screen"]
    J --> L
    L --> M["TvPlayerScreen / Media3"]
    L --> N["Explicit ProviderEmbedPlayerScreen"]
    M --> O["Lazy Cinemar grant for selected local reference"]
```

Перед каждым запуском details и provider documents запрашиваются заново. Page preparation
имеет bounded budget 18 секунд; optional gateway recovery — 12 секунд. Gateway не должен
работать при старте приложения и не должен становиться обязательной catalog dependency.

## Поддерживаемые native inputs

### Direct media

`DirectMediaResolver` принимает явно найденные HTTPS HLS, DASH и MP4 locations, прошедшие
destination validation.

### Cinemar

`data/playback/cinemar/`:

- разбирает browser-visible public config;
- поддерживает фильмы и сериалы;
- строит сезоны/серии, озвучки, варианты media и subtitles;
- не выполняет provider JavaScript;
- проверяет все media/subtitle destinations.

Discovery нового Cinemar offer остаётся строгим: только exact HTTPS host `cinemar.cc`,
стандартный порт и непустой `/embed/...` без query/fragment/userinfo. Однако актуальная
authenticated Kinogo detail может уже вернуть player document на непрозрачном exact-host
runtime route. Для этой второй, уже discovered стадии используется отдельный
`validatedPlayerDocumentUri`: разрешён только non-root/non-`/api/` path того же exact host,
по-прежнему без query/fragment/userinfo и нестандартного порта. Это не расширяет discovery
на произвольные Cinemar routes.

Live-контракт от 21 августа 2026 года добавляет deferred leaves: playlist по-прежнему
декодируется из публичного `#2` envelope, но leaf может содержать opaque `data` и пустой
placeholder `file`. Такой leaf попадает в `PlaybackMediaPlan` как случайная локальная ссылка.
Токен и exact iframe остаются в session-owned `CinemarDeferredGrantRegistry`; только когда
Media3 открывает выбранный вариант, registry выполняет один exact-origin JSON-string
`POST /api/playlist/load`, принимает HLS grant и передаёт его безопасному data source.
Grant endpoint не берётся из HTML: он конструируется отдельно на том же origin. Cookies,
redirect и transport retry запрещены; один исход попытки memoize-ится в текущей сессии.

Один registry принадлежит одному media plan и исчезает вместе с playback-сессией. Он
ограничен parser node/document bounds, имеет TTL и single-flight memoization: повторный или
параллельный `open` одной серии не создаёт несколько grant POST. Новый source refresh строит
новый plan/registry и не повторяет старый transient URL.

### Collaps

`data/playback/collaps/`:

- разбирает browser-visible player config;
- поддерживает film, hierarchical seasons и flat episodes;
- отображает audio tracks как voice options;
- поддерживает HLS/DASH/file и subtitles;
- отвергает remote playlist scripts, blocked/DRM-like и unsafe destinations.

### Web fallback

Если native plan построить нельзя, но iframe document прошёл exact-origin/public-DNS
boundary, пользователь может явно открыть provider-only fullscreen WebView.

Web fallback:

- не получает интерфейс сайта Kinogo;
- блокирует navigation за пределы admitted origin;
- запрещает file/content access, mixed content, popup, download, geolocation и permissions;
- отключает third-party cookies;
- сохраняет обычное first-party cookie/DOM-storage состояние только внутри профиля WebView;
- содержит TV HUD и виртуальный D-pad cursor.

При выходе приложение отправляет PlayerJS `pause`, ждёт callback (не более 750 ms), затем
вызывает `CookieManager.flush()` и только после этого уничтожает WebView. Flush сохраняет
лишь внутреннее first-party cookie-состояние профиля WebView: cookies не копируются в
OkHttp/native session и не логируются. `stop` больше не используется, поскольку он сбрасывает
позицию. На
проверенном Cinemar конфиг содержит стабильный `cuid`; штатный механизм PlayerJS может
запоминать playlist item и time в том же browser profile независимо от iframe URL. Это
web-to-web resume, а не экспорт cookies в OkHttp и не межустройственная синхронизация.

На KIVI D-pad selector достиг `Оригинальный web-плеер` (`Смотреть онлайн · cinemar`),
fullscreen WebView запустился и Back чисто вернул Details, затем History. Provider
playlist/position не видны accessibility и безопасным логам; поэтому actual resume после
повторного открытия **не подтверждён** и остаётся отдельным runtime-пунктом.

`PlayerJsCapabilities` и расширенные JS-команды существуют как изолированный код, но полный
унифицированный выбор web-quality/audio/subtitles ещё не подключён к production Web screen.
Не документируйте его как готовый parity с native player.

## Единая медиаматрица

`PlaybackMediaVariant` описывает:

```text
source
season?
episode?
voiceover
quality
media URL
subtitle tracks
optional preferred audio track
```

`PlaybackMediaPlan` гарантирует:

- film и episodic content не смешиваются;
- tuple source/season/episode/voiceover/quality уникален;
- у series есть совместимые season/episode coordinates;
- UI получает только варианты, реально присутствующие в plan.
- optional `PlaybackMediaUrlResolver` живёт только в активном plan, скрывает opaque provider
  token и разрешает локальную ссылку непосредственно на Media3 loader thread.

Список сезонов и серий зависит от выбранной озвучки. UI не строит декартово произведение
несуществующих вариантов.

## Выбор до запуска

После подготовки открывается `PlaybackSourceSelectionScreen`. Он показывает фактический
набор source/voice/season/episode/quality. Сохранённый checkpoint и global preferences
используются как начальный compatible selection.

Карточка details не показывает пустую speculative секцию вариантов. Реальный выбор появляется
только после свежего discovery.

## Native player

Основные файлы:

- `player/ui/TvPlayerScreen.kt`;
- `player/PlaybackCompletionPolicy.kt`;
- `player/PlayerContract.kt`;
- `player/TvPlayerReducer.kt`;
- `player/TvPlayerKeyMapper.kt`;
- `player/Media3PlayerController.kt`;
- `player/SafePlaybackDataSources.kt`.

Media3 `PlayerView` выводит видео без стандартного controller. Весь интерактивный HUD
рисует Compose:

- title/status;
- elapsed/duration и focusable timeline;
- previous/play-pause/next;
- source, season, voiceover, quality и subtitles selectors;
- нижняя episode row для series.

HUD полупрозрачный и полноэкранное видео остаётся видимым.

Сфокусированный timeline не получает прямоугольную рамку. Его текущая позиция обозначается
белой точкой диаметром 12 dp. При Media3 `STATE_BUFFERING` по центру видео появляется
индикатор в затемнённом круге; в `reduceMotion` вместо анимации остаётся статическое кольцо.

## Контракт пульта

| Состояние | Кнопка | Действие |
| --- | --- | --- |
| HUD скрыт | `OK` | Показать HUD; не ставить на паузу первым нажатием |
| HUD скрыт | `Left` / `Right` | Seek на настроенный шаг, показать HUD и сфокусировать timeline |
| HUD скрыт | `Up` / `Down` | Показать HUD |
| HUD открыт | D-pad | Обычная focus navigation; seek только когда focus на timeline |
| Любое | Play/Pause/Stop | Прямое media-действие |
| Любое | Media Previous/Next | Предыдущая/следующая доступная серия, включая границу сезона |
| Любое | Rewind/Fast Forward | Seek на настроенный шаг |
| Series | `0`–`9` | Набрать номер серии; timeout 1,5 секунды или подтверждение `OK` |
| Drawer открыт | `Back` | Закрыть drawer и вернуть focus вызвавшему selector |
| HUD открыт | `Back` | Скрыть HUD |
| HUD скрыт | `Back` | Сохранить checkpoint и вернуться в карточку |

Повторный seek не должен теряться в момент появления HUD. `HudFocusRequest` повторяет
timeline focus на следующих Compose frames, если первый `requestFocus()` вернул `false`.

Previous/Next не вычисляют номер простой арифметикой. `PlaybackMediaPlan` строит
упорядоченный список существующих координат для текущих source и voiceover. Поэтому переход
с последней серии сезона выбирает первую реально доступную серию следующего совместимого
сезона, а переход назад с первой серии — последнюю доступную серию предыдущего. Пропуски в
номерах сезонов и серий не синтезируются.

## История и resume

Источник истины — `WatchProgress`, не Media3 URL.

Checkpoint сохраняется:

- каждые 10 секунд;
- при pause/lifecycle boundary;
- перед сменой variant/source/episode;
- при error/end/exit.

Resume начинает на 5 секунд раньше сохранённой позиции. Eligibility и completion определяет
`WatchProgressRules`: учитывается минимальная просмотренная длительность, 90% и остаток до
конца. История группируется по content ID, но хранит записи отдельных эпизодов.

History, Catalog и Search используют одну `preferredResumeProgress` policy: среди записей
того же content ID выбирается самая новая незавершённая единица с eligible resume position.
Завершённый checkpoint первой серии не маскирует более новую незавершённую серию.
Details показывает season/episode/position в действии, например
`Продолжить S03E07 с 17:42`; если все checkpoints завершены, Continue action не показывается.

Точная позиция локальна для TV и не синхронизируется с аккаунтом сайта.

## Auto-next

Настройка `autoNextEpisode` применяется к Media3 session. Завершение элемента плейлиста
обрабатывается по трём фактическим сигналам Media3:

- автоматический `onMediaItemTransition` внутри сезона сначала сохраняет финальный checkpoint
  старой серии, затем продолжает следующую;
- при отключённом auto-next `pauseAtEndOfMediaItems` выдаёт
  `PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM`: серия получает финальный checkpoint,
  а fullscreen flow сразу возвращается в карточку;
- `STATE_ENDED` фильма или последнего элемента сезонного playlist передаётся
  `PlaybackCompletionPolicy`: он либо запускает первую реально доступную координату
  следующего совместимого сезона для текущих source/voiceover, либо возвращает в карточку.

В `0.5.0` end-of-item pause не трактуется как exit, если `autoNextEpisode=true`: flow ждёт
решения cross-season completion policy. Замена media items на первую серию следующего сезона
явно возобновляет playback, даже если завершившийся item уже сбросил `playWhenReady`.
При отключённом auto-next тот же сигнал по-прежнему сохраняет completion checkpoint и возвращает в
details. Оба варианта покрыты unit policy, но новый runtime-flow ещё нужно подтвердить на TV.

Тот же cross-season порядок используется ручными Previous/Next. Сейчас отдельного
визуального обратного отсчёта следующей серии нет; countdown с Cancel/Play now остаётся в
roadmap.

## Обновление источника при playback error

При Media3 playback error плеер сначала сохраняет checkpoint, затем может один раз
запросить у composition root свежую details/provider preparation. Это не retry старого URL:
новый `PlaybackMediaPlan` строится обычным защищённым discovery-flow, после чего выбор
нормализуется по свежей sparse-матрице. Позиция и automatic restart восстанавливаются только
если normalized selection сохранил exact исходные `contentId/season/episode`. Если unit
изменилась либо исчезла, flow открывает обычный selector с позицией 0 и не запускает другую
серию незаметно.

Автоматический refresh ограничен одной попыткой на stable unit key
`contentId + season? + episode?`. Набор attempted keys переносится в replacement player session,
поэтому тот же failing provider не может создать inter-screen loop. Смена remapped source/quality
не обнуляет лимит; другая серия получает свою одну попытку. После исчерпания лимита
остаётся обычный user-safe error и ручное действие refresh. URL и attempted provider data не сохраняются.
Этот recovery покрыт pure tests; реальный expiry/error на TV для `0.5.0` pending.

Consumed attempted-unit budget объединяется с active session **до** открытия launch screen
и disposal failing Media3. Recovery launch устанавливает discard-on-exit contract. Если до
подготовки пользователь нажал Back, material исчез из известных карточек либо active
verified mirror отсутствует, root очищает active/pending/embed sessions, увеличивает launch
generation и показывает конкретную ошибку. Back из ошибки возвращает в Details; late job не
может resurrect-ить dead player с пустым immutable attempt set.

Три pure safety guards проверяют: persistence budget + discard, неизменность ordinary launch
и explicit early errors для missing content/mirror. Отдельный exact-unit guard проверяет,
что old position не применяется к другому episode.

Focused C-007 runtime на KIVI Android TV 14 подтвердил current exact-host Cinemar route:
native selector «Далеко во Вселенной» показал озвучки, сезоны 1–4 и серии, resume — 10:48;
Media3 S2E5 продвинулся 11:01 → 11:39. Скрытый HUD по `OK` открылся без паузы. Back вернул
Player → Details → History. Отдельно подтверждены возврат/focus второй History card и
второго Search result с сохранённым запросом/выдачей. Actual expired/404 source, exact
same-unit automatic recovery и natural cross-season end остаются отдельными pending сценариями.

## Сетевые ограничения плеера

- Только HTTPS и public DNS destinations.
- Media3 redirect выполняется вручную, максимум пять переходов.
- При смене origin чувствительные headers удаляются.
- Private/local/documentation networks запрещены.
- Embed, media и subtitle URL не сохраняются и скрываются в `toString`.
- Cinemar discovery принимает только `/embed/...`; already discovered runtime player document
  проходит отдельную exact-host/non-root/non-`/api/` проверку без query/fragment/userinfo/
  non-443 port.
- Cinemar grant token не включается в MediaItem URI, storage, cookie jar или diagnostics;
  fixed same-origin grant endpoint не получает cookies, не следует redirect, не повторяет
  transport request и имеет bounded response.
- Истёкший 401/403/404/410 URL требует fresh preparation, а не бесконечного retry того же
  location.
- DRM, remote JavaScript playlist и неизвестный config нельзя обходить.

## Добавление адаптера

Acceptance checklist:

1. Отдельный package models/parser/adapter.
2. Минимальные redacted fixtures для film и series.
3. Malformed, oversized, unsafe, blocked и missing-config tests.
4. Public-DNS validation каждого конечного URL.
5. Mapping в `PlaybackMediaPlan` и проверка уникальности координат.
6. Dependent choice tests.
7. Подключение только в `KinogoPlaybackPreparationService`.
8. Сохранение явного web fallback.
9. Unit/lint/build.
10. Реальный TV-тест start/resume/seek/source/episode/media keys.

Дополнительная целевая спецификация: [`NATIVE_PLAYER_TV_UX.md`](NATIVE_PLAYER_TV_UX.md).
Границы provider adapters: [`NATIVE_PROVIDER_ADAPTERS.md`](NATIVE_PROVIDER_ADAPTERS.md).
