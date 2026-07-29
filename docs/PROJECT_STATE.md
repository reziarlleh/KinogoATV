# Текущее состояние проекта

Последнее обновление: **29 июля 2026 года**.

## Краткий итог

Версия `0.4.0-dev` собрана, установлена поверх пользовательской установки на KIVI 4K
Android TV 14 и прошла автоматическую проверку и аппаратный UI/D-pad smoke. Данные
приложения, история и сохранённая позиция просмотра не были очищены и сохранились после
`adb install -r`.

Большая переработка интерфейса завершена. Новый completion flow плеера имеет unit guards,
однако переход через границу сезона и естественное окончание материала ещё не были
полностью проиграны на реальном TV. Поэтому B-001 остаётся подтверждённой playback-точкой
отката, а `0.4.0-dev` фиксируется как текущий validation candidate, не как новый полностью
аппаратно подтверждённый baseline.

## Текущий validation candidate

| Поле | Значение |
| --- | --- |
| Candidate | **C-002 / 0.4.0-dev** |
| Application source commit | `5a22f2a` |
| Application ID | `com.kinogo.atv` |
| Version code | `10` |
| Version name | `0.4.0-dev` |
| Минимальная версия | Android TV 9 / API 28 |
| Compile / target SDK | 37 / 37 |
| UI | Kotlin + Jetpack Compose, landscape TV-only |
| Плеер | AndroidX Media3 / ExoPlayer |
| Подпись APK | стабильный локальный ключ, APK Signature Scheme v2 |
| Baseline tag | не создавался: полный player runtime pass ещё pending |

Проверенный локальный APK: `dist/KinogoTV-0.4.0-dev.apk`. APK не коммитится в Git; в
`dist/SHA256SUMS.txt` хранится его контрольная сумма.

SHA-256:
`188A2CF14226C1541B2E0D5822F9CD445E09EF1E2FCE1B41483C5CC2E093EFFE`.

Certificate SHA-256:
`154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.

Документальный commit после `5a22f2a` не меняет application source или APK.

## Known-good baseline и откат

Текущий полностью подтверждённый playback baseline: **B-001 / 0.3.3-dev**.

- Runtime evidence: 28 июля 2026 года.
- Source baseline tag: `baseline-0.3.3-dev`.
- Локальный rollback artifact: `dist/KinogoTV-0.3.3-dev.apk`.
- Artifact SHA-256:
  `931253976140D5A76276AB4F30E7A709600CD61EABFE1FD8A36C29F38B454A77`.
- Signature certificate SHA-256:
  `154ba15141982ada63499114ea38da6d16df9e5c9c47aba1fe6c3b4f156923c9`.

Rollback на APK допустим только поверх совместимой подписи и с `versionCode`, разрешённым
Android. Для отката source использовать tag/commit, пересобрать с тем же signing key и
назначить новый увеличенный versionCode; обычный downgrade APK Android может запретить.

История поломок, candidate evidence и guards:
[`REGRESSION_LOG.md`](REGRESSION_LOG.md).

## Состояние подсистем

| Подсистема | Статус | Подтверждённое поведение |
| --- | --- | --- |
| Запуск | Работает | Нативный первый кадр, Compose bootstrap, отчёт раннего сбоя, cold launch |
| Android TV launcher | Работает | LEANBACK launcher, фирменные TV banner и ATV icon |
| Навигация | Работает | Постоянный компактный rail, D-pad focus, подтверждение выхода |
| Главная | Работает | История одной строкой, новинки многострочной сеткой, hero отсутствует |
| Каталог | Работает | Четыре раздела, шесть колонок, локальная сортировка, одиночные GET-фильтры, ранний preload |
| Поиск | Работает | Debounce 750 ms, immediate submit, закрытие клавиатуры, voice action |
| Карточка | Работает | Крупный постер, полный текст, основные/status/favorite actions |
| Постеры | Работает | HTTPS-only загрузка, memory/disk cache, безопасная заглушка |
| Зеркала | Работает | Built-in и ручные кандидаты, fingerprint/redirect, TTL и активный origin |
| Аккаунт | Работает | HTML-login, зашифрованные credentials, восстановление cookie-сессии |
| Закладки | Работает | Статусы сайта, независимое избранное, sync и локальный outbox |
| История | Работает | Snapshot, постер, сезон, серия, позиция, resume и legacy ID recovery |
| Выбор источника | Работает | Source/voice/season/episode/quality sparse-матрица видна до запуска |
| Нативный плеер | Работает; новый end flow pending TV | Media3, HUD, selectors, D-pad/media keys; cross-season/completion покрыты unit tests |
| Web fallback | Работает | Явный provider-only WebView с origin boundary, TV HUD и виртуальным курсором |
| Настройки | Работает | Компактные строки; значение меняется по OK, Left/Right навигируют |

## Последняя проверка C-002

Проверка выполнена 29 июля 2026 года для application source commit `5a22f2a`:

- 67 test suites, **281 unit test**, 0 failures, 0 errors, 0 skipped;
- Android Lint: 0 errors, 6 warnings и 2 hints;
- `assembleDebug`: успешно;
- ZIP alignment: успешно;
- v2 signature: успешно, certificate digest совпал;
- metadata: `com.kinogo.atv`, version code 10, `0.4.0-dev`, minSdk 28, targetSdk 37,
  LEANBACK launcher/banner присутствуют;
- APK SHA-256:
  `188A2CF14226C1541B2E0D5822F9CD445E09EF1E2FCE1B41483C5CC2E093EFFE`.

Аппаратный smoke на KIVI 4K Android TV 14:

- `adb install -r` завершён успешно; `firstInstallTime` остался
  `2026-07-26 16:42:18`, версия стала code 10 / `0.4.0-dev`;
- финальный cold launch: `Status: ok`, `LaunchState: COLD`, `TotalTime: 2487 ms`;
- `MainActivity` осталась `topResumedActivity`; в post-launch log нет fatal exception или
  ANR приложения;
- сохранённая история показала реальный постер, название и checkpoint серии;
- проверены steel/cyan shell, фиксированный rail, каталог, диалог фильтра, dropdown
  сортировки, immediate search submit, Settings OK-only/Left-to-rail, Details и source
  selection;
- source selection на реальном сериале показал Cinemar, три озвучки, сезон и 12 серий; выбор
  серии доступен до запуска;
- после финальной установки воспроизведение намеренно не запускалось, чтобы не изменять
  пользовательский checkpoint.

Не переносить старое runtime evidence автоматически. Для C-002 ещё не проверены полным
реальным сценарием:

- Previous/Next и auto-next через границу сезона;
- `PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM` при отключённом auto-next;
- естественный финальный `STATE_ENDED` фильма/последней серии и возврат в details;
- фактический buffering overlay и white timeline marker в соответствующих runtime-состояниях.

Базовый старт, seek, HUD и реальное native playback были подтверждены пользователем и
устройством на B-001, но это не является аппаратным доказательством нового completion flow.

## Текущие технические границы

- Каталог зависит от server-rendered HTML DLE. Закрытый JSON-шлюз официального приложения
  используется только как необязательный playback-time recovery path.
- Серверные фильтры представлены одиночными подтверждёнными GET-маршрутами новинок, года,
  страны и allowlisted жанра. Комбинации фильтров и server-wide sorting не имитируются;
  сортировка загруженных карточек локальна.
- Нативные адаптеры разбирают browser-visible конфигурации Cinemar и Collaps. Неизвестный,
  DRM- или JavaScript-only источник нельзя маскировать под Media3.
- Web fallback остаётся явным выбором пользователя; приложение не переключается в него
  молча.
- Точная позиция просмотра хранится локально на TV. С сайтом синхронизируются аккаунтные
  закладки и статусы, но не Media3 checkpoint.
- Runtime проверяет встроенные и ручные зеркала и может принять безопасную конечную цель
  redirect. Интернет-wide поиск и подписанный удалённый manifest пока не подключены.
- CI ещё не настроен. Clean-clone unit/lint/debug требует внешний Android SDK и JDK 17;
  release и обновление пользовательской установки дополнительно требуют стабильный signing
  key, который намеренно отсутствует в Git.

## Активный фокус

Следующий шаг — продолжительное пользовательское тестирование `0.4.0-dev` и закрытие
аппаратного player regression pass без изменения данных аккаунта:

- проверить overscan, фокус и плотность на всех разделах;
- проверить timeline marker и buffering state в реальном воспроизведении;
- проверить ручной и автоматический переход через границу сезонов;
- дождаться естественного окончания с включённым и выключенным auto-next;
- после подтверждения назначить новый known-good baseline/tag.

Подробная очередь — в [`ROADMAP.md`](ROADMAP.md).
