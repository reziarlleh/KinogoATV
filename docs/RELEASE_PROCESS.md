# Процесс выпуска APK

Последнее обновление: **26 августа 2026 года**.

## Виды сборок

- `debug` без stable key — только чистый clone, emulator или disposable device.
- `debug` со stable key — устанавливаемая dev-версия, способная обновить текущую установку.
- `release` со stable key — кандидат для распространения.

Текущий C-009 / `0.5.3` (code 17, minSdk 28, targetSdk 37) — published validation release для
Details-first History, delete/clear, codec v3 source persistence и видимого startup updater.
Application source `777c8a0528f24db67402536631257d6cdc91f148`, final local canonical
tests/lint, exact post-commit release rebuild и stable-signed APK/hash уже зафиксированы.
Application/docs merge `0473a820eefedea16ce2f393df568c90e5b30bbe`, annotated tag/regular
latest Release, Android CI, signed manifest merge
`ff7f5f8eea9776ef626010fe57993dc1906f5d4a`, Pages и live transports также
зафиксированы. Только hardware History/resume/updater runtime и in-app installer до
Android OS confirmation остаются **PENDING**. C-009 не получает playback baseline tag и
не считается аппаратно проверенным только из-за успешной публикации.

C-008 / `0.5.2` (code 16) остаётся предыдущим exact published validation release:
application commit, local canonical tests/lint, stable-signed APK/hash, public merge,
regular Release, signed manifest, Android CI, Pages publish и live transport checks
зафиксированы в `PROJECT_STATE.md`.

C-007 / `0.5.1` (code 15) остаётся историческим integration rollback point. Его final local
canonical run, exact stable-signed APK/signed manifest и focused KIVI native/navigation
runtime проверены для application source `8b0be72`; эти числа, hash и device evidence нельзя
переносить на C-009.

Исторический локальный canonical result C-007:
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease`
SUCCESS за 4 мин 27 с; 82 suites / 393 tests, 0 failures/errors/skips; lint
0 errors / 22 warnings / 2 hints. Exact APK `dist/KinogoATV-0.5.1-code15.apk`:
38 304 478 bytes, SHA-256
`3166898FDFA882DB9A637ECDA6CDA612A5AF0B5F70D30580FD1449A906EBF875`; package
`com.kinogo.atv`, code 15 / `0.5.1`, minSdk 28, targetSdk 37, LEANBACK launcher/label
`KinogoATV`, zipalign OK, v2 true,
certificate SHA-256
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.

Предыдущий C-006 / `0.5.0` (code 14) имеет отдельно записанный local
75-suite/348-test pass, stable-signed artifact SHA-256
`3650C44B40A7AC066F98B597E0831BB800512CA5695EBD554DDD5620E15ED52B` и X96/KIVI
device smoke. Он остаётся integration rollback point; полный playback rollback — B-001.

Последний проверенный dev artifact остаётся C-005:
`dist/KinogoTV-0.4.3-dev.apk`, versionCode 13, minSdk 28, targetSdk 37, SHA-256
`5A3EAAF4A23663AE73FE987CFDCEE6F311ED4AFD3A48B29833C44C5DAB5F67E9`. Для application
source commit `15efacc` пройдены 68 suites / 309 tests, lint: 0 errors / 7 warnings /
2 hints, assemble, zipalign и v2 verification; digest сертификата линии обновлений
не изменился. `adb install -r` на KIVI сохранил данные и `firstInstallTime`, cold launch
занял 2504 ms. Полный playback checklist для C-005 не повторялся; подтверждённой
точкой отката для playback остаётся B-001.

## Signing identity

Канонический локальный файл:

```text
.signing/kinogo-tv-dev.keystore
```

Допустим внешний путь через `KINOGO_SIGNING_STORE_FILE`.

Ожидаемый SHA-256 сертификата текущей линии обновлений:

```text
154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9
```

Перед выпуском сверить digest. Новый случайный key создаст несовместимое приложение.

## 1. Подготовка версии

В `app/build.gradle.kts` увеличить:

```kotlin
versionCode = <строго больше предыдущего>
versionName = "<новая версия>"
```

Обновить:

- `CHANGELOG.md`;
- `PROJECT_STATE.md`;
- пользовательский `README.md`, если изменилась функция;
- профильные документы;
- `ROADMAP.md`.

Для встроенного updater version/tag/asset обязаны совпасть буквально:

```text
tag:        v<versionName>
asset:      KinogoATV-<versionName>-code<versionCode>.apk
release:    не draft и не prerelease
digest:     sha256:<64 hex> в metadata GitHub asset
```

Нельзя публиковать тот же versionCode под другим APK: updater принимает только строго
растущую версию и ожидаемую signing identity.

Для primary signed-manifest channel тот же exact artifact описывается payload с
literal fields:

```text
versionName, versionCode, assetName, assetSizeBytes, sha256,
issuedAtEpochSeconds, expiresAtEpochSeconds, downloadUrls
```

Envelope schema 1 хранит exact UTF-8 payload в base64 и подпись `SHA256withRSA`
или `SHA256withECDSA` от той же identity, что подписала APK. Lifetime — не более
90 дней; download URLs — от одного до четырёх safe HTTPS-адресов. Pages и jsDelivr
дают отдельные от `github.com` UI/API transport paths, но оба канала берут artifact
из GitHub repository/release и не являются operator-owned non-GitHub storage.

## 2. Проверка рабочего дерева

```powershell
git status -sb
git diff --check
git ls-files --others --exclude-standard
```

Убедиться, что нет:

- APK/build outputs;
- signing key или secret properties;
- user photos/attachments;
- live cookies/tokens/DataStore;
- стороннего decompiled research.

Для публичного repository дополнительно проверить README disclaimer: проект
неофициальный/неаффилированный и не хранит видео. Не добавлять open-source license без
явного решения владельца; отсутствие `LICENSE` означает, что права автоматически не
предоставлены. QR поддержки допустим только как неизменённый owner-supplied asset с
зафиксированным hash.

## 3. Автотесты

```powershell
.\gradlew.bat testDebugUnitTest lintDebug `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Любая ошибка блокирует выпуск. Lint warnings оцениваются и фиксируются либо документируются.

Для C-009 полный canonical набор
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease`
завершён **SUCCESS за 7 мин 12 с**: 89 suites / 455 tests, 0 failures, 0 errors,
0 skipped; lint 0 errors / 22 warnings / 2 hints. Результат привязан к application source
`777c8a0528f24db67402536631257d6cdc91f148`. Exact post-commit
`assembleRelease --rerun-tasks` — **SUCCESS за 4 мин 04 с**, 50 tasks.

Предыдущий local integration pass C-006: 75 suites / 348 unit tests, 0 failures, 0 errors,
0 skipped; lint 0 errors / 19 warnings / 2 hints; debug, AndroidTest APK и release assembly
успешны и относятся к application commit `6567088`. Повторить команду, если application
source изменится.

Для C-007 final local canonical run: SUCCESS за 4 мин 27 с, 82 suites / 393 tests,
0 failures, 0 errors, 0 skipped; lint 0 errors / 22 warnings / 2 hints; debug, AndroidTest
и release assemblies успешны. Результат привязан к application source `8b0be72`; remote
CI ещё **PENDING**. При любом следующем production change полную команду нужно
повторить.

Для C-008 полный canonical набор обязан включать
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease`.
Для application source
`4cfa7ac8ebd48b70c7b172e54a0716fec09669a1` этот набор завершён **SUCCESS за 5 мин 20 с**:
87 suites / 441 tests, 0 failures, 0 errors, 0 skipped; lint 0 errors / 22 warnings / 2 hints.
Post-commit `assembleRelease --rerun-tasks` — **SUCCESS за 5 мин 29 с**. В green run входят
`PlaybackSourceRefreshTest`, `KinogoAppRootResumeTest`, `PlaybackProgressCodecTest`,
`PlaybackQualityPolicyTest`, `PlaybackBufferPolicyTest`, `PlaybackMediaPlanTest`,
`PlaybackPlaylistNavigationTest`, `PlaybackQualitySwitchGuardTest`,
`PlaybackPreloadFailurePolicyTest`, `PlaybackSourceSelectionModelTest` и preferences tests.
Remote Android CI [run 32598900494](https://github.com/reziarlleh/KinogoATV/actions/runs/32598900494)
на manifest/main merge `367bcf2` завершён SUCCESS (`2026-08-22T21:12:08Z`–`21:13:28Z`).

`.github/workflows/android.yml` повторяет canonical unit/lint/assembleDebug на push в
`main` и pull request. Official Actions закреплены полными commit SHA актуальных Node 24
релизов. Перед release сохранить URL/result run для exact source commit. Workflow не содержит
stable signing key и не заменяет локальную проверку release APK.

## 4. Сборка

Dev candidate:

```powershell
.\gradlew.bat assembleDebug `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Release candidate:

```powershell
.\gradlew.bat assembleRelease `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Release task без stable key должна завершиться до выдачи пригодного артефакта.

## 5. Проверка APK

Для выбранного APK:

```powershell
apkanalyzer manifest application-id <apk>
apkanalyzer manifest version-code <apk>
apkanalyzer manifest version-name <apk>
apkanalyzer manifest min-sdk <apk>
apkanalyzer manifest target-sdk <apk>

zipalign -c -v 4 <apk>
apksigner verify --verbose --print-certs <apk>

Get-FileHash <apk> -Algorithm SHA256
```

Проверить:

- `com.kinogo.atv`;
- versionCode/versionName;
- minSdk 28;
- ожидаемый targetSdk;
- alignment successful;
- signature v2 true;
- certificate digest совпал;
- SHA сохранён.
- имя файла точно совпадает с updater contract;
- APK package/version/code/signer совпадают с metadata Release и установленным приложением.

Для C-009 локально проверен exact `dist/KinogoATV-0.5.3-code17.apk`:
38 386 398 bytes, SHA-256
`3C88DF356A9815865DB02F7821DA53BE3C6E25F03FE493516FCCAF0F48F0C17A`, package
`com.kinogo.atv`, code 17 / `0.5.3`, minSdk 28, targetSdk 37, zipalign PASS, v2 true,
ровно один signer, certificate SHA-256
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`, embedded revision
`777c8a0528f24db67402536631257d6cdc91f148`. Application/docs PR #5 влит merge
`0473a820eefedea16ce2f393df568c90e5b30bbe`; PR CI `32920452170` и post-merge main CI
`32920746857` завершились SUCCESS. Annotated `v0.5.3` указывает на этот merge;
regular latest Release и GitHub asset digest совпадают с exact APK. TV runtime ещё
**PENDING**.

Исторически, для C-008 локально проверен exact
`dist/KinogoATV-0.5.2-code16.apk`: 38 353 630 bytes,
SHA-256 `FC70D02A2BC7A3F9E5E2F04A1A7B139037AC215C85166E72E9842D0DB3CB4B38`, package
`com.kinogo.atv`, code 16 / `0.5.2`, minSdk 28, target/compile SDK 37, LEANBACK
launcher/banner, zipalign OK, v2 true, embedded revision `4cfa7ac`, certificate SHA-256
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`. Он связан с
application source `4cfa7ac8ebd48b70c7b172e54a0716fec09669a1`; значения C-007 в эту строку не
копировались.

Для C-007 локально проверен exact `dist/KinogoATV-0.5.1-code15.apk`: 38 304 478 bytes,
SHA-256 `3166898FDFA882DB9A637ECDA6CDA612A5AF0B5F70D30580FD1449A906EBF875`, package
`com.kinogo.atv`, code 15 / `0.5.1`, minSdk 28, targetSdk 37, LEANBACK launcher/label
`KinogoATV`, zipalign OK, v2 true,
certificate SHA-256
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`. Final commit,
published asset и installed-TV identity match остаются **PENDING**.

## 6. Обновление на TV

Этот раздел не разрешает агенту подключаться к устройству. Сначала выполняются review,
автоматические тесты и artifact verification. Любое ADB-подключение к реальному TV,
`install -r`, instrumentation или hardware smoke допустимы только после предварительного
явного разрешения владельца на конкретный узкий сценарий, результат которого нельзя
надёжно установить иначе. Ранее выданная ADB-авторизация устройства не заменяет такое
разрешение.

После полученного разрешения не удалять старую версию:

```powershell
adb install -r <apk>
```

Если Android сообщает incompatible signature, остановиться. Uninstall сотрёт account/history
и не является допустимым «решением» без разрешения пользователя.

После установки:

1. cold launch;
2. проверить foreground/focus и crash log;
3. открыть каталог, историю и settings;
4. убедиться, что account/library/history сохранены;
5. выполнить реальное native playback;
6. проверить D-pad HUD и checkpoint;
7. проверить Previous/Next через границу сезона на реальном многосезонном материале;
8. проверить auto-next в первую совместимую серию следующего сезона;
9. дождаться естественного Media3-окончания фильма либо последней серии и подтвердить
   возврат в details;
10. удалить только созданные тестовые записи точечным store API.

Для C-009 владелец намеренно оставляет TV/ADB-проверку за собой. Агент не устанавливает
`0.5.3` на TV и не выполняет smoke без отдельного нового разрешения. В release evidence это
фиксируется как `hardware PENDING (owner manual validation)`, а не как failure и не как
подтверждённое поведение.

Исторически та же граница применялась к C-008: его публикация не создавала hardware
baseline, а in-app updater/runtime оставались на ручную проверку владельца.

Для C-006 / `0.5.0` расширенный focus/resume/source-refresh/update/registration checklist и
полный playback pass закрыты лишь частично. Debug runtime уже подтвердил cold rail
focus, Settings Switch/dropdown, About/QR/exact browser actions и путь Home → Details →
selector → ~14 с native playback → Back → focused `Продолжить с 0:14`. D-pad
instrumentation подтвердила registration rules default-decline и explicit accept. Это не
заменяет live account submit, реальный expired-source recovery, natural cross-season end,
или newer-version installer. Playback rollback baseline по-прежнему B-001.

Final Release install evidence уже закрыто на X96Max Plus Ultra Android TV 14:

- `adb install -r`, `firstInstallTime` `2026-08-14 08:34:38` сохранён;
- installed base APK hash/size точно совпали;
- cold launch `Status: ok`, `TotalTime: 1023 ms`, initial Home rail focus;
- каталог/постеры загрузились, FATAL/ANR нет;
- final rules instrumentation `OK (1)`, scroll boundary → `Не принимаю`, test package
  удалён.

Focused C-007 smoke выполнен на KIVI `192.168.1.112`, Android TV 14:

- stable-signed candidate установлен через `adb install -r`; `firstInstallTime`
  `2026-07-26 16:42:18` сохранился;
- current exact-host Cinemar runtime document дал native selector с озвучками, сезонами
  1–4 и сериями; resume 10:48, Media3 S2E5 продвинулся 11:01 → 11:39;
- hidden-HUD `OK` показал управление без паузы; Back вернул Player → Details → History;
- вторая History card и второй Search result восстановили exact focus; Search query/results
  и recent-query row сохранились;
- D-pad запустил `Оригинальный web-плеер`, fullscreen WebView открылся и Back чисто вернул
  Details → History;
- случайно изменённый «Spider-Man» восстановлен адресно; broad clear/uninstall не выполнялись.

Для C-007 остаются release/runtime пункты:

1. проверить About как первую Settings card и action логотипа rail на exact candidate;
2. доказать Web fallback reopen с тем же playlist item/position; launch/Back smoke этого не
   доказывает, а provider state не доступен accessibility/safe logs;
3. проверить signed-manifest metadata/download при недоступном GitHub API и передачу в
   Android Package Installer;
4. закрыть expanded player checklist: source refresh, cross-season и natural end.

Отдельно проверить GitHub fallback updater на том же candidate:

1. stable GitHub Release check находит exact asset;
2. download проходит size/SHA/package/version/signer verification;
3. при отсутствии permission открывается системный экран unknown sources;
4. затем открывается Android Package Installer с явным подтверждением;
5. приложение не подтверждает установку само и не удаляет прежний package;
6. после согласованной установки данные и signing lineage сохранены.

## 7. Локальная упаковка

`dist/` — локальная staging-папка, APK в Git не входят.

Имя локального/публикуемого Release asset:

```text
KinogoATV-<version>-code<versionCode>.apk
```

Обновить `dist/SHA256SUMS.txt`. Значение должно быть вычислено с точной копии APK, которая
будет опубликована.

## 8. Подписанный update manifest

Сначала опубликовать exact stable GitHub Release asset и дождаться его metadata
digest. Затем из той же локальной stable-signed копии создать envelope:

```powershell
$expires = [DateTimeOffset]::UtcNow.AddDays(30)
.\scripts\New-SignedUpdateManifest.ps1 `
  -ApkPath .\dist\KinogoATV-0.5.3-code17.apk `
  -VersionName 0.5.3 -VersionCode 17 -ExpiresAt $expires `
  -DownloadUrl @(
    'https://reziarlleh.github.io/KinogoATV/update/KinogoATV-0.5.3-code17.apk',
    'https://ghfast.top/https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.3/KinogoATV-0.5.3-code17.apk',
    'https://ghproxy.net/https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.3/KinogoATV-0.5.3-code17.apk',
    'https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.3/KinogoATV-0.5.3-code17.apk'
  )
```

Команда выше воспроизводит опубликованный C-009 contract. Final code 17
`update/manifest.json`: 1 273 bytes, file SHA-256
`860D90C22D9F404A38E783BD313A9E9A0FDEFC5BC870F933A819D35145489977`, issued
`2026-08-26T02:04:54Z`, expires `2026-09-25T02:04:54Z`; payload содержит exact APK
size/SHA-256 и четыре URLs Pages/ghfast/ghproxy/direct GitHub. Manifest source
`7faebbba8d305a0c339f6966e7759ec7c7f96b90`, PR #6, main merge
`ff7f5f8eea9776ef626010fe57993dc1906f5d4a`; PR CI run `32921520976`, main Android run
`32921627748` за 1 мин 04 с и Pages run `32921627746` за 45 с завершились SUCCESS.

Исторический final code 16
`update/manifest.json`: 1 273 bytes, file SHA-256
`BCB6699708CC2C6FF4A71F8379032F709742AC714440622F179130D5AFA80E94`, issued
`2026-08-22T21:02:03Z`, expires `2026-09-21T21:02:03Z`; payload содержит exact APK
size/SHA-256 и четыре URLs Pages/ghfast/ghproxy/direct GitHub. Manifest/main merge —
`367bcf288dd5b3ad729af94d9b21308e5c96354c`. Старый code 15 manifest был намеренно удалён
перед первым merge C-008 и заменён только после появления exact v0.5.2 Release asset.

Исторический C-007 local release manifest candidate `update/manifest.json`: 1 273 bytes,
file SHA-256
`3C167F87208077E6EC4717F202F968AD555B800C76043CFCF69B941627323070`, payload code 15 /
`0.5.1`, `issuedAtEpochSeconds=1787294465`, `expiresAtEpochSeconds=1794984054`
(18 ноября 2026 года, 06:40:54 UTC), четыре download URLs и exact APK SHA-256
`3166898FDFA882DB9A637ECDA6CDA612A5AF0B5F70D30580FD1449A906EBF875` при size
38 304 478 bytes. Локальные envelope/content checks завершены; файл ещё не является live
release metadata. Final commit, Release asset и Pages/jsDelivr deployment — **PENDING**.
Если APK или production source изменятся, manifest нужно пересоздать и заново проверить.

Скрипт до подписи обязан проверить package `com.kinogo.atv`, version/name/code, minSdk 28,
zipalign, v2 signature, certificate, size и SHA-256. Keystore/password не передаются в
аргументах командной строки и не коммитятся. Перед работой прогнать signer self-test, а generated
file дополнительно проверить Python verifier, который также запускает workflow:

```powershell
.\scripts\New-SignedUpdateManifest.ps1 -SelfTest
py -3 .\scripts\verify_update_manifest.py self-test
```

Два default metadata transports, начиная с code 15:

- `https://reziarlleh.github.io/KinogoATV/update/manifest.json`;
- `https://cdn.jsdelivr.net/gh/reziarlleh/KinogoATV@main/update/manifest.json`.

jsDelivr — отдельный CDN-транспорт для малого подписанного manifest, но он читает
файл из GitHub repository и не является operator-owned storage. `ghfast.top` и
`ghproxy.net` — недоверенные best-effort download transports; read-only check 21 августа
2026 года показал их доступность, но не даёт SLA или trust. Любые их байты принимаются
только после совпадения с подписанными size/SHA-256 и повторных
package/version/signer checks. Operator-owned non-GitHub manifest+APK hosting остаётся
**PENDING**.

Для C-009 live exact bytes подтверждены у Pages manifest+APK и jsDelivr manifest
после адресной очистки его кэша. APK через ghfast, ghproxy и direct GitHub совпал с
38 386 398 bytes и SHA-256
`3C88DF356A9815865DB02F7821DA53BE3C6E25F03FE493516FCCAF0F48F0C17A`. Это закрывает
publication/transport evidence, но не hardware/in-app installer runtime и не
инфраструктурную независимость от GitHub.

Для исторического C-008 live exact bytes подтверждены у Pages manifest+APK и jsDelivr
manifest. APK через
ghfast, ghproxy и direct GitHub совпал с 38 353 630 bytes и SHA-256
`FC70D02A2BC7A3F9E5E2F04A1A7B139037AC215C85166E72E9842D0DB3CB4B38`. Это проверенная
transport diversity, но не инфраструктурная независимость от GitHub: Pages/jsDelivr/proxy
маршруты в конечном счёте зависят от repository/Release asset.

`update/manifest.json` коммитится только после review. Workflow
`.github/workflows/pages-update.yml` скачивает exact Release asset, повторяет
manifest/APK/certificate/v2/minSdk/zipalign checks и публикует минимальные
`update/manifest.json` + APK через Pages. Ни workflow source, ни HTTP 200 не закрывают
release-check: нужны successful deployment URL, проверка обоих metadata endpoints, каждого
download fallback и приложения до Android OS confirmation. Перевыпустить manifest до expiry.

## 9. Git и GitHub

1. Commit исходников и документации.
2. Push в repository; перед публичностью повторно проверить hygiene/disclaimer/no-license
   status.
3. Только для аппаратно подтверждённого known-good APK создать annotated baseline tag
   `baseline-<version>`; validation release без hardware evidence такой tag не получает.
4. Для законченного распространяемого выпуска отдельно создать release tag `v<version>` и
   GitHub Release.
5. Прикрепить APK и `SHA256SUMS.txt` как Release assets.
6. В release notes перечислить только фактические пользовательские изменения и validation.

Пример baseline tag только после полного подтверждения версии:

```powershell
git tag -a baseline-<version> -m "KinogoATV <version> known-good baseline"
git push origin baseline-<version>
```

Обычный распространяемый release желательно создавать после аппаратного подтверждения.
Допустимое исключение — validation release для ручной проверки updater владельцем: canonical
tests/lint, exact stable-signed artifact, manifest и CI всё равно обязательны, release notes
явно называют hardware validation pending, baseline tag не создаётся и никакое TV-поведение
не объявляется подтверждённым. Updater не принимает GitHub `prerelease`, поэтому такой
validation release может быть технически regular Release, но его evidence-классификация
остаётся validation, а не baseline.

Пример C-009 validation GitHub Release после вычисления exact digest:

```powershell
gh release create v0.5.3 `
  dist/KinogoATV-0.5.3-code17.apk `
  dist/SHA256SUMS.txt `
  --title "KinogoATV 0.5.3" `
  --notes-file <release-notes.md>
```

После создания проверить, что GitHub API отдаёт asset `digest` с тем же SHA-256. Не
создавать обычный known-good release до аппаратной проверки соответствующего APK;
единственное исключение описано выше для явно обозначенной validation-публикации.
Draft/prerelease не обслуживаются updater как stable update.

Фактический C-009: application/docs PR #5 влит merge
`0473a820eefedea16ce2f393df568c90e5b30bbe`; PR CI run
[32920452170](https://github.com/reziarlleh/KinogoATV/actions/runs/32920452170) и post-merge main
[32920746857](https://github.com/reziarlleh/KinogoATV/actions/runs/32920746857) за 4 мин 22 с — SUCCESS.
Annotated tag `v0.5.3` указывает на `0473a820`; regular latest
[Release 0.5.3](https://github.com/reziarlleh/KinogoATV/releases/tag/v0.5.3)
опубликован `2026-08-26T01:59:56Z` с `draft=false`, `prerelease=false`. Exact
[APK asset](https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.3/KinogoATV-0.5.3-code17.apk)
имеет 38 386 398 bytes и GitHub digest
`sha256:3c88df356a9815865db02f7821da53be3c6e25f03fe493516fccaf0f48f0c17a`.

Исторический C-008 Release: public repository
[reziarlleh/KinogoATV](https://github.com/reziarlleh/KinogoATV), application/docs merge
`08c90c9`, tag `v0.5.2`,
[regular Release](https://github.com/reziarlleh/KinogoATV/releases/tag/v0.5.2) с
`draft=false`, `prerelease=false`. Exact
[APK asset](https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.2/KinogoATV-0.5.2-code16.apk)
имеет GitHub digest
`sha256:fc70d02a2bc7a3f9e5e2f04a1a7b139037ac215c85166e72e9842d0db3cb4b38`.
Pages [run 32598900503](https://github.com/reziarlleh/KinogoATV/actions/runs/32598900503)
на `367bcf2` завершён SUCCESS (`2026-08-22T21:12:09Z`–`21:12:57Z`).

## Release checklist C-009

- [x] Version code увеличен до 17, version name — `0.5.3`.
- [x] Application source commit записан:
      `777c8a0528f24db67402536631257d6cdc91f148`.
- [x] Canonical local verification зелёная: 89 suites / 455 tests, 0
      failures/errors/skips; lint 0 errors / 22 warnings / 2 hints.
- [x] Exact post-commit `assembleRelease --rerun-tasks` зелёный: 4 мин 04 с,
      50 tasks.
- [x] Exact stable-signed APK упакован как `KinogoATV-0.5.3-code17.apk`.
- [x] Package/version/minSdk/targetSdk, zipalign, v2, one-signer certificate, embedded
      revision, size и SHA-256 проверены локально.
- [x] Application/docs PR #5 влит merge `0473a820`; PR CI `32920452170` и main
      CI `32920746857` зелёные.
- [x] Annotated tag `v0.5.3`, regular latest GitHub Release и exact asset `sha256:` digest
      совпадают.
- [x] Signed manifest создан из того же APK, опубликован merge `ff7f5f8`;
      PR/main Android CI зелёные.
- [x] Pages workflow `32921627746`, Pages/jsDelivr metadata и все заявленные APK
      transports проверены на exact bytes.
- [x] Hardware boundary явно записана: TV/ADB runtime **PENDING**, baseline tag не
      создаётся.
- [ ] После ручной приёмки владельцем startup update prompt, Details-first History,
      delete/clear, focus restore и source-aware resume проверены на TV.

### Historical completed checklist C-008

- [x] Version code увеличен до 16, version name — `0.5.2`.
- [x] Application source commit C-008 записан: `4cfa7ac8ebd48b70c7b172e54a0716fec09669a1`.
- [x] GitHub Actions run `32598900494` на manifest/main merge `367bcf2` зелёный.
- [x] Changelog/state/docs отражают опубликованный validation release.
- [x] Unit tests зелёные: 87 suites / 441 tests без failures/errors/skips.
- [x] Lint без errors: 22 warnings / 2 hints оценены.
- [x] APK собран stable key.
- [x] Metadata, alignment, signature и certificate проверены.
- [x] SHA-256 записан.
- [x] Release tag, versionName/code, exact asset name и GitHub `sha256:` digest совпадают.
- [x] Signed manifest создан из того же APK, его installed-signer signature, exact
      payload/expiry и download URLs проверены.
- [x] Pages workflow `32598900503` успешен; Pages и jsDelivr metadata URLs проверены из целевой
      сети, а Pages не объявлен fully independent от всей GitHub infrastructure.
- [x] Каждый заявленный APK transport (Pages, ghfast, ghproxy, direct GitHub) проверен на
      exact bytes; каждый остаётся недоверенным transport, а trust основан на signed
      size/SHA и final signer/package checks.
- [x] Hardware validation либо выполнена после отдельного явного разрешения, либо явно
      оставлена `PENDING`; для C-008 владелец проверяет updater вручную.
- [ ] Если разрешено: `adb install -r` сохранил данные.
- [ ] Если разрешено: cold launch и реальный playback проверены.
- [ ] Если разрешено: D-pad/media key regressions проверены.
- [ ] Если разрешено: Previous/Next и auto-next через границу сезона проверены на TV.
- [ ] Если разрешено: natural end последнего материала вернул в details.
- [ ] Если разрешено: cold initial rail focus и Settings dropdown focus-return проверены.
- [ ] Если разрешено: newest-unfinished resume и bounded source refresh проверены на TV.
- [ ] Registration rules default-decline/explicit-accept, remember-only input,
      generation guard, bounded CAPTCHA и remote mirror quarantine проверены без обхода
      protection.
- [ ] Source recovery сохранил position только для exact same unit; remap другой серии
      вернул selector без скрытого autoplay.
- [ ] Updater дошёл до обязательного Android OS confirmation; silent install отсутствует.
- [ ] About/QR/exact external links проверены; public disclaimer и repository hygiene актуальны.
- [ ] Тестовые данные очищены адресно.
- [ ] Source commit и tag указывают на этот APK.
- [ ] APK опубликован Release asset, а не Git blob.
