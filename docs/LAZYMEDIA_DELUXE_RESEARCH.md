# LazyMedia Deluxe: исследование UX и архитектурных границ

> **Статус документа:** исторический clean-room UX-референс. Локальные APK и производные
> decompile-артефакты исключены из Git; файл не является dependency, source code или текущей
> спецификацией KinogoATV.

Дата проверки: **26 июля 2026**. Это исследование совместимости и UX-референсов
для нативного Android TV клиента. Оно не переносит в продукт код, изображения,
ресурсы, URL провайдеров, обходы ограничений или сетевые контракты LazyMedia.

## Артефакт и происхождение

Скачанный файл: `research/lazymedia/LazyMediaDeluxe_ver3.462.apk`.

| Поле | Значение |
| --- | --- |
| Заявленная версия в канале | `3.462`, сообщение [@lazymediadeluxe/494](https://t.me/lazymediadeluxe/494) от 2026-07-25 23:32:30 UTC |
| Цепочка источника | этот канал указывает `https://bit.ly/lmd_apk`; на момент проверки он делал HTTP 301 на [raw GitHub APK](https://raw.githubusercontent.com/lazycatsoft/lmd/master/apks/LazyMediaDeluxe.apk) |
| Git snapshot источника | `lazycatsoft/lmd`, ветка `master`: `eba4c237878198d6b187368e7810410a5ad638e6` при проверке |
| Размер | 11,149,191 bytes |
| SHA-256 APK | `1D8C76B045BE3CA27B046C82067471910B69EC881BDE338854F7693A42C4A2B3` |
| package / label | `com.lazycatsoftware.lmd` / `LazyMedia Deluxe` |
| versionCode / versionName | `3462` / `3.462` |
| minSdk / targetSdk / compileSdk | `17` / `29` / `31` |
| подпись | v1 и v2 проходят; один подписант: `CN=Andrey Krivulchak, O=LazyCat Software`; SHA-256 сертификата `D1889B79B3A68F85FE4AB103B5E837D67DEBA608C220B2AA55050890EC970927` |

### Уровень доверия к источнику

**Observed:** канал сам ведёт на сокращённый URL, а тот — на APK в указанном
GitHub-репозитории; локальный APK имеет согласованную версию и подпись
`LazyCat Software`. Это наиболее сильная доступная публичная цепочка на дату
проверки.

**Unknown:** публичного криптографического заявления владельца ключа или
независимой официальной страницы разработчика, связывающей Telegram, GitHub и
сертификат, не найдено. Поэтому это *publisher-associated/reliable source*, но
не доказательство «официальности» в строгом смысле. `master` и short URL
изменяемы; воспроизводимым объектом является сохранённый APK и его SHA-256.

## Как проверялось

Локальные результаты не добавлялись в `app/`:

- `aapt dump badging`, `aapt dump permissions`, `aapt dump xmltree` — package,
  SDK, TV manifest, activities, permissions и intent-фильтры;
- `apksigner verify --verbose --print-certs` (Android Build Tools 35.0.0) —
  схемы подписи и certificate digest;
- Apktool 3.0.2 — manifest/resources/smali в `research/lazymedia/apktool-out`;
- JADX 1.5.6 — read-only Java decompile в `research/lazymedia/jadx-out`.
  Завершился с 21 ошибкой декомпиляции из 6,095 классов, но нужные TV- и
  player-классы извлечены; спорные выводы ниже не опираются на ошибочные классы;
- байтовая строковая проверка (`rg -a` по `classes*.dex`) и ресурсы APK.
  Отдельного `strings.exe` в среде не было.

`research/lazymedia/extracted` и `apktool-out` содержат производные от APK
материалы только для аудита; они не являются источниками приложения Kinogo.

## Наблюдаемая TV-форма и навигация

### Observed

- APK действительно TV-ориентирован: содержит `LEANBACK_LAUNCHER`, обязательный
  `android.software.leanback`, TV banner и отдельное дерево
  `ui.tv.activities.*`; touchscreen объявлен необязательным.
- TV путь разделён на главную, секции/списки, карточку, поиск, закладки,
  историю, настройки, фильтры/сортировку и option-экраны. В manifest есть
  отдельные `ActivityTvListPlayerVideo`, `ActivityTvListPlayersTorrent`,
  `ActivityTvOptionsHistory` и `ActivityTvOptionsBookmark`.
- Android TV home channel/deep links предусмотрены для приложения, истории,
  закладок и карточки. Есть отдельный `SearchProvider` и `ACTION_SEARCH`.
- Внутренний экран плеера — полноэкранный `PlayerView` (ExoPlayer) с loading и
  error layer. Оверлей содержит: заголовок/subject, progress, time/duration,
  status, play/pause, external player, aspect, audio tracks, video tracks,
  text/subtitles, info, back, close, orientation и горизонтально прокручиваемый
  ряд элементов контента. Карточки ряда focusable и имеют признак played.
- У плеера есть native `SeekBar`; ExoPlayer сохраняет текущую позицию перед
  остановкой, а activity передаёт текущей записи позицию и длительность перед
  паузой и при смене элемента. В ресурсах также есть отдельная настройка
  управления сохранёнными позициями и настройка удаления связанных данных при
  удалении записи из истории.
- История, закладки, search history и viewing mark существуют как отдельные
  сущности UI; строками подтверждены `Mark as viewed`, `Uncheck a view`,
  `Remove from history` и отдельное управление history / saved player position
  / viewing mark.

### D-pad mapping (observed in `ActivityExoPlayer` + controls fragment)

| Состояние | Наблюдаемое действие |
| --- | --- |
| Оверлей открыт | DPAD left/right скрабят native progress; удержание увеличивает шаг. MEDIA_REWIND / MEDIA_FAST_FORWARD делают то же. DPAD up/down не перехватываются этим обработчиком, поэтому остаются обычной focus-навигацией. |
| Оверлей скрыт: left/right | Открывает оверлей. При включённой настройке `Seek control` фокус стартует на progress, иначе — на основном контроле. |
| Оверлей скрыт: up/down | При включённой настройке `Volume control` направляет события в системную громкость; иначе открывает оверлей. |
| Play/Pause / media keys | `MEDIA_PLAY`, `MEDIA_PAUSE`, `MEDIA_PLAY_PAUSE`, stop и button-like keys меняют состояние native player и показывают оверлей. |
| Channel up/down | Переходят по элементам текущего плейлист-сеанса, когда это доступно. |
| Back / Escape / B | При открытом оверлее закрывают его; дальнейший исходный Back уходит в activity. |

Это точнее, чем общий тезис «пульт поддерживается»: фокус, скраббер и громкость
являются независимыми режимами. Для Kinogo полезна именно такая явная политика
состояний, без эмуляции pointer/cursor для native playback.

### Inferred UX pattern

Уместный чистый референс: card/row -> detail -> короткий option/choice screen
-> playback session; внутри плеера — один предсказуемый overlay с фокусируемыми
действиями и возвращением фокуса. В нашем приложении это следует реализовать
с собственной терминологией, Compose-структурой и model/state, а не повторять
assets, разметку, имена или порядок экранов LazyMedia.

### Unknown

Без запуска на реальном Android TV не подтверждены initial focus после каждого
перехода, animation timing, поведение производителя пульта и точный UX при
ошибке/буферизации. Статический анализ не заменяет hardware D-pad test.

## Сезоны, серии, озвучки, качество и источник

### Observed

- В ресурсах есть `Season`, определения сортировки/фильтрации `by quality` и
  `by translation`, а также набор `server_*` / `server_info_*` и настройки
  включения источников и использования источника во внутреннем поиске.
- Для video selection существует отдельная TV activity,
  `ActivityTvListPlayerVideo`, которая получает serializable
  `playlistsession`; torrent flow отделён в `ActivityTvListPlayersTorrent`.
- Native player отдельно показывает выбор audio/video/text tracks. Это выбор
  дорожек, который доступен только если выбранный media source их предоставляет.
- Предупреждение `webplayer_warning` прямо различает web-player путь: в нём
  позиция и серия не запоминаются средствами приложения, а переход по сериям и
  озвучки выполняются интерфейсом самого web player.
- Настройки и модельные классы разделены как минимум на `models/service`,
  `baseurl`, `checkerurl`, `geo`, `universalsync`, `update`, `webplayer`,
  `player`, `ui.tv` и `ui.touch`; в APK также есть Room, WorkManager, Leanback,
  Android TV provider, ExoPlayer и Cast libraries.

### Inferred architecture (не контракт и не код для воспроизведения)

Это похоже на архитектуру **каталог/поиск -> service registry -> parser/resolver
конкретного сервиса -> playlist session -> один из playback adapters**. В таком
подходе сезоны, серии, перевод/озвучка, качество и источник — данные адаптера,
а не универсальная фиксированная схема базы. Отдельный web player является
fallback-веткой, а native ExoPlayer — другой веткой с собственным resume.

Для Kinogo безопасный вывод — проектировать свой `PlayableVariant` с
`source`, `season`, `episode`, `audioLabel`, `qualityLabel` и стабильным
`variantId`, но считать поля optional и отображать только подтверждённые
данные нашего легального адаптера. Не заимствовать имена сервисов, URL, parser
логику, обходы или схемы авторизации.

### Unknown

- Не доказан точный порядок панелей (source -> season -> episode -> translation
  -> quality либо иной), их обязательность и порядок фокуса: публичные строки и
  обфускация не дают этого утверждать.
- Не доказано, что «озвучка» всегда отдельная панель: это может быть
  provider-level translation, audio track в media source или UI web player.
- Не извлекались запросы, URL источников, токены, JavaScript/iframe логика,
  реальные потоки или DRM. Это вне цели UX-исследования и не должно попадать в
  Kinogo.

## Resume и history: что брать как принцип

### Observed

В native ветке сохраняются position и duration текущего playable item при
pause/stop/switch. История, просмотренность и сохранённая позиция управляются
раздельно; при удалении history есть настройка каскадно удалить связанные
position/viewing-mark данные. Web-player ветка явно не обещает такого resume.

### Рекомендация для Kinogo (inferred)

Хранить progress по ключу `(contentId, sourceId, seasonId?, episodeId?,
variantId?)`, обновлять atomically, а resume предлагать только после проверки,
что выбран тот же воспроизводимый вариант. Удаление истории должно иметь
предсказуемую политику связанных progress/status. Это архитектурный принцип,
не перенос реализации LazyMedia.

## Риски и границы использования

- Это sideload APK, а не Play-distribution: его целостность подтверждена только
  указанным hash и self-identified certificate; повторная загрузка должна
  заново сверять оба значения.
- APK запрашивает, среди прочего, `RECORD_AUDIO`, location, Bluetooth,
  GET_ACCOUNTS, storage, `QUERY_ALL_PACKAGES`, boot/foreground service и
  использует `usesCleartextTraffic=true`; targetSdk 29 и legacy storage.
  Не делать его dependency, sample или security baseline.
- Многопровайдерные parser/balancer подходы изменчивы, могут быть юридически и
  регионально ограничены и не являются надёжным контрактом. В Kinogo сохраняем
  только документированные и разрешённые direct playback adapters.
- Никаких proprietary code/resources/assets из APK не добавлено в production
  код. Research-каталог изолирован; для публикации репозитория следует решить,
  допустимо ли хранить сторонний бинарник и декомпилированные материалы, либо
  оставить только этот hash-verified отчёт.
