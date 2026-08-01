# Процесс выпуска APK

Последнее обновление: **1 августа 2026 года**.

## Виды сборок

- `debug` без stable key — только чистый clone, emulator или disposable device.
- `debug` со stable key — устанавливаемая dev-версия, способная обновить текущую установку.
- `release` со stable key — кандидат для распространения.

Текущий C-005 / `0.4.3-dev` собран как stable-signed **debug APK**, а не release variant.
Статус verified baseline определяется только после полного release checklist и фиксируется
в `PROJECT_STATE.md`; один versionName этого не доказывает.

Текущий проверенный dev artifact: `dist/KinogoTV-0.4.3-dev.apk`, versionCode 13,
minSdk 28, targetSdk 37, SHA-256
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

## 3. Автотесты

```powershell
.\gradlew.bat testDebugUnitTest lintDebug `
  --no-daemon --max-workers=1 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

Любая ошибка блокирует выпуск. Lint warnings оцениваются и фиксируются либо документируются.

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

Для C-005 / `0.4.3-dev` полный playback checklist, включая аппаратную проверку natural end
последней серии, остаётся pending; playback rollback baseline по-прежнему B-001,
пока воспроизведение действительно не выдаст соответствующий естественный Media3 callback.
Unit test completion policy, ручной Back и перемотка почти к концу не заменяют этот evidence.

## 7. Локальная упаковка

`dist/` — локальная staging-папка, APK в Git не входят.

Имя:

```text
KinogoTV-<version>.apk
```

Обновить `dist/SHA256SUMS.txt`. Значение должно быть вычислено с точной копии APK, которая
будет опубликована.

## 8. Git и GitHub

1. Commit исходников и документации.
2. Push в private repository.
3. Для аппаратно подтверждённого known-good dev APK создать annotated baseline tag
   `baseline-<version>`.
4. Для законченного распространяемого выпуска отдельно создать release tag `v<version>` и
   GitHub Release.
5. Прикрепить APK и `SHA256SUMS.txt` как Release assets.
6. В release notes перечислить только фактические пользовательские изменения и validation.

Пример baseline tag после полного подтверждения `0.4.3-dev`:

```powershell
git tag -a baseline-0.4.3-dev -m "Kinogo TV 0.4.3-dev known-good baseline"
git push origin baseline-0.4.3-dev
```

Пример GitHub Release, только если этот dev milestone действительно решено публиковать:

```powershell
gh release create v0.4.3-dev `
  dist/KinogoTV-0.4.3-dev.apk `
  dist/SHA256SUMS.txt `
  --title "Kinogo TV 0.4.3-dev" `
  --notes-file <release-notes.md>
```

Не создавать tag/release до аппаратной проверки соответствующего APK.

## Release checklist

- [ ] Version code увеличен.
- [ ] Changelog/state/docs актуальны.
- [ ] Unit tests зелёные.
- [ ] Lint без errors.
- [ ] APK собран stable key.
- [ ] Metadata, alignment, signature и certificate проверены.
- [ ] SHA-256 записан.
- [ ] `adb install -r` сохранил данные.
- [ ] Cold launch и реальный playback проверены.
- [ ] D-pad/media key regressions проверены.
- [ ] Previous/Next и auto-next через границу сезона проверены на TV.
- [ ] Natural end последнего материала вернул в details.
- [ ] Тестовые данные очищены адресно.
- [ ] Source commit и tag указывают на этот APK.
- [ ] APK опубликован Release asset, а не Git blob.
