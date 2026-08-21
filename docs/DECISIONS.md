# Журнал решений

Последнее обновление: **21 августа 2026 года**.

Это краткие ADR. Решение считается действующим, пока здесь явно не отмечено как superseded.
Новый агент не должен менять его как «очевидное упрощение» без отдельного обсуждения.

## D-001 — TV-only, landscape, API 28+

- Дата: 16 июля 2026 года
- Статус: принято

Приложение предназначено для Android TV, горизонтального экрана и обычного пульта.
Touchscreen необязателен. Минимальная версия — Android 9/API 28.

Следствие: любое основное действие должно быть достижимо D-pad + OK + Back; смартфонный
portrait layout и touch-only жесты не входят в продуктовый контракт.

## D-002 — Нативное приложение, не WebView-оболочка

- Дата: 16 июля 2026 года
- Статус: принято

Каталог, поиск, карточки, закладки, история и управление плеером реализуются нативно.
WebView разрешён только как отдельный fullscreen provider fallback.

Следствие: нельзя «быстро исправить» parser regression открытием всего сайта внутри WebView.

## D-003 — Зеркала являются заменяемыми origin

- Дата: 16 июля 2026 года
- Статус: принято

Content identity хранится как stable ID/relative path. Built-in, manual и redirect-discovered
origins проходят health/fingerprint/public-DNS boundary.

Следствие: абсолютный host нельзя вшивать в history, library или catalog models. Похожий HTML
не доказывает официальный статус зеркала.

## D-004 — Credentials сохраняются постоянно, но через Keystore

- Дата: 22 июля 2026 года
- Статус: принято по прямому требованию пользователя

Логин/пароль должны переживать истечение cookie-сессии и повторный запуск. Они хранятся в
отдельном encrypted DataStore с AES/GCM и Android Keystore key.

Следствие: запрет plaintext storage не означает отказ от сохранения пароля. Auth blob
исключён из backup/device transfer.

## D-005 — Семантика закладок совпадает с сайтом

- Дата: 22 июля 2026 года
- Статус: принято

`WATCHING`, `WATCHED`, `PLANNED`, `DROPPED` взаимоисключающие. «Не смотрел» означает
`status = null` и удаляет материал из статусных разделов. `favorite` независим.

Следствие: нельзя создавать список всех материалов без статуса и называть его «Не смотрел».

## D-006 — Точный progress локален и host-independent

- Дата: 22 июля 2026 года
- Статус: принято

History key — content/season/episode. Сохраняются stable selection, position и snapshot
карточки; media URL не сохраняется.

Следствие: смена зеркала не ломает resume. Межустройственная синхронизация exact position
потребует отдельного подтверждённого механизма.

## D-007 — Native provider adapters и явный Web fallback

- Дата: 26 июля 2026 года
- Статус: принято

Browser-visible конфигурация конкретного провайдера разбирается отдельным clean-room adapter
и маппится в общий Media3 plan. Неизвестный или JS-only provider остаётся явно выбранным
Web fallback, если безопасен.

Следствие: универсального небезопасного «парсера всех iframe» нет; каждый adapter имеет
fixtures, limits и destination tests.

## D-008 — Единый контракт управления плеером

- Дата: 26–28 июля 2026 года
- Статус: принято

Первый OK при скрытом HUD только показывает его. Hidden Left/Right выполняет seek и
фокусирует timeline. Episodes доступны в нижнем row; selectors находятся в одной компактной
строке. Media keys работают независимо от Compose focus. Previous/Next перемещаются по
реальным координатам выбранных source/voiceover и пересекают границу сезона. При
естественном окончании последнего доступного материала player flow возвращается в details;
при включённом auto-next следующая совместимая серия запускается до этого возврата.

Следствие: UI polish не должен возвращать pointer-only управление или случайную паузу при
первом OK. Нельзя вычислять следующую серию как простой `episode + 1` или оставлять
пользователя на пустом player/source screen после `STATE_ENDED`.

## D-009 — Нативный первый кадр и startup diagnostics

- Дата: 22 июля 2026 года
- Статус: принято

Activity показывает простой Android View до инициализации Compose/сети, отслеживает стадии
старта и умеет показать/сохранить отчёт.

Следствие: удаление bootstrap ради сокращения кода неприемлемо без эквивалентной runtime
защиты.

## D-010 — Official gateway только optional playback recovery

- Дата: 22–26 июля 2026 года
- Статус: принято

Закрытый gateway официального APK нестабилен и требует app-specific contract. Он не является
startup/catalog dependency и вызывается только после неудачи fresh HTML playback candidates.

Следствие: его отказ не должен блокировать каталог, вход и запуск приложения.

## D-011 — Стабильный signing key вне Git

- Дата: 22 июля 2026 года
- Статус: принято

Пользовательские обновления подписываются одним ключом. Keystore хранится отдельно минимум в
двух backup, не в repository.

Следствие: clean clone может использовать стандартную debug signature; release без stable
key запрещён. Нельзя решать signature mismatch удалением пользовательского приложения.

## D-012 — Clean-room использование сторонних приложений

- Дата: 22–26 июля 2026 года
- Статус: принято; узкое branding-исключение уточнено 29 июля 2026 года

Официальный APK Kinogo и LazyMedia исследовались как поведенческие/UX-референсы. Их code,
UI layouts, decompiled output и исполняемые компоненты не входят в продукт и Git.

По прямому требованию пользователя сделано единственное узкое исключение: в Git хранится
байт-в-байт исходная официальная PNG-иконка Kinogo
`drawable-nodpi/ic_kinogo_original.png` с SHA-256
`8C35D58CD0688611D9B4BFB40EE35293CD86DE3D6275E10B26B675A8CB2410C1`.
TV banner и launcher variants не копируются из APK, а детерминированно компонуются нашим
скриптом из этой иконки, текста `KINOGO`, подписи `for Android TV` и метки `ATV`.

Следствие: branding-исключение не разрешает переносить другие assets, экранные композиции,
код или decompiled output. В репозитории остаются собственные Markdown-выводы,
синтетические fixtures и явно одобренная исходная иконка с документированной provenance.
Иконка не доказывает подлинность конкретного домена и не отменяет replaceable-origin policy
из D-003.

## D-013 — Документация обновляется вместе с кодом

- Дата: 29 июля 2026 года
- Статус: принято по прямому требованию пользователя

`AGENTS.md` и `docs/` являются частью Definition of Done. README остаётся пользовательским;
планы, риски и отказавшиеся идеи находятся в agent docs.

Следствие: задача с изменением поведения не завершена, пока профильные документы, state,
roadmap и changelog не приведены в соответствие.

## D-014 — Фиксированный TV-каркас и единый steel/cyan focus

- Дата: 29 июля 2026 года
- Статус: принято по прямому требованию пользователя

Основной UI использует edge-to-edge горизонтальный TV-каркас: фиксированный компактный
navigation rail слева, более светлый стальной content frame, бирюзовый active/focus color и
общую сетку из шести постеров в строке. Панель не всплывает поверх раздела.

Следствие: safe-area отступы не должны превращаться в большие декоративные поля, размеры
карточек и focus graph нельзя независимо переопределять в каждом экране. Точный контракт
цветов, плотности и D-pad-переходов хранится в `UI_DESIGN.md`.

## D-015 — Только подтверждённые одиночные GET-фильтры каталога

- Дата: 29 июля 2026 года
- Статус: superseded решением D-016 от 1 августа 2026 года

`CatalogQuery` допускает ровно один режим: верхний раздел, поиск либо один серверный
GET-фильтр. На момент принятия были формализованы новинки, год, страна и allowlisted жанры,
а сортировка выполнялась локально над загруженными карточками.

Следствие: UI не должен изображать комбинацию нескольких фильтров или глобальную серверную
сортировку работающими, пока их детерминированный сетевой контракт не подтверждён.

## D-016 — Каталог использует подтверждённый stateful xSort

- Дата: 1 августа 2026 года
- Статус: принято по результатам live read-only исследования и прямым требованиям
  пользователя

Категории представлены точными allowlisted relative paths из текущего HTML. Сортировка,
подборка, год и страна применяются через подтверждённый form-urlencoded xSort POST в одной
origin-scoped cookie-сессии; варианты подборки, года и страны разбираются динамически.
Если fragment не содержит sidebar, empty-only fallback использует ровно 28
`CatalogCategory.entries`; непустой server subset сохраняется без расширения.
Главная, Каталог и Поиск имеют независимые UI feed states, но сетевые смены xSort identity
сериализуются, потому что серверное состояние сессии общее.

Направление сортировки имеет отдельную TV-кнопку: повторный выбор пункта сортировки его не
меняет. Каталог стартует с реальной категории `Новинки`. Следующие страницы Главной,
Каталога и Поиска добавляются в общий стабильный D-pad grid, когда под строкой фокуса
остаётся меньше двух загруженных строк.

Следствие: нельзя возвращать локальную сортировку уже загруженных карточек, выдуманные
фильтры или независимые параллельные xSort-запросы в одной cookie-сессии. При изменении
cookie-session epoch applied identity инвалидируется, а ответ обязан подтвердить явно
запрошенное активное состояние до append; конкурентная смена epoch повторяет transaction
целиком ограниченное число раз. При изменении HTML сначала обновляются
датированный live snapshot, fixtures и contract tests.

## D-017 — Home-reserve предшествует фоновому прогреву Каталога

- Дата: 1 августа 2026 года
- Статус: superseded решением D-018 от 1 августа 2026 года

Главная автоматически цепляет строго возрастающие страницы до минимум 18 уникальных
карточек (`3 × 6`) либо до конца серверной выдачи. Только после этого приложение в фоне
прогревает первую страницу Каталога, не меняя его default-категорию `Новинки`
(`CatalogCategory.NEW_RELEASES`, `/novinki/`). Прямой вход пользователя в Каталог обходит
только этот scheduling gate: запрос Каталога планируется немедленно.

Home chain, фоновый warm-up и прямой Catalog load не обходят общий repository mutex:
origin-scoped xSort cookie-session остаётся последовательной, а каждая смена identity
применяется и проверяется в своей целой transaction. Прямой Catalog request может дождаться
уже выполняющегося Home transaction, но не ждёт заполнения всех 18 карточек.

Следствие: нельзя запускать невидимый Catalog warm-up раньше Home-reserve, распараллеливать
xSort одной cookie-session или считать дубликаты карточек частью порога. Background append
не должен запрашивать UI-фокус: stable IDs и query generation сохраняют текущую D-pad-позицию.
Категория `Новинки`, её группа `Фильмы` и маршрут `/novinki/` этим решением не изменяются.

## D-018 — Видимая лента приоритетна, xSort повторяется только целой transaction

- Дата: 1 августа 2026 года
- Статус: принято для `0.4.3-dev`

Stateful DLE-клиент каталога, авторизации и серверной библиотеки принудительно использует
HTTP/1.1: на Android TV подтверждён тайм-аут response headers внутри HTTP/2 stream, тогда как
HTTP/1.1 до того же сервиса отвечал штатно. Playback-клиенты остаются отдельными и не меняют
transport protocol этим решением.

`HtmlCatalogRepository` сохраняет общий mutex для origin-scoped xSort-сессии. После сетевого
сбоя допускается ровно одна повторная попытка всей transaction — от `clearallfields` через
все выбранные xSort-команды до целевой страницы. Отдельный toggle POST не повторяется,
поскольку это могло бы незаметно изменить направление сортировки. Сетевой сбой, отмена или
любое другое незавершённое применение инвалидирует `appliedQuery`.

Невидимый Catalog warm-up из D-017 удалён: даже отложенный запрос конкурировал с активной
лентой за ту же последовательную серверную сессию. Home по-прежнему формирует резерв из 18
уникальных карточек, а Catalog загружается при прямом входе пользователя. Reset одной ленты
отменяет её устаревший job; Home и Catalog на том же origin сохраняют прежние карточки и
controls во время замены и при transient failure. Search и cross-origin reset очищают
выдачу.

Следствие: нельзя возвращать hidden warm-up, raw retry одиночного xSort POST, параллельные
xSort-команды одной cookie-сессии либо переносить HTTP/1.1-ограничение на playback без
отдельного аппаратного доказательства.

## D-019 — Первый фокус принадлежит navigation rail

- Дата: 15 августа 2026 года
- Статус: принято; C-006 debug TV smoke passed

При cold start selected rail item получает первый focus. Разделы не запрашивают свой
initial focus до явной активации контента. Focused и selected-unfocused состояния визуально
различаются; enum Settings открываются dropdown, boolean — Switch, Left/Right остаются
навигацией.

Следствие: нельзя возвращать конкурирующие initial requests экранов либо скрытые циклические
значения Settings без явного D-pad popup/focus-return контракта.

## D-020 — Resume выбирает newest unfinished, source refresh ограничен unit key

- Дата: 15 августа 2026 года
- Статус: принято; basic resume-return passed, source-recovery runtime pending

History/Catalog/Search выбирают newest unfinished eligible checkpoint content ID, а Details
показывает S/E/position. Playback error допускает одну полную fresh details/provider
preparation на `content/season/episode`; attempted set переживает replacement player.
Автоматическое восстановление position запускается только если normalized fresh selection
совпадает с исходной exact unit; иначе пользователь возвращается в selector с нулевой
позицией, чтобы другая серия не стартовала незаметно.

Следствие: нельзя предпочитать completed default episode более новому unfinished либо
повторять один transient URL/сбрасывать retry guard при remap source/quality.

## D-021 — Updater проверяет GitHub Release, но установку подтверждает Android

- Дата: 15 августа 2026 года
- Статус: частично superseded решением D-028; финальная проверка APK и
  системное подтверждение остаются действующими

Updater принимает только stable Release этого repository с exact tag/asset/digest,
проверяет package/version/code и полное совпадение signer с installed app. APK передаётся
non-exported FileProvider системному Package Installer.

Следствие: silent install, произвольный download URL, signer mismatch и автоматическое
нажатие системного подтверждения запрещены. Unknown-sources permission и финальная установка
остаются явными действиями пользователя.

## D-022 — Регистрация повторяет same-origin форму и не обходит CAPTCHA

- Дата: 15 августа 2026 года
- Статус: принято; rules D-pad instrumentation passed, live submit pending

Flow разделяет DLE rules page и account form. Rules POST выполняется только после явного OK,
а безопасный default focus — `Не принимаю`. Поля/hidden state/image CAPTCHA берутся из
browser-visible same-origin form и живут в памяти; sensitive UI input использует
`remember`, не `rememberSaveable`. CAPTCHA решает пользователь; refresh получает форму
заново. Wire-size и bitmap dimensions/pixels/decode bounded. Generation+origin guard
отбрасывает late response. Интерактивные reCAPTCHA/hCaptcha/Turnstile явно unsupported.

Следствие: нельзя подключать распознавание CAPTCHA, переносить её third party либо ослаблять
origin/cookie boundary ради регистрации.

## D-023 — Unsigned remote manifest только обнаруживает кандидатов

- Дата: 15 августа 2026 года
- Статус: принято

Bounded `config/mirrors.json` с exact GitHub raw path/schema/expiry получает provenance от
repository/TLS, но не криптографическое trust. Любой origin входит только как
`DISCOVERY + QUARANTINED` и проходит существующий health/fingerprint flow.
Текущий snapshot содержит четыре кандидата, включая `kinogo.family`; состав manifest не
является списком trusted/official mirrors.

Следствие: адрес из manifest нельзя сразу делать active/official; если потребуется более
сильная гарантия, вводится отдельная подпись/revocation, а не скрытое повышение trust.

## D-024 — Публичность не означает аффилиацию и не выбирает лицензию автоматически

- Дата: 15 августа 2026 года
- Статус: принято по прямому требованию владельца

Public README сообщает только релевантный пользователю unofficial/non-affiliation/no-hosting
status; он не обязан публиковать внутренний license-status проекта. Выбор лицензии остаётся
отдельным явным решением владельца. Пока такое решение не оформлено соответствующим файлом,
агент не объявляет исходники open source и не предполагает предоставленные права.

About открывает только exact GitHub и Donate.Stream allowlist. `donate_qr.png` предоставлен
владельцем репозитория и добавлен без изменений (SHA-256
`C8DCA7846A344DC83563BA338AB6691286C482A3E612C3083F0CB2D6D042BEEE`); donation
не меняет функции продукта. На KIVI оба exact intent открылись во внешнем Yandex TV
browser.

Следствие: агент не выбирает лицензию без отдельного разрешения, не расширяет URL allowlist
и не заменяет QR перерисованной/сжатой версией.

## D-025 — Back возвращает в исходный раздел с его состоянием

- Дата: 21 августа 2026 года
- Статус: принято; final local canonical guards и KIVI History/Search non-first evidence passed

Details/player flow не сбрасывает shell в Home: исходный `TvDestination`
поднимается в root и снова передаётся как `initialDestination`. Для поиска root
дополнительно владеет строкой, выдачей и stable ID последнего сфокусированного
постера. Stable focused ID также хранится отдельно для Home, Catalog, Bookmarks и History;
смена identity/filter сбрасывает только соответствующий target. До десяти подтверждённых
поисковых запросов хранятся локально в DataStore
и показываются одной горизонтальной TV-строкой. Запись в history происходит только
после явного commit: OK/Enter, принятого голосового результата или выбора recent-query
chip. Промежуточные debounce-строки могут обновлять выдачу, но не history.

Следствие: Back из Details не может выбрасывать в Home, а append/recomposition поиска не
может подменять сохранённый focus первой карточкой. История запросов ограничена,
дедуплицирована без учёта регистра и не содержит результатов/сетевых адресов.
На KIVI подтверждены вторая History card и второй Search result после возврата; query/results
Search сохранились, recent-query row доступна.

## D-026 — Cinemar deferred grant разрешается только для выбранного leaf

- Дата: 21 августа 2026 года
- Статус: принято; final local canonical guards и real KIVI native playback passed

Текущий browser-visible Cinemar отдаёт leaf с `data`, а конечный HLS — только
после same-origin JSON-string POST на exact `/api/playlist/load`. Parser сохраняет
непрозрачный grant в redacted model, но не запрашивает все leaf заранее. Каждый playback
plan владеет отдельным bounded registry: `MediaItem` видит только случайную
`kinogo-cinemar://grant/...` ссылку, а `ResolvingDataSource` обменивает её на HTTPS HLS
непосредственно при открытии. Повторные/конкурентные открытия одного leaf имеют
один in-flight/result в рамках текущей сессии.

Следствие: grant token, iframe и конечный media URL не попадают в логи, persistence и
локальную MediaItem URI. Grant client не переносит cookies, не следует redirect,
сохраняет HTTPS/public-DNS/exact-origin границы и fail-closed отклоняет не-HLS ответ.
Текущий exact-host Cinemar runtime document подтверждён на KIVI: selector показал
озвучки/сезоны 1–4/серии, Media3 S2E5 воспроизводился более 15 секунд.

## D-027 — Web fallback сохраняет provider state только в своём WebView profile

- Дата: 21 августа 2026 года
- Статус: принято; final local canonical guard passed, provider/device verification pending

Для выбранного explicit Cinemar Web fallback разрешены first-party cookies и DOM
storage точного provider origin; third-party cookies запрещены. При выходе приложение
сначала отправляет PlayerJS `pause`, ждёт callback, вызывает `CookieManager.flush()` для
внутреннего WebView profile и только затем dispose; bounded grace
не даёт зависшему renderer заблокировать Back. `stop` не используется, так как он
сбрасывает provider checkpoint. Стабильный `cuid`/playlist item остаются механизмом самого
PlayerJS.

Следствие: это web-to-web resume внутри одного профиля приложения, а не синхронизация
с сайтом/другим устройством и не перенос checkpoint между native Media3 и WebView. Cookies
не экспортируются, не логируются и не копируются в HTML-сессию каталога.
`flush()` закрепляет только internal profile state и не является cookie sync API.

## D-028 — Updater использует signed multi-endpoint manifest, GitHub API — fallback

- Дата: 21 августа 2026 года
- Статус: принято; final local canonical guards/manifest passed, Pages/jsDelivr deployment
  и live updater pending

Первичный update channel читает до четырёх HTTPS manifest endpoints параллельно. Exact
UTF-8 payload в envelope подписан RSA/ECDSA ключом той же signing identity, что и
установленный APK. Payload жёстко задаёт version/name/code, size, SHA-256, срок не более
90 дней и до четырёх download URLs. Несогласованные manifest с одинаковым максимальным
versionCode отклоняются. Default metadata endpoints — GitHub Pages и jsDelivr:
`https://reziarlleh.github.io/KinogoATV/update/manifest.json` и
`https://cdn.jsdelivr.net/gh/reziarlleh/KinogoATV@main/update/manifest.json`. Их deployment/
live-доступность не считаются доказанной до проверки после release. jsDelivr — отдельный
CDN-транспорт, но он по-прежнему берёт manifest из GitHub repository. Дополнительные зашитые в APK
адреса задаются `KINOGO_UPDATE_MANIFEST_URLS`; при недоступности всех подписанных
каналов сохранён strict GitHub Release API fallback.

Следствие: TLS/host сам по себе не даёт update trust; manifest без валидной
подписи installed identity отклоняется. Каждая загрузка по-прежнему проходит
size/SHA/package/version/signer verification и только затем передаётся системному Android
Package Installer. GitHub Pages может быть заменён другим HTTPS CDN без обновления
клиентской trust model, если payload подписан тем же ключом.
Включаемые в signed payload proxy URLs остаются недоверенным best-effort transport:
криптографическую целостность даёт не прокси, а signed size/SHA-256 и final APK signer check.
Operator-owned non-GitHub storage остаётся отдельной pending-задачей.

## D-029 — Cinemar discovery и already discovered player document имеют разные policy

- Дата: 21 августа 2026 года
- Статус: принято; unit/contract и KIVI current-runtime evidence passed

Новый Cinemar offer принимается только как exact HTTPS `cinemar.cc` `/embed/...` на
стандартном порту. Authenticated Kinogo detail, однако, может уже содержать player document
на непрозрачном runtime route того же exact host. Для этой второй стадии
`validatedPlayerDocumentUri` допускает только non-root/non-`/api/` path без
query/fragment/userinfo и нестандартного порта. Subdomains и arbitrary API routes не
наследуют доверие. При безопасном same-origin redirect native config остаётся привязан к
исходному уже проверенному offer, а explicit Web fallback использует validated resolved URL.

Grant route не извлекается из runtime path: endpoint всегда отдельно конструируется как
fixed same-origin `/api/playlist/load`. POST не получает cookies, не следует redirect и не
повторяется после неоднозначного transport failure. Это исправляет
`INVALID_EMBED_ADDRESS`, не превращая exact provider host в общий URL allowlist.

Локальная evidence-граница C-007 для этих решений: canonical command SUCCESS за 4 мин 27 с,
82 suites / 393 tests, 0 failures/errors/skips; lint — 0 errors / 22 warnings / 2 hints;
все assemblies green. Exact release APK — 38 304 478 bytes, SHA-256
`3166898FDFA882DB9A637ECDA6CDA612A5AF0B5F70D30580FD1449A906EBF875`, package
`com.kinogo.atv`, code 15 / `0.5.1`, minSdk 28, targetSdk 37, LEANBACK label verified,
zipalign OK, v2 true,
certificate SHA-256
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`. Exact local
manifest — 1 273 bytes, SHA-256
`3C167F87208077E6EC4717F202F968AD555B800C76043CFCF69B941627323070`, issued
`1787294465`, expires `1794984054` (18 ноября 2026 года, 06:40:54 UTC), четыре URLs.
KIVI подтвердил current Cinemar native playback и History/Search non-first Back/focus.
Final commit, CI, publication/Pages, Web fallback resume и extended TV evidence остаются
**PENDING**.
