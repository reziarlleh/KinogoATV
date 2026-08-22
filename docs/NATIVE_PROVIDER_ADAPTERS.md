# Карта нативных адаптеров плееров

> **Статус документа:** provider-specific snapshot и подробная инженерная спецификация.
> Текущий production-flow и фактически подключённые возможности см. в
> [`PLAYBACK.md`](PLAYBACK.md) и [`PROJECT_STATE.md`](PROJECT_STATE.md).

Статус: экспериментальные native adapters реализованы 26 июля и актуализированы
21 августа 2026 года. Они
обрабатывают только конфигурацию, которую обычный браузер уже получает внутри iframe,
не выполняют JavaScript, не обходят DRM, авторизацию, referer/cookie, географические
или entitlement-ограничения. Контракт остаётся нестабильным и требует web fallback.

## Вывод

Официальный gateway выдаёт только discovery-данные:
`post_id`, `has_player`, `trailer`, `tabs[]` и `mirrors[]`. В `tabs[]` есть только
`id`, `title`, `balancer`, `iframe_url`, `is_serial`. В нём нет списка сезонов,
серий, озвучек, качества или manifest URL. Поэтому приложение получает каждое
iframe-предложение заново непосредственно перед запуском и передаёт его отдельному
адаптеру.

Сейчас работают три нативных пути:

1. явный HTTPS HLS/DASH/MP4 через `DirectMediaResolver`;
2. Cinemar: разбор JSON-объекта `Cinemar(...)` и его публичного `#2` envelope;
3. Collaps: разбор публичной конфигурации `makePlayer(...)` VenomPlayer.

Оба provider adapter возвращают эфемерную структуру
`источник -> сезон -> серия -> озвучка -> качество -> субтитры`, после чего общий
mapper строит `PlaybackMediaPlan` для Media3. Если структура изменилась, требует
удалённый JS/DRM или не проходит HTTPS/public-DNS boundary, приложение сохраняет
изолированный полноэкранный web-плеер как явную альтернативу.

## Зафиксированные наблюдения

| Источник | Что подтверждено | Чего нет |
| --- | --- | --- |
| Официальный APK / gateway | `GET /v1/post/{id}/player` с именованными `tabs`; gateway требует `X-App-Signature`; у записи из live fixture есть вкладки `cinemar` и `collaps`. | Прямого manifest URL, выбора серии/сезона/озвучки/качества, описания срока жизни или документации провайдеров. |
| Cinemar | Discovery `/embed/...` и current authenticated exact-host runtime player document на `cinemar.cc`; вызов `Cinemar({...})`, публичный `#2` envelope и playlist-tree. Leaf содержит opaque `data`, а реальный HLS выдаёт fixed same-origin `POST /api/playlist/load`; cookies не требуются. KIVI подтвердил native selector с озвучками/сезонами/сериями и Media3 S2E5 >15 с. | Гарантированный TTL и стабильная схема; не-HLS grants, JavaScript-computed/DRM варианты остаются web-only. |
| Collaps | Exact origin `api.ortified.ws`, `/embed/...`, публичный `makePlayer({...})`; movie и season/episode playlist, HLS/DASH/file, порядок audio tracks и subtitles. Live-снимок дал 2 manifests, 2 audio и 4 subtitles. | Гарантия неизменности VenomPlayer-конфига и поддержка remote playlist/DRM; эти формы остаются web-only. |
| Alloha | В старом исследовании и gateway-поиске встречалось имя Alloha; test fixture намеренно проверяет, что неизвестная вкладка не выбирается. | В актуальном player fixture нет именованной Alloha-вкладки; нет origin, API-контракта или media metadata. |
| Kodik | В исследованных APK, gateway JSON и site fixtures не обнаружен. | Идентификация, endpoint, host allowlist, metadata и native delivery contract. |
| Динамические hosts из `mirrors[]` | В live fixture были динамический subdomain-host и токенизированный host. Поле `provider` — число без опубликованной таблицы соответствия. | Стабильная принадлежность, доверенный origin, право передачи cookies/referer, прямое медиа. |

Первичный live-research датирован 22 июля 2026 года; Cinemar contract повторно проверен
21 августа 2026 года. В новой схеме массовая hydration всех playlist leaves запрещена:
grant запрашивается лениво только при открытии выбранного элемента Media3. В частности,
у Cinemar успешные повторные GET наблюдались примерно в течение 15 секунд после
получения страницы, но верхняя граница TTL не измерялась. Это не разрешает кэширование
или повторное использование offer: каждый offer надо считать одноразовым и только
in-memory. Полные tokenized URL не приводятся ни здесь, ни в логах.

Текущий authenticated detail дополнительно показал второй route-class: уже discovered
player document может находиться на непрозрачном non-`/embed/` path exact host `cinemar.cc`.
Старый общий validator возвращал `INVALID_EMBED_ADDRESS`. Исправление не ослабляет discovery:
новый offer всё ещё принимается только как `/embed/...`, а `validatedPlayerDocumentUri`
применяется лишь после discovery и отклоняет root, `/api/`, query, fragment, userinfo,
нестандартный порт и любой другой host.

## Идентификация и discovery

1. Сначала получить карточку только от уже проверенного Kinogo origin. Его поля
   (тип, год, `kinopoisk_id`, качество и перевод) полезны для сопоставления контента,
   но не являются media metadata провайдера.
2. Если пользователь включил опциональный gateway, выполнить свежий
   `lightsearch`, затем `/v1/post/{gatewayId}/player`. Сопоставление строгое:
   нормализованные title + year и, когда есть, original title / `kinopoisk_id`.
   Нет точного единственного совпадения — остановиться.
3. Принимать только `tabs[]` с известным текстовым `balancer`; новый Cinemar offer проходит
   strict exact-host `/embed/...` discovery policy. Каждый fresh URL и
   каждый redirect проверять как HTTPS/public-DNS, затем привязывать web fallback к
   exact origin именно этого предложения. Дедуплицировать только в памяти.
   `mirrors[]` не использовать:
   числовой `provider` и динамический адрес не образуют trust relationship.
4. Загрузить player document без cookies и без автоматических redirect, с ограничением
   размера. После уже подтверждённого Cinemar discovery допускается exact-host runtime
   document через отдельную non-root/non-`/api/` policy; это не разрешение произвольного
   route discovery. Адаптер читает только browser-visible конфигурацию, не выполняет script.
5. Проверить каждый media/subtitle endpoint до передачи Media3. URL остаются только
   в активном `PreparedPlaybackSession`; `toString()` их скрывает.
6. Для Cinemar deferred leaf сохранить token только в session-owned resolver, поместить в
   plan случайную локальную ссылку и обменять token ровно один раз при первом Media3 open.
   Exact endpoint — fixed same-origin `/api/playlist/load`; no cookies, no redirects,
   no transport retry, bounded JSON.

Не допускается использовать имя балансира, hostname suffix, HTML-строку `m3u8` или
успешную загрузку iframe как доказательство поддержки. Нельзя угадывать mapping
`mirrors[].provider`; динамические offers необходимо показывать как неизвестный
источник или игнорировать.

## Контракт native adapter

Предпочтительный долгосрочный вариант — версионированная спецификация провайдера.
Текущие browser-config adapters реализуют ту же внутреннюю форму:

```text
discover(content identity) -> ProviderTitle { providerContentId, isSerial, expiresAt? }
listVariants(providerContentId) -> Season[] -> Episode[] -> Voice[] -> Quality[]
requestMedia(selection) -> MediaGrant {
  httpsUrl, kind = HLS | DASH | MP4, expiresAt,
  allowedRequestHeaders, drm = none | supported-scheme
}
```

Условия принятия:

- `httpsUrl` — exact URL, без user-info и fragment; DNS каждого host только на
  публичные адреса. Redirect не следует молча: каждый hop валидируется до запроса,
  а новый origin разрешён только явной спецификацией провайдера.
- `kind` и Content-Type должны согласовываться. Для HLS/DASH проверяются manifest и
  все segment/key/license URL тем же HTTPS/public-DNS/allowlist policy. Никаких
  custom data source, который обходит эту проверку.
- `allowedRequestHeaders` — узкий allowlist из официальной документации, без
  переноса Kinogo cookies, Authorization или page headers. Referer допустим только
  когда он прямо указан поставщиком и нормализован до известного origin/path.
- Cookies из Kinogo и provider session изолированы по exact origin. Никогда не
  пересылать cookie между зеркалом, iframe и CDN; CookieJar по умолчанию пустой.
- DRM: принимать только штатно поддерживаемую Media3 DRM-конфигурацию с
  документированной лицензией и правом пользователя. Не извлекать ключи, PSSH или
  license URL из iframe и не пытаться снять DRM.

### Expiry и retry

`MediaGrant` и iframe-offer не сохраняются в БД, preferences, crash report или
analytics. В лог допускаются provider id, status class, duration и короткий hash,
но не URL, query, Cookie, Authorization, Referer или token.

Cinemar selected-leaf grant является исключением из общего будущего retry guidance ниже:
POST не повторяется после неоднозначного transport failure. Session memoize-ит success или
failure одной попытки; новый запрос возможен только через новый fresh playback plan.

При `401`, `403`, `404` или явном истечении срока один раз заново выполнить
**документированный** `requestMedia` с тем же выбором. При сетевой ошибке —
ограниченный exponential backoff без повторной отправки секретов в лог. Не повторять
старый signed URL, не менять voice/quality самовольно и не переходить к неизвестному
mirror. Если refresh не описан поставщиком, завершить воспроизведение понятной
ошибкой и не пытаться изучать/выполнять iframe.

## Политика SSRF, redirect и доверия

`NetworkDestinationValidator`, `ResilientPublicDns` и `PublicOnlyDns` — базовый
минимум, который должен применяться на discovery, grant, manifest и segment hop.

- Только `https`, canonical host, порт 443 по умолчанию; отклонять IP literals,
  private/loopback/link-local/multicast/documentation ranges, opaque URI, fragment,
  user-info, whitespace/control characters и обратные слэши.
- Отключить automatic redirects в OkHttp/Media3. Текущий browser-config путь следует
  максимум пяти redirect вручную: каждый target обязан быть HTTPS на стандартном порту,
  без user-info/fragment/IP literal и с исключительно публичным DNS. При смене origin
  удаляются Authorization, Cookie, Origin, Proxy-Authorization и Referer. Для будущего
  документированного provider API поверх этой границы следует добавить exact CDN allowlist.
- Gateway origin, Kinogo mirror, provider iframe и CDN — четыре разные зоны
  доверия. Доверие одной зоны не переносится в другую. Manual mirror остаётся в
  quarantine до независимой HTTPS/redirect/content-fingerprint проверки.
- Проверять DNS при каждом новом origin и не строить allowlist из того, что вернул
  provider. Subdomain wildcard запрещён, пока он явно не выдан договором.
- Для Cinemar не объединять три разные политики: discovery `/embed/...`, already discovered
  exact-host runtime player document и fixed grant `/api/playlist/load`. Runtime-document
  validator никогда не применяется к API route и не наследует subdomains.

## Матрица приоритета

| Путь | Технический статус | Правовой/контрактный статус | Решение |
| --- | --- | --- | --- |
| Явный разрешённый direct HLS/DASH/MP4 | Уже поддержан resolver-ом | Нужна законная выдача URL | P0 — использовать после валидации |
| Письменный native API провайдера | Реализуем при наличии схемы выше | Требует соглашения и тестового entitlement | P1 — новый feature-flagged adapter |
| Cinemar browser config | Реализован playlist-tree, HLS/DASH/MP4, subtitles | Недокументированная изменчивая схема | P1 experimental + web fallback |
| Collaps browser config | Реализованы movie/serial, HLS/DASH/file, audio, subtitles | Частично описан VenomPlayer; provider contract изменчив | P1 experimental + web fallback |
| Alloha | Только исторический label | Нет текущего endpoint/договора | P4 — unsupported до документации |
| Kodik | Не наблюдался | Нет данных | P4 — unsupported до документации |
| `mirrors[]` dynamic hosts | Нестабильные tokenized iframe addresses | Неизвестный provider mapping | Reject / ignore |
| Выполнение/эмуляция provider JavaScript, DRM/key extraction | Не реализовано | Не одобрено | Запрещено; web fallback |

## Redacted fixtures и тесты

Ни один fixture не должен содержать работающий signed URL, cookie, bearer token,
полный Referer или opaque payload. Существующий безопасный пример —
`app/src/test/resources/fixtures/playback/gateway_night_business_player.json`: он
использует `.example`, проверяет dedupe Cinemar, допустимый Collaps и игнорирование
Alloha/`mirrors[]`.

Для нового документированного адаптера добавить fixtures только такого вида:

```json
{
  "provider": "provider-id",
  "content": { "id": "fixture-title", "is_serial": true },
  "selection": { "season": 1, "episode": 2, "voice": "fixture-voice", "quality": "1080p" },
  "grant": {
    "url": "https://media.provider.example/path/<redacted-signed-query>",
    "kind": "HLS",
    "expires_at": "2030-01-01T00:00:00Z",
    "headers": ["Accept"]
  }
}
```

Обязательные contract tests: unknown provider and dynamic `mirrors[]` are rejected;
only an exact host/path is accepted; a URL never reaches `toString`/logs; token expiry
causes one documented refresh; redirect to another/ private origin fails; every
selection field survives provider response; DRM/undocumented header/cookie request is
rejected. Test media must be provider-supplied non-production material or a local
test server, never a captured production grant.

## Открытые вопросы

1. Какой конкретный provider готов выдать право и официальный native API?
2. Как он авторизует устройство и пользователя, и как отзывается entitlement?
3. Какие exact CDN origins, redirect chain, headers, TTL и max refresh допустимы?
4. Как представлены serial hierarchy, voices, qualities, subtitles, audio tracks и
   accessibility metadata?
5. Есть ли DRM, и какой штатный Media3 license-flow разрешён контрактом?
6. Какие sandbox fixtures и production-like expiry tests поставщик разрешает?

При любом неизвестном ответе правильный результат — безопасный web fallback либо
явное «источник пока не поддерживается». Нативный parser не должен угадывать структуру,
выполнять код провайдера или ослаблять сетевую границу.
