# Процесс выпуска APK

Последнее обновление: **15 августа 2026 года**.

## Виды сборок

- `debug` без stable key — только чистый clone, emulator или disposable device.
- `debug` со stable key — устанавливаемая dev-версия, способная обновить текущую установку.
- `release` со stable key — кандидат для распространения.

Текущий C-006 / `0.5.0` (code 14, minSdk 28, targetSdk 37) остаётся integration candidate.
Local `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease`
pass завершён: 75 suites / 348 unit tests без failures/errors/skips, lint 0 errors /
19 warnings / 2 hints. Stable-signed `dist/KinogoATV-0.5.0-code14.apk` проверен и установлен
через `adb install -r` на X96Max Plus Ultra с сохранением firstInstallTime; cold launch
успешен. Final source commit, GitHub Actions run, public GitHub Release и updater-live
**PENDING**. Короткий smoke не превращает кандидата в полный playback baseline.

Artifact: 38 140 638 bytes; SHA-256
`3650C44B40A7AC066F98B597E0831BB800512CA5695EBD554DDD5620E15ED52B`; zipalign OK, v2 true;
certificate SHA-256
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.

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

Последний local integration pass C-006: 75 suites / 348 unit tests, 0 failures, 0 errors,
0 skipped; lint 0 errors / 19 warnings / 2 hints; debug, AndroidTest APK и release assembly
успешны. Повторить команду после final commit, если source изменится.

`.github/workflows/android.yml` повторяет canonical unit/lint/assembleDebug на push в
`main` и pull request. Перед release сохранить URL/result run для exact source commit.
Workflow не содержит stable signing key и не заменяет локальную проверку release APK.

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

## 6. Обновление на TV

Не удалять старую версию:

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

Отдельно проверить встроенный updater на том же candidate:

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

## 8. Git и GitHub

1. Commit исходников и документации.
2. Push в repository; перед публичностью повторно проверить hygiene/disclaimer/no-license
   status.
3. Для аппаратно подтверждённого known-good dev APK создать annotated baseline tag
   `baseline-<version>`.
4. Для законченного распространяемого выпуска отдельно создать release tag `v<version>` и
   GitHub Release.
5. Прикрепить APK и `SHA256SUMS.txt` как Release assets.
6. В release notes перечислить только фактические пользовательские изменения и validation.

Пример baseline tag после полного подтверждения `0.5.0`:

```powershell
git tag -a baseline-0.5.0 -m "KinogoATV 0.5.0 known-good baseline"
git push origin baseline-0.5.0
```

Пример stable GitHub Release после аппаратного подтверждения и вычисления exact digest:

```powershell
gh release create v0.5.0 `
  dist/KinogoATV-0.5.0-code14.apk `
  dist/SHA256SUMS.txt `
  --title "KinogoATV 0.5.0" `
  --notes-file <release-notes.md>
```

После создания проверить, что GitHub API отдаёт asset `digest` с тем же SHA-256. Не
создавать tag/release до аппаратной проверки соответствующего APK; draft/prerelease не
обслуживаются updater как stable update.

## Release checklist

- [ ] Version code увеличен.
- [ ] Final source commit записан; GitHub Actions run на нём зелёный.
- [ ] Changelog/state/docs актуальны.
- [ ] Unit tests зелёные.
- [ ] Lint без errors.
- [ ] APK собран stable key.
- [ ] Metadata, alignment, signature и certificate проверены.
- [ ] SHA-256 записан.
- [ ] Release tag, versionName/code, exact asset name и GitHub `sha256:` digest совпадают.
- [ ] `adb install -r` сохранил данные.
- [ ] Cold launch и реальный playback проверены.
- [ ] D-pad/media key regressions проверены.
- [ ] Previous/Next и auto-next через границу сезона проверены на TV.
- [ ] Natural end последнего материала вернул в details.
- [ ] Cold initial rail focus и Settings dropdown focus-return проверены на TV.
- [ ] Newest-unfinished resume и bounded source refresh проверены на TV.
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
