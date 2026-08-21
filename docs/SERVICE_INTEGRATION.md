# Интеграция с Kinogo

Последнее обновление: **21 августа 2026 года**.

## Граница интеграции

Документированного публичного API сайта для всех нужных функций нет. Production-каталог,
авторизация и серверные закладки работают как обычный браузерный клиент:

- ограниченные HTTPS GET/POST;
- origin-scoped cookies;
- разбор server-rendered HTML и DLE responses;
- повторная авторизация на новом проверенном origin.

Закрытый JSON-шлюз официального Android-приложения не является основным backend приложения.
Он используется только как необязательный recovery path во время подготовки плеера.
Подробности снимка — в [`OFFICIAL_APP_RESEARCH.md`](OFFICIAL_APP_RESEARCH.md).

Сетевой протокол изменчив. Даты исследовательских наблюдений не означают, что endpoint
гарантирован сегодня; контрактные fixtures и live-проверка нужны после любого сбоя.

## Replaceable origins

Встроенные кандидаты в текущем коде:

```text
https://kinogo.parts
https://kinogo.online
```

Это bootstrap candidates, а не криптографическое доказательство «официальности». Их
доступность и service fingerprint проверяются во время работы.

`MirrorUrlNormalizer` принимает только origin:

- схема только `https`;
- без path, query, fragment и user info;
- порт только стандартный 443;
- без IP literals, localhost и reserved/local DNS suffixes;
- IDN нормализуется в ASCII.

## Жизненный цикл зеркала

```mermaid
flowchart LR
    A["Seed / manual / safe redirect"] --> B["QUARANTINED or BUILT_IN"]
    B --> C["bounded health probe"]
    C --> D["HTTPS + public DNS"]
    D --> E["HTML fingerprint"]
    E --> F["fresh usable result"]
    F --> G["select best origin"]
```

- Manual и discovery candidates не trusted до успешной проверки.
- Redirecting origin не наследует доверие конечного origin.
- Безопасная конечная цель redirect добавляется как отдельный candidate и проходит отдельный
  probe.
- Health TTL — 6 часов.
- Refresh ограничивает количество и параллельность probes.
- Challenge/CAPTCHA, 401/403, geo restriction или DRM не являются поводом обходить защиту или
  бесконечно перебирать домены.

В `0.5.0` discovery дополнен узким operator-controlled bootstrap manifest:

```text
https://raw.githubusercontent.com/reziarlleh/KinogoATV/main/config/mirrors.json
```

Это не поиск по интернету и не доказательство подлинности mirror. `MirrorBootstrapClient`
не следует redirect, принимает только exact schema v1 до 32 KiB с ISO timestamps, не более
24 origin и validity не более 120 дней. Неизвестное поле, duplicate, expired/future manifest
или любой недопустимый origin отклоняют весь ответ.

Manifest пока **не имеет отдельной криптографической подписи**: его provenance ограничена
GitHub repository/TLS. Поэтому он не передаёт trust: каждый origin добавляется как
`DISCOVERY + QUARANTINED` и может стать active только после обычной независимой HTTPS/public-DNS/
service-fingerprint health check. Ручной ввод и safe redirect targets сохранены; internet-wide
crawler нет.

Repository snapshot `config/mirrors.json` от `2026-08-15T00:00:00Z` истекает
`2026-11-13T00:00:00Z` и содержит четыре discovery origin:
`https://w.kinogo.solar`, `https://kinogo.parts`, `https://kinogo.online` и
`https://kinogo.family`. Это список для последующей проверки, не список подтверждённых
зеркал; в частности, добавление `kinogo.family` не является live fingerprint evidence.
Expiry требует operator review, а не автоматического продления дат.

Ключевые файлы:

- `data/mirror/MirrorRegistry.kt`;
- `data/mirror/MirrorBootstrapClient.kt`;
- `data/mirror/MirrorHealthChecker.kt`;
- `data/mirror/MirrorPreferencesStore.kt`;
- `data/network/ResilientPublicDns.kt`.

## HTTP-клиенты

`SafeHtmlClient` и `KinogoSessionHttpClient` выполняют:

- HTTPS/public-DNS destination validation;
- ограничение redirect;
- ограничение размера документа;
- проверку content type и service fingerprint;
- нормализацию terminal same-origin relative path;
- redaction чувствительных данных в диагностике.

Stateful DLE-потоки каталога, авторизации и серверной библиотеки используют общий
`KinogoSessionHttpClient` только по HTTP/1.1. Это адресное transport-ограничение введено
после аппаратно наблюдавшегося на Android TV тайм-аута response headers внутри HTTP/2
stream; тот же origin по HTTP/1.1 отвечал штатно. Отдельные playback-клиенты и transient
media requests не входят в эту cookie-сессию и не наследуют ограничение автоматически.

Cookie jar разделён по origin. Cookies и password POST нельзя переносить через cross-origin
redirect. При смене зеркала `KinogoSessionManager` входит на новом origin сохранёнными
credentials.

Playback provider cookies не входят в эту DLE cookie-session. Provider WebView хранит только
собственное first-party browser state; Cinemar native grant от 21.08.2026 подтверждён без
cookies и выполняется отдельным no-cookie exact-origin клиентом.

### Live playback snapshot 2026-08-21

У Cinemar сохранились вызов `Cinemar({...})` и декодируемый `#2` playlist envelope, но leaf
больше не обязан содержать готовый media URL. Новая browser-visible форма содержит
placeholder `file` и opaque `data`. Один выбранный leaf обменивается JSON-string POST на
same-origin `/api/playlist/load`; ответ содержит HLS grant. На проверенном сериале было 45
таких leaves, поэтому приложение не выполняет eager hydration всего дерева.

Authenticated detail текущего сервиса возвращает player document непосредственно на
непрозрачном runtime route exact host `cinemar.cc`, а не обязательно на публичном
`/embed/...`. Это уже discovered player document, не новый общий route allowlist. Discovery
остаётся strict exact-host `/embed/...`; runtime document допускается отдельной проверкой
только для exact HTTPS `cinemar.cc`, non-root/non-`/api/`, без query/fragment/userinfo и
нестандартного порта. Старый общий validator отклонял этот flow как
`INVALID_EMBED_ADDRESS`.

`CinemarGrantClient` принимает только exact origin/path, не использует cookie jar, не следует
redirect, не повторяет POST после transport failure, ограничивает JSON 512 KiB и повторно
проверяет HTTPS/public-DNS для media/subtitle. Endpoint всегда строится как fixed same-origin
`/api/playlist/load`, а не берётся из HTML. Opaque token принадлежит session-owned resolver
и исчезает вместе с media plan.

На KIVI Android TV 14 этот current contract подтвердил native selector Cinemar с
озвучками, сезонами 1–4 и сериями для «Далеко во Вселенной», затем Media3 S2E5 с
продвижением 11:01 → 11:39. Exact runtime path, token и media URL в evidence не записаны.

## Каталог, фильтры и поиск

### Live snapshot 2026-08-01

На момент проверки оба bootstrap origin, `https://kinogo.parts` и
`https://kinogo.online`, перенаправляли на `https://w.kinogo.solar/`. Это датированное
наблюдение, а не постоянный адрес и не доказательство официальности домена. Raw filter block
на трёх адресах совпадал; приложение по-прежнему использует выбранный проверенный origin и
не сохраняет конечный host в карточках.

15 августа 2026 года `parts` и `online` снова указывали redirect target
`https://w.kinogo.solar`. Прямой service fingerprint в той проверке не был получен:
ответы закончились timeout/403/empty body. Поэтому `w.kinogo.solar` включён в
remote bootstrap только как quarantined candidate, а не как built-in trusted origin.

Текущий DLE-шаблон использует stateful xSort, а не отдельные GET-маршруты года, страны и
жанра. Корневые маршруты задают только ленту:

| Назначение | Относительный путь |
| --- | --- |
| Главная | `/` |
| Категория | один из allowlisted путей ниже |
| Поиск | `/search/{percent-encoded term}/` |
| Страница главной | `/page/{n}/` |
| Страница категории | `{category-base}page/{n}/`, например `/filmy/page/2/` |
| Страница поиска | `/search/{percent-encoded term}/page/{n}/` |

`KinogoHtmlParser` определяет наличие следующей страницы по `.pagiNation a[href]` и номеру
`/page/{n}/` либо ссылке `Позже`. `KinogoRoutes` затем строит page-route для той же базовой
ленты. Один paging generation закреплён за origin и query identity; карточки добавляются по
стабильному ID без повторов. Search является отдельным режимом и не комбинируется с
категорией или browse filters.

Клиентская политика загрузки поверх этого wire contract:

- `TvPosterGrid` просит следующую страницу, когда под строкой сфокусированной карточки
  остаётся меньше двух загруженных строк; один и тот же query-aware boundary запрашивается
  только один раз;
- Home после первой страницы автоматически следует только по строго возрастающему
  `nextPage`, пока не накопит минимум 18 уникальных карточек (`3 × 6`) либо пока сервер не
  завершит выдачу; повторы ID не засчитываются в этот резерв;
- скрытая загрузка Catalog после Home-reserve не выполняется: она конкурировала с видимой
  лентой за общую origin-scoped xSort-сессию;
- первая страница Catalog с default `CatalogCategory.NEW_RELEASES` и маршрутом `/novinki/`
  планируется при прямом входе пользователя. Категории, их allowlist и маршруты этим
  правилом не меняются.

### Категории

Когда ответ содержит sidebar, категории читаются из `aside#sideBar .categories` и
`.bySearials`/`.bySerials`, но href принимается только при совпадении same-origin relative
path с `CatalogCategory`. xSort POST может вернуть полноценный документ либо fragment без
sidebar: непустой разобранный subset сохраняется как есть, а только при пустом списке UI
использует ровно 28 проверенных `CatalogCategory.entries`. Произвольный href не становится
fallback-пунктом. Числа рядом с категориями изменчивы и в модель не входят.

Фильмы, в порядке текущего сайта:

| Название | Путь | Название | Путь |
| --- | --- | --- | --- |
| Все фильмы | `/filmy/` | Мультфильмы | `/multfilmy/` |
| Новинки | `/novinki/` | Фантастика | `/fantastika/` |
| Фэнтези | `/fjentezi/` | Нуар | `/nuar/` |
| Ужасы | `/uzhasy/` | Триллер | `/triller/` |
| Спорт | `/sport/` | Приключения | `/prikljuchenija/` |
| Исторические | `/istoricheskie/` | Мюзикл | `/mjuzikl/` |
| Мелодрама | `/melodrama/` | Короткометражка | `/korotkometrazhka/` |
| Криминал | `/kriminal/` | Драма | `/drama/` |
| Комедия | `/komedija/` | Документальные | `/dokumentalnye/` |
| Детектив | `/detektiv/` | Детский | `/detskij/` |
| Военный | `/voennyj/` | Вестерн | `/vestern/` |

Сериалы:

| Название | Путь | Название | Путь |
| --- | --- | --- | --- |
| Все сериалы | `/serialy/` | Зарубежные | `/zarubezhnye-serialy/` |
| Русские | `/russkie-serialy/` | Мультсериалы | `/multserialy/` |
| Аниме-сериалы | `/anime-serialy/` | Аниме | `/anime/` |

### xSort contract

Контейнер управления — `.xsort-area`; каждый список имеет вид
`.xsort-ul[data-field] > li[data-val]`, активный пункт отмечен `li.current`, отображаемое
значение находится в `.xsort-selected`, сброс — в `.xsort-div-clearall`, а карточки ответа —
в `#dle-content`.

Поддерживаются ровно четыре server fields:

| Поле | Назначение | Источник вариантов |
| --- | --- | --- |
| `defaultsort` | серверная сортировка | allowlisted wire values из HTML |
| `podborki` | подборка | динамически из HTML |
| `year` | год | динамически из HTML |
| `country` | страна | динамически из HTML |

Wire values сортировки: `date`, `rating`, `views_top`, `views`, `comm`, `year`, `kp`. На
главной в snapshot отсутствовал пустой вариант, текущим был `views_top` с направлением
DESC. На `/filmy/` и `/serialy/` перед теми же семью вариантами присутствовал пустой
`data-val=""` с подписью `по умолчанию`.

В snapshot списки `podborki`, `year` и `country` содержали соответственно 98, 88 и 101
`li`, включая пустые placeholders. Годы шли от 2026 до 1940; список стран содержал 100
значений. Эти размеры и тексты не хардкодятся: UI получает только разобранные серверные
варианты. Пять элементов `podborki` с неэкранированными кавычками имели повреждённый
`data-val`; parser пропускает только конкретный элемент, если нормализованные `data-val` и
видимая метка не совпадают, не теряя остальные варианты.

Изменение browse identity выполняется последовательно под mutex:

1. POST на base route с form-urlencoded body
   `xsort=1&xs_field=clearallfields`;
2. разбор доступных controls из HTML-документа либо xSort fragment;
3. по одному POST для выбранных значений в порядке sort, collection, year, country:
   `xsort=1&xs_field={field}&xs_value={value}`;
4. разбор последнего ответа либо GET соответствующего `/page/{n}/`;
5. проверка, что активные sort/direction/collection/year/country в ответе совпадают с явно
   запрошенными значениями; несовпадение не кэшируется и не добавляется к старой выдаче.

xSort хранит состояние в origin-scoped cookie session. Все POST и следующие page GET
обязаны идти через один `KinogoSessionHttpClient`; перенос cookies на другой origin
запрещён. При переключении между Главной и Каталогом repository заново применяет identity,
поскольку серверная xSort-сессия общая. Transport публикует только числовой session epoch,
не содержимое cookies: фактическая cookie mutation или clear инвалидирует applied-query
cache, поэтому после входа или переподключения фильтры применяются заново.
Если epoch меняется конкурентно с catalog transaction, repository ограниченно повторяет
clear/apply/page целиком; частичный ответ не возвращается в UI.

При network failure repository делает ровно одну повторную попытку всей transaction,
начиная с `clearallfields` и заново отправляя все выбранные команды. Нельзя повторять только
упавший POST: одинаковая команда сортировки является toggle и может перевернуть `xasc` /
`xdesc`. При сетевом сбое, отмене coroutine либо любом другом незавершённом применении
`appliedQuery` инвалидируется, поэтому следующий запрос не доверяет частично изменённой
серверной сессии.

Home auto-chain и прямой Catalog load не должны обходить эту границу. Каждый page
transaction сериализуется тем же repository mutex. Явный переход в Catalog планирует
запрос сразу, но безопасно ждёт текущую mutex transaction, если та уже выполняется. После
выдачи mutex нужная identity применяется и подтверждается заново; параллельных xSort POST
в одной cookie-session нет. Невидимый Catalog warm-up отсутствует, чтобы не отнимать эту
последовательную сессию у активной ленты.

Отдельного `xs_order` нет: сервер меняет `xasc`/`xdesc` повторным идентичным POST текущей
сортировки. В приложении поле и направление разделены: повторный выбор того же пункта
dropdown не меняет направление, это делает только отдельная кнопка `↑`/`↓`. Repository
отправляет необходимое число одинаковых POST, чтобы получить выбранное состояние.

Не смешивать HTML wire values с идентификаторами закрытого шлюза официального приложения
(`top`, `comm_num`, `news_read` и другими): это разные контракты.

`KinogoHtmlParser` также извлекает карточки, details metadata, description, player notice и
iframe candidates. ID и `relativePath` не должны включать active host.

При изменении HTML:

1. Сохранить минимальный redacted fixture без cookies и media tokens.
2. Добавить failing contract test.
3. Исправить parser, не UI.
4. Сохранить content-size/fingerprint boundary.
5. Выполнить live read-only проверку главной, категории, xSort response, следующей страницы
   и поиска без account mutations.
6. Обновить этот документ и `CHANGELOG.md`.

## Авторизация

Текущий HTML-flow:

```text
POST /
login_name=<login>
login_password=<password>
login=submit
```

Успешность определяется по авторизованному HTML (`dle_group != 5`); свежий
`dle_login_hash` используется там, где DLE требует user hash.

Credentials:

- постоянно сохраняются по продуктовому требованию;
- находятся в отдельном DataStore `kinogo_auth`;
- шифруются AES-256/GCM через non-exportable Android Keystore key;
- не входят в Android backup/device transfer;
- удаляются только явным действием пользователя.

Cookie session memory-only. Истёкшая или сменившая origin сессия восстанавливается через
сохранённые credentials.

### Регистрация

`KinogoRegistrationApi` реализует двухшаговый browser-visible DLE flow
`/index.php?do=register`. Первый ответ может быть отдельной страницей правил:
`RegistrationHtmlParser` возвращает `RegistrationDocument.Rules`, UI показывает текст и по
умолчанию фокусирует `Не принимаю`. Hidden `dle_rules_accept` POST отправляется только после
явного выбора `Принимаю и продолжить`; account form до этого не подставляется и не
сабмитится.

На втором шаге имена login/e-mail/password/confirmation, submit и hidden fields берутся из
текущего HTML с ограничениями размера/имени; action за пределами selected origin
отклоняется. Вводимые login/e-mail/password/CAPTCHA живут только в Compose `remember`, а не
в `rememberSaveable`/DataStore. После dismiss значения не восстанавливаются. Только после
успешной регистрации login/password проходят существующий encrypted `saveAndLogin` flow.

Image CAPTCHA получается тем же cookie transport с лимитом 512 KiB и проверкой
bitmap signature/content type. Перед Compose decode дополнительно проверяются размеры:
не более 4096 px по каждой стороне и 8 млн pixels; крупное допустимое изображение
downsample-ится до bounded 840×256 target в RGB_565. Код вводит пользователь; ни обхода, ни
external recognition нет. reCAPTCHA/hCaptcha/Turnstile возвращают explicit unsupported, а
не fallback с ослабленной защитой. После server rejection повторно загружаются вся форма и
CAPTCHA.

Каждый load/rules-accept/submit имеет монотонную registration generation и исходный origin.
Response применяется только если оба всё ещё актуальны: late result после retry, dismiss
или смены зеркала не может снова открыть старую форму, сохранить credentials либо изменить
UI текущего origin.

Форма/parser/API покрыты offline tests. Final hardware D-pad instrumentation `OK (1)`
подтвердил default decline, explicit accept и возврат из нижней границы rules scroll на
`Не принимаю`; test package удалён. Live registration submit остаётся pending.

## Серверные закладки

Статусная закладка меняется через DLE mylist:

```text
POST /engine/ajax/controller.php?mod=mylist
post_id=<DLE post id>&folder=watch|done|todo|drop|0
```

Соответствие:

| UI | Folder |
| --- | --- |
| Смотрю | `watch` |
| Смотрел | `done` |
| Буду | `todo` |
| Бросил | `drop` |
| Не смотрел | `0` / `status = null` |

«Не смотрел» удаляет material из статусных закладок. Оно не добавляет материал в огромный
список «всего непросмотренного» и не изменяет independent favorite.

Статусные страницы:

- `/favorites/watch/`;
- `/favorites/done/`;
- `/favorites/todo/`;
- `/favorites/drop/`.

Independent favorite читается с `/favorites/` и переключается DLE favorites action с
актуальным `user_hash`.

`LibraryStateStore` хранит server snapshot и coalescing outbox отдельно для status/favorite.
При конфликте pending локальная команда имеет приоритет до успешной отправки.

Подробный протокол и ограничения: [`AUTH_AND_SYNC.md`](AUTH_AND_SYNC.md).

## Прогресс просмотра

Точный Media3 checkpoint не является частью account protocol Kinogo. В текущей архитектуре:

- сайт синхронизирует status/favorite;
- TV хранит season/episode/voice/quality/position локально;
- iframe provider может отдельно хранить собственный localStorage, который не равен аккаунту
  сайта.

Не пытайтесь отправлять секунды воспроизведения в неподтверждённый endpoint.

## Обработка ошибок

UI должен показывать пользовательскую причину без URL, cookies и stack trace:

- нет рабочего зеркала;
- network timeout/unreachable;
- service fingerprint не совпал;
- challenge required;
- malformed/unsupported document;
- источник не найден или истёк.

Reset Home или Catalog отменяет устаревший request job той же ленты. При смене фильтра на
том же origin уже загруженные карточки и controls остаются видимыми до успешного ответа и
после transient failure; ошибка не превращает экран в пустой. Search очищается при новом
запросе, а любая смена origin очищает старую выдачу, чтобы данные разных зеркал не
смешивались.

Смена зеркала разрешена только для безопасных idempotent reads и повторной login-сессии.
Нельзя автоматически повторять пользовательскую mutation на другом origin без idempotency
или локального outbox.
