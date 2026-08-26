# Безопасность и границы доверия

Последнее обновление: **26 августа 2026 года**.

## Модель угроз

Приложение получает изменчивый HTML и media descriptors от зеркал и сторонних player
providers. Любой origin, redirect, iframe, media URL и subtitle URL считается недоверенным,
пока не пройдёт соответствующую проверку.

Основные риски:

- lookalike mirror или изменившийся domain;
- SSRF через DNS, redirect или media manifest;
- утечка credentials/cookies на другой origin;
- утечка transient media token через log, crash report или persisted state;
- подмена remote mirror manifest или APK GitHub Release;
- произвольная navigation/permission из WebView;
- потеря signing key и невозможность обновления установленного APK;
- публикация пользовательских фото, APK или декомпилированного стороннего кода.

## Credentials

Пользователь потребовал постоянное сохранение логина и пароля для повторной авторизации.
Реализация:

- отдельный DataStore `kinogo_auth`;
- AES-256/GCM;
- non-exportable key Android Keystore;
- randomized IV и authenticated additional data;
- auth DataStore исключён из cloud backup и device transfer;
- явное удаление аккаунта очищает сохранённые credentials.

Запрещено:

- переносить пароль в обычный `kinogo_tv_state`;
- выводить credentials, encrypted blob или DataStore в console/fixture;
- добавлять «временный» plaintext fallback;
- отправлять password POST через cross-origin redirect.

Keystore скрывает данные от обычного чтения файлов, но не обещает защиту на
скомпрометированном/rooted устройстве. Это соответствует текущей локальной threat model.

## Cookies и сессии

- Cookie jar разделён по exact origin.
- Cookies memory-only и не копируются между зеркалами.
- При смене origin выполняется новый login сохранёнными credentials.
- Sensitive headers удаляются при разрешённой смене origin в playback redirect.
- Логи не должны содержать `Cookie`, `Authorization`, password или full query string.

## Mirror trust

Manual/discovered origin не активируется только потому, что URL похож на Kinogo.

Проверяется:

- HTTPS origin syntax;
- public DNS resolution;
- bounded response/timeout;
- HTTP/content type;
- service HTML fingerprint;
- конечный origin redirect как отдельный candidate.

CAPTCHA, geo block, 401/403 и DRM не обходятся перебором доменов.

Remote `config/mirrors.json` загружается только с exact GitHub raw path, без redirect, и
проходит strict schema/size/count/expiry проверки. У него нет отдельной криптографической
подписи, поэтому даже корректный manifest не наделяет origin доверием. Кандидат остаётся
`DISCOVERY + QUARANTINED` до независимой HTTPS/public-DNS/fingerprint-проверки.
Текущий четырёхадресный snapshot включает `kinogo.family`, но этот факт сам по себе не
является live validation или повышением trust.

## Registration CAPTCHA

Registration hidden fields, CAPTCHA text и bitmap живут только в памяти текущей same-origin
формы и скрыты из `toString`. Изображение ограничено 512 KiB и проверяется по
magic bytes/content type. Пользователь решает challenge сам; приложение не передаёт
картинку/ответ third-party recognition service и не имитирует обход reCAPTCHA/hCaptcha/Turnstile.

DLE rules gate является отдельным первым шагом. Без явного OK пользователя не отправляется
`dle_rules_accept`; default focus находится на `Не принимаю`. Account fields используют
`remember`, но не `rememberSaveable`, поэтому чувствительный ввод не сериализуется при
dismiss/recreation. Load/rules/submit responses защищены generation+exact-origin guard:
устаревший ответ не может примениться после retry, dismiss либо смены зеркала.

512 KiB wire-limit не является единственной защитой bitmap allocation. До decode UI
проверяет границы 4096 px на сторону и 8 млн pixels, затем использует bounded downsample к
840×256/RGB_565. Final hardware D-pad instrumentation `OK (1)` подтвердил rules
default-decline, explicit accept и безопасный выход из нижней границы scroll; test package
удалён. Live account submit остаётся pending.

## SSRF и destination validation

`NetworkDestinationValidator`, `SafeHtmlClient`, `ProviderEmbedDocumentClient` и
`SafePlaybackDataSources` блокируют:

- IP literals;
- loopback, link-local, private, multicast и documentation ranges;
- local/reserved DNS suffixes;
- cleartext HTTP;
- неожиданные ports;
- unsafe redirect;
- слишком длинные chains.

DNS rebinding учитывается проверкой всех полученных адресов. `ResilientPublicDns` может
использовать DoH/system fallback, но результат всё равно проходит public-address policy.

Нельзя исправлять provider 404/timeout отключением этой проверки. Нужно исследовать свежий
browser-visible contract и добавить bounded adapter.

## WebView

Provider WebView допускается только для отдельного validated embed:

- exact admitted origin;
- `usesCleartextTraffic=false`;
- mixed content запрещён;
- file/content access запрещён;
- popup/multiple windows запрещены;
- download, geolocation и runtime permissions запрещены;
- third-party cookies выключены;
- first-party cookies и DOM storage остаются только во внутреннем профиле WebView и не
  экспортируются в DLE/OkHttp/native player;
- external navigation блокируется;
- JavaScript включён только потому, что он нужен самому provider player.

Virtual cursor — UI-механизм, а не разрешение навигации за trust boundary.

## Ephemeral playback data

Не сохраняются:

- media URL;
- iframe URL с token/query;
- subtitle URL;
- official gateway offers;
- provider HTML;
- player cookies/localStorage dump.

`PreparedPlaybackSession`, resolved source и related models должны redacted-форматировать
`toString`. Fixtures содержат только синтетические или очищенные значения.

Допустимое исключение в history codec v3 — короткий stable adapter `sourceId`, необходимый
для точного resume (`cinemar`, `collaps`, `direct-media`). Он проходит non-blank
domain invariant, не является URL/token и не разрешает сохранять provider document,
grant либо конечный поток.

Cinemar deferred token дополнительно не помещается в media URI. Он хранится только в
session-owned `PlaybackMediaUrlResolver`. Три адресные политики намеренно разделены:

- discovery нового Cinemar offer — только exact HTTPS `cinemar.cc` `/embed/...`;
- already discovered player document — exact host, non-root/non-`/api/`, без
  query/fragment/userinfo и нестандартного порта;
- grant — отдельно сконструированный fixed same-origin `/api/playlist/load`.

Grant POST вызывается без cookies/redirect/retry, с лимитом 512 KiB и повторной
HTTPS/public-DNS validation результата. Single-flight future кеширует один success/failure
исход на leaf и исчезает вместе с media plan. Расширение player-document path не расширяет
discovery, не допускает API paths и не наследует subdomains.

Диагностика rejection может записать только provider id, тип исключения и bounded
address shape (host, route-class, наличие query/fragment). Точный runtime path, query,
token, iframe/media URL и cookies не логируются.

## Android backup

Обычные настройки и история могут участвовать в backup по platform policy. Auth DataStore
исключён, потому что Android Keystore key device-bound и восстановленный encrypted blob был
бы бесполезен или приводил бы к ошибке.

## Signing key

`.signing/kinogo-tv-dev.keystore` подписывает устанавливаемые dev builds постоянным
сертификатом. Android разрешит update поверх существующей версии только при совпадении
подписи.

- Keystore не коммитится даже в private repository.
- Нужны минимум две отдельные offline backup-копии.
- Password/alias задаются локальными Gradle properties или защищёнными CI secrets.
- Release без стабильного ключа должен завершаться явной ошибкой.
- Debug build без ключа допустим только для чистого clone/emulator и не сможет обновить
  установленную stable-signed версию.

## Обновления APK

Встроенный updater использует несколько транспортных маршрутов и одну signing identity как
корень доверия. GitHub Pages, jsDelivr и proxy/direct download URLs повышают транспортную
доступность, но не являются независимой от GitHub инфраструктурой: operator-owned
non-GitHub endpoint остаётся отдельной задачей.

`Cache-Control: no-cache` у signed manifest влияет только на freshness транспорта. Он не
обходит HTTPS/public-DNS URL policy, проверку подписи manifest и последующую проверку
package/version/size/SHA/signer APK.

Основной канал — signed manifest, загружаемый максимум с четырёх явно заданных HTTPS
endpoints. В опубликованной `0.5.2` по умолчанию используются GitHub Pages и jsDelivr для
metadata:

- envelope подписан RSA/ECDSA public key сертификата уже установленного APK;
- подписанные поля фиксируют version/name/size/SHA-256, срок не более 90 дней и до четырёх
  HTTPS download locations;
- metadata endpoints опрашиваются параллельно с 20-second bound, redirect запрещён;
- APK redirect ограничен четырьмя hops, каждый host проходит public-only DNS;
- manifest replay после expiry и конфликт одинакового versionCode отклоняются.

GitHub Release остаётся compatibility fallback:

- metadata запрашивается по exact `api.github.com/repos/reziarlleh/KinogoATV/releases/latest`;
- draft/prerelease отклоняются; tag, versionName, versionCode и имя
  `KinogoATV-<version>-code<code>.apk` должны совпасть;
- asset не может быть больше 200 MiB и обязан иметь GitHub `sha256:` digest;
- initial download URL должен быть exact release path; разрешено не более четырёх
  ручных redirect только на заданные GitHub CDN hosts; public-DNS policy сохраняется;
- до открытия installer сверяются длина, SHA-256, package name, version name/code, рост
  versionCode и полное совпадение signing certificate с установленным приложением;
- APK передаётся системному Android Package Installer через non-exported `FileProvider`.

Ни TLS/CDN, ни manifest сами по себе не заменяют финальную проверку APK: общий verifier
сверяет length, SHA-256, package, version и точную signing certificate identity.

Provider WebView при выходе выполняет PlayerJS `pause`, затем
`CookieManager.flush()`. Это сохраняет состояние только во внутреннем профиле WebView;
cookie-данные не экспортируются в native/OkHttp, updater или журналы.

Updater не может установить APK тихо. Если permission unknown-sources не выдан, Android
сначала открывает системные настройки. В любом случае финальная установка требует
явного OS confirmation пользователя.

Для `0.5.2` проверены exact Release asset и GitHub lowercase digest, signed manifest
размером 1 273 bytes с SHA-256
`BCB6699708CC2C6FF4A71F8379032F709742AC714440622F179130D5AFA80E94`, его успешный Pages
deployment и точное совпадение опубликованных bytes: manifest через Pages/jsDelivr, APK
через Pages/ghfast/ghproxy/direct. Это доказывает публикацию и криптографические входы
verifier, но не runtime встроенного updater: check/download/verify/Package Installer на TV
остаётся **PENDING**, ADB по политике владельца не запускался.

Stable-signed candidate `0.5.3` собран из exact source
`777c8a0528f24db67402536631257d6cdc91f148`; embedded revision совпадает с ним. APK
`KinogoATV-0.5.3-code17.apk` имеет `38,386,398` bytes и SHA-256
`3C88DF356A9815865DB02F7821DA53BE3C6E25F03FE493516FCCAF0F48F0C17A`; package
`com.kinogo.atv`, versionCode `17`, min/target SDK `28/37`, `zipalign` **PASS**, ровно один
v2 signer с certificate SHA-256
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`. Canonical source
verification прошла как `89 suites / 455 tests` за `7m12s`, post-commit release rerun —
за `4m04s`.

App/docs PR #5 merged как `0473a820eefedea16ce2f393df568c90e5b30bbe`; PR CI
`32920452170` и main CI `32920746857` — **PASS**. Annotated release tag `v0.5.3`
указывает на `0473a820`, regular latest Release содержит exact asset выше. Manifest source
`7faebbba8d305a0c339f6966e7759ec7c7f96b90` прошёл PR #6 и merged как
`ff7f5f8eea9776ef626010fe57993dc1906f5d4a`; PR CI `32921520976`, main Android
`32921627748` и Pages `32921627746` — **PASS**. Signed manifest: 1 273 bytes, SHA-256
`860D90C22D9F404A38E783BD313A9E9A0FDEFC5BC870F933A819D35145489977`, issued
`2026-08-26T02:04:54Z`, expires `2026-09-25T02:04:54Z`.

Live exact bytes подтверждены для Pages manifest+APK, jsDelivr manifest после targeted
purge и ghfast/ghproxy/direct GitHub APK. Это транспортное разнообразие, а не независимая
инфраструктура: все заявленные пути зависят от GitHub assets, operator-owned host отсутствует.
In-app check/download/verify/install и hardware runtime остаются **PENDING**; release tag не
является playback baseline tag.

## Repository hygiene

В Git входят собственные source, tests, fixtures и Markdown-выводы. Дополнительно по прямому
требованию пользователя разрешён один точно идентифицированный branding bitmap:
`drawable-nodpi/ic_kinogo_original.png`, SHA-256
`8C35D58CD0688611D9B4BFB40EE35293CD86DE3D6275E10B26B675A8CB2410C1`.
Его provenance зафиксирована в `OFFICIAL_APP_RESEARCH.md`; launcher/banner derivatives
создаются детерминированно нашим скриптом.

Исключены:

- `.signing/`, `.tools/`, `.gradle-user-home/`, build outputs;
- `.codex-remote-attachments/` и локальные agent files;
- `research/` со сторонними APK, smali, JADX/apktool output и screenshots;
- все остальные assets сторонних APK, их UI layouts и decompiled code;
- APK/AAB/IDSIG, keystore/cert/private-key форматы;
- `.env`, `local.properties` и secrets properties;
- пользовательские DataStore, logs и crash reports.

Repository публичен по адресу `https://github.com/reziarlleh/KinogoATV`. APK публикуются как
GitHub Release assets с SHA-256, а не как Git blobs. Regular latest release `v0.5.2`
содержит exact asset `KinogoATV-0.5.2-code16.apk`; update manifest хранит только публичные
release/transport URLs и подписанные метаданные, но не credentials, tokens или private key.

### About и поддержка автора

`app/src/main/res/drawable-nodpi/donate_qr.png` предоставлен непосредственно владельцем
репозитория и сохранён без изменений. Зафиксированный SHA-256:
`C8DCA7846A344DC83563BA338AB6691286C482A3E612C3083F0CB2D6D042BEEE`.
Это provenance конкретного asset, а не доверие к медиа-источникам.

External actions из About принимают не произвольный URL, а только exact allowlist:
`https://donate.stream/donate_6a60559cd9e35` и
`https://github.com/reziarlleh/KinogoATV`. Donation необязателен, не разблокирует функции и
не передаёт в Donate.Stream account, cookies или историю просмотра.
На KIVI обе ссылки открылись во внешнем Yandex TV browser; это подтверждает intent routing,
но не расширяет allowlist и не наделяет browser дополнительным доверием.

Публичный README обязан явно говорить, что KinogoATV — неофициальный неаффилированный
клиент, а репозиторий не хранит/не раздаёт видео. Пока нет явно выбранного
`LICENSE`, публикация source не должна называться open-source лицензией и не предоставляет
прав на копирование/изменение/распространение.

Перед первым commit и каждым release:

```powershell
git status --short
git ls-files --others --exclude-standard
git grep -n -I -E "password|authorization|cookie|BEGIN .*PRIVATE KEY"
```

Совпадение слова `password` в API/документации само по себе не secret; нельзя печатать
найденное значение. При реальном secret commit нужно остановить push, удалить secret из
истории и выполнить rotation.

## Данные пользовательского телевизора

На личном TV запрещены без прямого разрешения:

- `adb uninstall com.kinogo.atv`;
- `adb shell pm clear com.kinogo.atv`;
- полный overwrite/print DataStore;
- managed `connectedDebugAndroidTest`, способный переустановить target package.

Для cleanup использовать точный store API и проверять только нужные поля внутри target
process. После теста удалять только `com.kinogo.atv.test`.
