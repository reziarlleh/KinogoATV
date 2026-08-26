# Авторизация и синхронизация Kinogo

> **Статус документа:** снимок наблюдаемого протокола на указанные даты. Актуальные
> архитектурные границы и состояние реализации см. в
> [`SERVICE_INTEGRATION.md`](SERVICE_INTEGRATION.md) и
> [`PROJECT_STATE.md`](PROJECT_STATE.md).

Состояние login/bookmark HTML-протокола проверено 16 июля 2026 года; нативный registration-клиент
добавлен 15 августа 2026 года по browser-visible двухшаговому DLE flow. Final hardware
instrumentation подтвердил rules gate и выход из scroll boundary к safe decline; live submit
новой учётной записи ещё не выполнен.
Документированного публичного API у
сервиса нет: текущая реализация повторяет обычные HTTPS GET/POST сайта, хранит cookie только
внутри origin проверенного зеркала и разбирает серверный HTML/ответы DLE.

22 июля 2026 года в официальном Android APK обнаружен отдельный закрытый JSON-шлюз. Он
использует Bearer-токены, но не поддерживает статусные разделы сайта и не синхронизирует
точную позицию просмотра. Поэтому существующая HTML-сессия остаётся необходимой для
«Смотрю»/«Смотрел»/«Буду»/«Бросил» и межзеркального повторного входа. Подробности шлюза —
в `OFFICIAL_APP_RESEARCH.md`.

## Вход и хранение

- форма входа: `POST /` с `login_name`, `login_password`, `login=submit`;
- `dle_group = 5` в полученном HTML означает гостя, другое числовое значение — выполненный вход;
- свежий `dle_login_hash` берётся из авторизованной страницы и используется для «Избранного»;
- все `Set-Cookie` хранятся раздельно по origin и не копируются между зеркалами;
- при смене зеркала приложение входит на новом проверенном origin теми же сохранёнными
  реквизитами; POST с паролем никогда не следует за cross-origin redirect;
- логин и пароль не имеют срока истечения в приложении. Они записываются в отдельный DataStore
  `kinogo_auth`, зашифрованы AES-256/GCM ключом Android Keystore и удаляются только явным
  действием пользователя;
- auth DataStore исключён из Android Backup, потому что device-bound Keystore-ключ на другом
  устройстве расшифровать его не сможет.

## Регистрация

`KinogoRegistrationApi` открывает `/index.php?do=register` тем же origin-scoped
`KinogoSessionHttpClient`, что и login-flow. Структура формы и имена полей разбираются
из текущего HTML; same-origin action и hidden server fields хранятся только в памяти этой
формы и скрыты из `toString`/диагностики.

Если первый ответ содержит DLE rules gate, parser возвращает отдельный
`RegistrationRulesPage`. UI сначала показывает правила и фокусирует безопасное действие
`Не принимаю`; POST с `dle_rules_accept=yes` возможен только после явного OK на
`Принимаю и продолжить`. Лишь серверный ответ на этот POST открывает account form.

Если DLE требует image CAPTCHA:

- изображение загружается в той же cookie-сессии и только с same-origin relative path;
- размер ограничен 512 KiB, а PNG/JPEG/GIF/WebP проверяются по magic bytes и
  допустимому content type;
- UI до bitmap allocation отклоняет размерность больше 4096×4096 или 8 млн pixels и
  downsample-ит допустимое изображение к bounded 840×256 decode в RGB_565;
- код вводит сам пользователь; приложение не обходит CAPTCHA и не отправляет её во внешний
  recognition service;
- refresh заново получает всю форму и картинку, чтобы не разорвать одноразовое DLE-состояние.

reCAPTCHA, hCaptcha, Turnstile и иные интерактивные challenge встроенная форма не
обходит: UI показывает, что этот тип не поддерживается. Server rejection возвращает
сообщение и обновлённую форму/CAPTCHA. После успешного submit те же login/password
передаются существующему `saveAndLogin`, то есть последующее хранение credentials не имеет
отдельного plaintext-пути.

Поля login/e-mail/password/confirmation/CAPTCHA используют только Compose `remember`, не
`rememberSaveable`: dismiss/recreation не сериализует чувствительный ввод. Монотонная
registration generation вместе с exact origin защищает UI и `saveAndLogin` от late response
старого load/rules/submit после retry, dismiss или смены зеркала.

## Закладки

Взаимоисключающий статус меняется запросом:

```text
POST /engine/ajax/controller.php?mod=mylist
post_id=<DLE post id>&folder=watch|done|todo|drop|0
```

`folder=0` — действие «Не смотрел»: оно удаляет материал из всех четырёх статусных разделов.
Это не отдельный раздел и не список всех непросмотренных материалов. В локальной модели
результат представлен как `status = null`. Если независимое «Избранное» тоже выключено,
материал полностью исчезает из «Закладок»; если включено — остаётся только в «Избранном».

Читаются статусы со страниц:

- `/favorites/watch/` — «Смотрю»;
- `/favorites/done/` — «Смотрел»;
- `/favorites/todo/` — «Буду»;
- `/favorites/drop/` — «Бросил».

Независимое «Избранное» читается с `/favorites/` и меняется через
`GET /engine/ajax/controller.php?mod=favorites` с `fav_id`, `action=plus|minus`,
`skin=kinogoB`, `alert=0`, `user_hash`.

Локальная модель хранит один nullable status и отдельный Boolean favorite. Каждое измерение
имеет coalescing-outbox, поэтому последняя офлайн-команда не теряется, включая снятие статуса
или «Избранного». При первом входе локальные и серверные избранные объединяются; после него
серверный snapshot является базой, а ещё не отправленные локальные изменения имеют приоритет.

## Прогресс просмотра

Kinogo имеет `POST /engine/ajax/controller.php?mod=series`, но он переключает лишь грубую
отметку просмотренной серии и не принимает секунду, перевод или качество.

Актуальный iframe `cinemar.cc` сохраняет resume в своём `localStorage` под ключом вида
`pljsplayfrom_<host><cuid>`. В значении находятся `file_id`, позиция, длительность и время;
по `file_id` плеер восстанавливает серию и `voice_id`. Серверного account endpoint для этих
данных в сетевом протоколе не обнаружено. Нативный Media3-плеер не разделяет localStorage с
браузером, поэтому точное продолжение остаётся локальным на Android TV.

Продуктовый контракт C-010 фиксирует эту границу:

- с аккаунтом сайта синхронизируются только status и независимое favorite;
- season/episode/voice/quality/position/history хранятся только на устройстве в
  `PlaybackProgressStore`;
- provider `localStorage` может помочь только повторно открыть тот же WebView-профиль;
  это не account sync и не обмен позицией с сайтом;
- новую передачу playback progress нельзя добавлять без подтверждённого серверного endpoint
  и отдельного продуктового решения.

Источники протокола:

- <https://kinogo.parts/>
- <https://kinogo.parts/templates/kinogoB/js/app.js?1.1.3>
- <https://kinogo.parts/engine/classes/js/dle_js.min.js?v=412fc.6.4>
- <https://kinogo.parts/templates/kinogoB/js/hdvb.js?1.0.5>
