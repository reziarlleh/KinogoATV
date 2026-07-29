# Безопасность и границы доверия

Последнее обновление: **29 июля 2026 года**.

## Модель угроз

Приложение получает изменчивый HTML и media descriptors от зеркал и сторонних player
providers. Любой origin, redirect, iframe, media URL и subtitle URL считается недоверенным,
пока не пройдёт соответствующую проверку.

Основные риски:

- lookalike mirror или изменившийся domain;
- SSRF через DNS, redirect или media manifest;
- утечка credentials/cookies на другой origin;
- утечка transient media token через log, crash report или persisted state;
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

APK публикуются как GitHub Release assets с SHA-256, а не как Git blobs.

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
