# История изменений

Формат близок к Keep a Changelog, но ранние версии являются development milestones, а не
стабильными SemVer releases.

Важно: до 29 июля 2026 года локальная ветка не имела commit history. Ранние записи ниже
честно реконструированы по APK в `dist/SHA256SUMS.txt`, датам файлов, тестам и
пользовательскому циклу проверки. Это milestone history, не точный список коммитов.

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
