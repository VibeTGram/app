# VibeTGram: проверка архитектурных фактов по первичным источникам

Дата проверки: 2026-08-15.

Этот документ фиксирует только факты, проверенные по официальной документации, исходному коду владельцев проектов и спецификациям платформ. Он не является юридическим заключением. Ссылки на ветки `master`/`development` нужно заменить на permalink конкретного commit при формировании релизного BOM.

Обозначения:

- **Подтверждено** — источник прямо описывает свойство или показывает его в исходном коде/API.
- **Архитектурный вывод** — решение VibeTGram, основанное на подтверждённых фактах; сам источник такого решения не предписывает.
- **Ограничение доказательства** — вывод из отсутствия интерфейса или структуры репозитория, а не явное обещание владельца проекта.

## 1. TDLib, MTProto и `td_api`

### Подтверждено

TDLib — самостоятельная кроссплатформенная библиотека для создания Telegram-клиентов. Её README прямо говорит, что библиотека берёт на себя сетевую реализацию, шифрование и локальное хранение данных; в исходниках TDLib присутствует отдельная реализация MTProto. Источники: [TDLib README](https://github.com/tdlib/td#features), [каталог `td/mtproto`](https://github.com/tdlib/td/tree/master/td/mtproto).

Публичная поверхность TDLib описывается схемой `td_api.tl`; README называет эту схему и сгенерированную HTML-документацию списком всех доступных методов и классов TDLib. Источники: [TDLib README — Examples and documentation](https://github.com/tdlib/td#examples-and-documentation), [`td_api.tl`](https://github.com/tdlib/td/blob/master/td/generate/scheme/td_api.tl), [сгенерированные классы TDLib](https://core.telegram.org/tdlib/docs/classes.html).

Клиент передаёт TDLib типизированный объект, являющийся наследником `td_api::Function`; это контракт TDLib, а не прямой вызов произвольного Telegram TL-конструктора. Источник: [`ClientManager::send`](https://core.telegram.org/tdlib/docs/classtd_1_1_client_manager.html).

Единственный похожий на универсальный публичный метод `sendCustomRequest` прямо документирован как «for bots only» и принимает имя и JSON-параметры специального запроса. Он не является универсальным user-account MTProto RPC. Источники: [`sendCustomRequest`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1send_custom_request.html), [определение в `td_api.tl`](https://raw.githubusercontent.com/tdlib/td/master/td/generate/scheme/td_api.tl).

### Ограничение доказательства

В проверенной публичной схеме `td_api.tl` нет документированного метода вида `invokeRawMtproto(method, payload)` для пользовательского аккаунта. Это вывод из того, что README называет `td_api.tl` полным списком публичных методов, а найденный `sendCustomRequest` ограничен ботами; это не отдельное обещание разработчиков, что такой метод никогда не появится. Источники: [TDLib README](https://github.com/tdlib/td#examples-and-documentation), [`td_api.tl`](https://github.com/tdlib/td/blob/master/td/generate/scheme/td_api.tl), [`sendCustomRequest`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1send_custom_request.html).

### Архитектурный вывод для VibeTGram

- TDLib остаётся единственным MTProto-движком приложения. Это предотвращает появление второй независимой авторизации и второго источника состояния; Telegram/TDLib не предписывают эту архитектуру.
- «Raw API» VibeTGram означает типизированную поверхность конкретной версии `td_api`, а не произвольный MTProto transport.
- Недостающий RPC добавляется узким изменением форка TDLib и новым типом в `td_api.tl`; commit TDLib и hash схемы закрепляются в BOM.
- Отсутствие generic raw-вызова должно автоматически перепроверяться при каждом обновлении закреплённого commit TDLib.

## 2. `tgnet`

### Подтверждено

Исходники `tgnet` находятся внутри репозитория официального Telegram Android по пути `TMessagesProj/jni/tgnet`. `ConnectionsManager.cpp` содержит управление соединениями, дата-центрами, сессиями и файлом конфигурации `tgnet.dat`. Источники: [дерево `TMessagesProj/jni/tgnet`](https://github.com/DrKLO/Telegram/tree/master/TMessagesProj/jni/tgnet), [`ConnectionsManager.cpp`](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/jni/tgnet/ConnectionsManager.cpp).

Telegram Android собирает `tgnet` как статическую библиотеку и линкует её в основной JNI-модуль клиента. Источник: [`TMessagesProj/jni/CMakeLists.txt`](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/jni/CMakeLists.txt).

### Ограничение доказательства

Расположение `tgnet` внутри Android-клиента подтверждено, но официальный источник не объявляет этот каталог стабильным отдельно версионируемым SDK для сторонних приложений. Формулировка «внутренний модуль Telegram Android» является выводом из структуры исходного репозитория, а не опубликованной гарантией API-совместимости. Источник: [репозиторий Telegram Android](https://github.com/DrKLO/Telegram).

### Архитектурный вывод для VibeTGram

`tgnet` не подключается ни вместо TDLib, ни параллельно TDLib. Если потребуется низкоуровневая Telegram-функция, предпочтителен узкий патч TDLib, потому что именно TDLib остаётся владельцем авторизации и состояния VibeTGram.

## 3. `tgcalls`

### Подтверждено

Официальный репозиторий называет `tgcalls` Telegram Calls Library и публикует его под LGPL-3.0. Источники: [`TelegramMessenger/tgcalls`](https://github.com/TelegramMessenger/tgcalls), [LICENSE](https://github.com/TelegramMessenger/tgcalls/blob/master/LICENSE).

Telegram MTProto передаёт клиенту JSON-параметры звонка, предназначенные для `tgcalls`; TDLib, со своей стороны, публикует типизированные call/signaling-методы и обновления. Это подтверждает разделение сигналинга Telegram и медиадвижка звонка. Источники: [`phoneCall` constructor](https://core.telegram.org/constructor/phoneCall), [`createCall`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1create_call.html), [`sendCallSignalingData`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1send_call_signaling_data.html), [`updateNewCallSignalingData`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_new_call_signaling_data.html).

Публичный C++-интерфейс `tgcalls` содержит управление аудиоустройствами, mute/volume, signaling data, видеозахватом и видеовыводом; в дереве исходников есть отдельные компоненты групповых звонков и захвата экрана. Источники: [`tgcalls/Instance.h`](https://github.com/TelegramMessenger/tgcalls/blob/development/tgcalls/Instance.h), [дерево `tgcalls/`](https://github.com/TelegramMessenger/tgcalls/tree/development/tgcalls).

### Архитектурный вывод для VibeTGram

- `tgcalls` подключается как отдельная native-зависимость на точном commit и закрывается собственным версионированным `CallEngine API`.
- Luau не получает прямых C/C++/JNI-дескрипторов `tgcalls`; доступ к звонкам возможен только через capability-проверяемую прослойку.
- LGPL obligations и способ динамической/статической линковки должны быть отдельно проверены до релиза; наличие LGPL-3.0 в репозитории не решает автоматически вопрос соблюдения лицензии конкретной сборкой.

## 4. Telegram API Terms и `api_id`

### Подтверждено

Каждое стороннее клиентское приложение обязано получить собственный `api_id`. Страница получения идентификатора также говорит, что `api_id` и `api_hash` нужны для авторизации пользователей, а примерный ID из открытого кода не подходит для публичного приложения. Источники: [Telegram API Terms, пункт 2.1](https://core.telegram.org/api/terms), [Obtaining `api_id`](https://core.telegram.org/api/obtaining_api_id).

Terms требуют корректной работы базовых функций и запрещают вмешательство в базовую функциональность, включая действия без ведома и согласия пользователя, сохранение самоуничтожающегося контента, сокрытие online/last seen, подмену read status/«ghost mode» и подавление typing status. Источник: [Telegram API Terms, пункты 1.3–1.4](https://core.telegram.org/api/terms).

Terms не делают исключения для функций, загруженных как мод. Напротив, пункт 1.2 разрешает расширения лишь при условии, что они не нарушают Terms. При неисправленном нарушении Telegram оставляет за собой прекращение доступа приложения к API. Источник: [Telegram API Terms, пункты 1.2 и 4](https://core.telegram.org/api/terms).

### Архитектурный вывод для VibeTGram

- Размещение спорной функции в аддоне и пользовательское предупреждение не превращают её в соответствующую Terms функцию. Это буквальное проектное толкование текста, не юридическое заключение.
- Согласованный `Modification Mode` с 15-секундным предупреждением информирует пользователя о риске, но не устраняет риск блокировки аккаунта или общего `api_id`.
- Метка `verified` в магазине должна означать проверку конкретного исходного commit и безопасности, а не соответствие Telegram API Terms.

## 5. Luau: песочница, память и прерывание

### Подтверждено

Официальная документация Luau заявляет пригодность VM для исполнения недоверенного кода, но подчёркивает необходимость сотрудничества embedder-а: безопасность зависит от того, какие host API он открыл скрипту. Источник: [Embedding a sandboxed Luau virtual machine](https://luau.org/sandbox/).

`luaL_sandbox` делает стандартные библиотеки, встроенные metatable и глобальную таблицу read-only; `luaL_sandboxthread` создаёт для потока отдельную глобальную таблицу, чьи записи не меняют исходное глобальное окружение. Источник: [Luau C API — Sandboxing](https://luau.org/api/#sandboxing).

`lua_newstate` принимает пользовательский allocator типа `lua_Alloc`; allocator сигнализирует отказ возвратом `nullptr`. VM также позволяет учитывать память по категориям через `lua_setmemcat`/`lua_totalbytes`. Источник: [Luau C API — Virtual Machine State и Memory](https://luau.org/api/).

Через `lua_callbacks` embedder может установить `interrupt`; документация указывает safepoints на обратных рёбрах циклов, вызовах/возвратах и GC. Это даёт host-у точку для watchdog и прекращения зависшего скрипта. Источник: [Luau C API — Callbacks](https://luau.org/api/#callbacks).

Luau отдельно предупреждает, что sandbox сам по себе не гарантирует завершение скрипта: недоверенный код может пытаться исчерпать CPU/память, а долгий вызов экспортированной C-функции задерживает срабатывание interrupt. Для более сильной изоляции trusted и untrusted code руководство рекомендует отдельные VM. Источники: [Luau `SECURITY.md`](https://github.com/luau-lang/luau/blob/master/SECURITY.md), [Sandbox — Interrupts](https://luau.org/sandbox/#interrupts), [Sandbox — Environment](https://luau.org/sandbox/#environment).

### Архитектурный вывод для VibeTGram

- Для каждого `(mod, account)` создаётся отдельный `lua_State`; это более сильная граница, чем одна VM с `luaL_sandboxthread`, но её выбирает VibeTGram, а не требует Luau.
- Пользовательский allocator реализует жёсткий memory budget, а `interrupt` — instruction/time budget. Наличие hooks само по себе лимиты не устанавливает: корректность нужно подтверждать стресс-тестами.
- Встроенные Java/JNI handles, произвольная файловая система, сокеты и reflection не экспортируются в Lua. Все полномочия выдаются только мостом VibeTGram после Policy Engine.
- Первая версия использует только интерпретатор: без Luau native code generation/JIT и без сохраняемого бинарного/bytecode-кэша. Это проектное ограничение, не требование Luau.

## 6. Android background work и foreground services

### Подтверждено

Foreground service предназначен для заметной пользователю работы и обязан показывать уведомление. Android ограничивает обычные background services начиная с API 26. Источник: [Android Services overview](https://developer.android.com/develop/background-work/services).

Для приложений с target Android 12+ запуск foreground service из фона разрешён лишь в определённых исключениях. Для target Android 14+ система также проверяет объявленный тип FGS и соответствующие разрешения. Источник: [Launch a foreground service](https://developer.android.com/develop/background-work/services/fgs/launch).

У разных типов foreground service есть собственные ограничения и timeout-правила, меняющиеся между Android-версиями. Например, ограничения `dataSync` и запуска из `BOOT_COMPLETED` нельзя игнорировать при повышении `targetSdk`. Источники: [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [Changes to foreground services](https://developer.android.com/develop/background-work/services/fgs/changes).

### Архитектурный вывод для VibeTGram

- FCM является основным фоновым механизмом. Foreground-service fallback включается явно и всегда имеет видимое уведомление.
- Нельзя считать FGS безусловной гарантией «вечного сокета». Перед каждым повышением `targetSdk` нужно заново проверить допустимый service type, старт после перезагрузки, timeout и поведение OEM на физическом релизном устройстве.

## 7. FCM, TDLib push и microG

### Подтверждено

Официальный Android FCM SDK получает/обновляет registration token и доставляет data messages через `FirebaseMessagingService.onMessageReceived`; callback имеет короткое окно выполнения, а длительную работу документация предлагает передавать подходящему background mechanism. Источники: [FCM Android setup](https://firebase.google.com/docs/cloud-messaging/android/get-started), [Receive messages](https://firebase.google.com/docs/cloud-messaging/android/receive-messages), [`FirebaseMessaging` reference](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging).

TDLib поддерживает регистрацию push-устройства через `registerDevice`, включая `deviceTokenFirebaseCloudMessaging` с флагом дополнительного шифрования. Полученный payload обрабатывается методом `processPushNotification`. Источники: [`DeviceToken`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_device_token.html), [`deviceTokenFirebaseCloudMessaging`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1device_token_firebase_cloud_messaging.html), [`processPushNotification`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1process_push_notification.html).

TDLib Notification API отдельно описывает end-to-end encrypted FCM push и порядок `getPushReceiverId` → выбор TDLib instance → `processPushNotification`, включая несколько аккаунтов. Источник: [TDLib Notification API](https://core.telegram.org/tdlib/notification-api/).

Проект microG сам указывает Firebase Cloud Messaging как полностью реализованную функцию. Его wiki также предупреждает, что FCM-приложения должны быть установлены после GmsCore; если официальные Google Play Services ранее были установлены, `Prerequisites` предписывает удалить официальный `GmsCore`/`com.google.android.gms` и перечисленные привилегированные Google-пакеты перед установкой microG. Источники: [microG Implementation status](https://github.com/microg/GmsCore/wiki/Implementation-Status), [Helpful Information](https://github.com/microg/GmsCore/wiki/Helpful-Information), [Prerequisites](https://github.com/microg/GmsCore/wiki/Prerequisites).

Официальная документация Firebase перечисляет поддержанный Google сценарий с Google Play Store/Google APIs; она не обещает поддержку microG. Источник: [FCM Android prerequisites](https://firebase.google.com/docs/cloud-messaging/android/get-started).

### Архитектурный вывод для VibeTGram

- Одна GitHub-сборка сначала пытается получить FCM token при наличии совместимого provider-а: официальный GMS или microG.
- microG — тестируемая VibeTGram совместимость, а не гарантия Google. Release-gate обязан проверять регистрацию, ротацию token, encrypted payload и запуск нужного TDLib account на реальном microG-телефоне.
- Если FCM registration или delivery не работают, приложение предлагает foreground-service fallback; отказ пользователя означает отсутствие гарантии мгновенных уведомлений.
- `encrypt = true` задаётся всегда. Analytics и Crashlytics не подключаются автоматически только потому, что используется FCM; FCM-документация позволяет отдельно управлять auto-init и Analytics collection. Источник: [FCM — Prevent auto initialization](https://firebase.google.com/docs/cloud-messaging/android/get-started#prevent-auto-initialization).

## 8. Android WebView multi-profile для Mini Apps

### Подтверждено

AndroidX WebKit `Profile` представляет отдельную WebView browsing session со своим набором данных. `ProfileStore` создаёт, перечисляет и удаляет профили. Источники: [`Profile`](https://developer.android.com/reference/androidx/webkit/Profile), [`ProfileStore`](https://developer.android.com/reference/androidx/webkit/ProfileStore).

Multi-profile API доступен только при поддержке runtime feature `WebViewFeature.MULTI_PROFILE`; этот feature покрывает отдельные `CookieManager`, `WebStorage`, geolocation permissions и service-worker controller профиля. Источник: [`WebViewFeature.MULTI_PROFILE`](https://developer.android.com/reference/androidx/webkit/WebViewFeature#MULTI_PROFILE).

`WebViewCompat.setProfile` должен вызываться до других операций с WebView и привязывает конкретный WebView к именованному профилю. Источник: [`WebViewCompat.setProfile`](https://developer.android.com/reference/androidx/webkit/WebViewCompat#setProfile(android.webkit.WebView,%20java.lang.String)).

Платформенный `WebView.setDataDirectorySuffix`, доступный с API 28, назначает
процессу отдельный каталог WebView-данных. Документация требует вызвать его до
создания любого WebView и до других обращений к `android.webkit`; после
инициализации смена suffix вызывает ошибку. Источник:
[`WebView.setDataDirectorySuffix`](https://developer.android.com/reference/android/webkit/WebView#setDataDirectorySuffix(java.lang.String)).

### Архитектурный вывод для VibeTGram

- При наличии `MULTI_PROFILE` каждому Telegram-аккаунту назначается свой WebView profile.
- Поддержку feature нужно проверять runtime-вызовом `WebViewFeature.isFeatureSupported`; `minSdk = 30` не гарантирует возможности конкретной установленной реализации WebView.
- При отсутствии feature fallback назначает каждому аккаунту отдельный
  непрозрачный data-directory suffix. В одном процессе может быть активен только
  один такой аккаунт; переключение требует controlled process restart до
  инициализации следующего suffix. Очистка общего CookieManager сама по себе не
  считается account isolation. Это архитектурный вывод VibeTGram из контракта
  `setDataDirectorySuffix`, а не готовая multi-profile возможность AndroidX.
- Luau-модам не экспортируются WebView instance, JavaScript interface, CookieManager или WebStorage. Mini App permissions выдаются Android-слоем на конкретный origin.

## 9. GitHub Releases и встроенный updater

### Подтверждено

GitHub Releases позволяют публиковать скачиваемые бинарные assets; REST API возвращает URL, размер и digest asset. Источник: [GitHub REST API — release assets](https://docs.github.com/en/rest/releases/assets).

GitHub endpoint `/releases/latest` возвращает последний опубликованный release, исключая drafts и prereleases. Поэтому он годится для Stable, но не является готовым механизмом каналов Preview/Nightly. Источник: [Get the latest release](https://docs.github.com/en/rest/releases/releases#get-the-latest-release).

Immutable releases — отдельная включаемая функция GitHub: она запрещает изменение release assets и связанного tag после публикации и создаёт attestation. Artifact attestations дают provenance/SBOM-сведения, но GitHub прямо отмечает, что attestation не гарантирует безопасность артефакта. Источники: [GitHub supply-chain security](https://docs.github.com/en/code-security/concepts/supply-chain-security/supply-chain-security), [Verify release integrity](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/verify-release-integrity).

Android `PackageInstaller` предусматривает статус `STATUS_PENDING_USER_ACTION`; приложение должно уметь передать системный intent пользователю для продолжения установки. Даже режим `USER_ACTION_NOT_REQUIRED` имеет условия и не гарантирует отсутствие пользовательского шага. Источники: [`PackageInstaller.STATUS_PENDING_USER_ACTION`](https://developer.android.com/reference/android/content/pm/PackageInstaller#STATUS_PENDING_USER_ACTION), [`SessionParams.setRequireUserAction`](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)).

Android принимает обновление существующего приложения при совпадении application ID и signing identity (с учётом поддержанной ротации ключа), а также проверяет version code. Источник: [How app updates work](https://developer.android.com/google/play/app-updates).

### Архитектурный вывод для VibeTGram

- GitHub Release asset является транспортом, но не заменяет собственную trust policy приложения.
- Updater получает отдельно подписанный manifest, сверяет канал, SHA-256, подпись manifest и Android signing certificate, а затем всегда выбирает flow с явным действием пользователя через системный installer.
- Для Releases следует включить immutability и provenance/attestations, но корнем доверия VibeTGram остаётся офлайн-ключ Stable и проверка Android signing certificate.

## 10. Как устроены модификации AyuGram и exteraGram

Проверенные revisions:

- AyuGram Desktop `dev` —
  [`db3b9891cb0b04ebb7d8c0e71ada3bcc669b910a`](https://github.com/AyuGram/AyuGramDesktop/commit/db3b9891cb0b04ebb7d8c0e71ada3bcc669b910a);
- exteraGram `main` —
  [`6f78031aafd7f27e0dfaa31a207715569a6df1a2`](https://github.com/exteraSquad/exteraGram/commit/6f78031aafd7f27e0dfaa31a207715569a6df1a2).

### Подтверждено: AyuGram Desktop

AyuGram Desktop является форком Telegram Desktop, а его функции находятся в
собственном C++-поддереве
[`Telegram/SourceFiles/ayu`](https://github.com/AyuGram/AyuGramDesktop/tree/db3b9891cb0b04ebb7d8c0e71ada3bcc669b910a/Telegram/SourceFiles/ayu)
и непосредственно компонуются с исходниками клиента. Это встроенные изменения
форка, а не загружаемый скриптовый Mod API.

Настройки реализованы собственными C++-классами и сохраняются в
`tdata/ayu_settings.json`. Ghost-mode хранит отдельные account-настройки для
read messages/stories, online и upload packets; worker напрямую отправляет
Telegram Desktop MTP-запрос `account.updateStatus`. Источники:
[`ayu_settings.cpp`](https://github.com/AyuGram/AyuGramDesktop/blob/db3b9891cb0b04ebb7d8c0e71ada3bcc669b910a/Telegram/SourceFiles/ayu/ayu_settings.cpp),
[`ayu_worker.cpp`](https://github.com/AyuGram/AyuGramDesktop/blob/db3b9891cb0b04ebb7d8c0e71ada3bcc669b910a/Telegram/SourceFiles/ayu/ayu_worker.cpp).

История удалённых/изменённых сообщений хранится в отдельной SQLite-модели
`DeletedMessage`/`EditedMessage`, а bridge преобразует текущие `HistoryItem` в
эти записи. Источники:
[`ayu_database.cpp`](https://github.com/AyuGram/AyuGramDesktop/blob/db3b9891cb0b04ebb7d8c0e71ada3bcc669b910a/Telegram/SourceFiles/ayu/data/ayu_database.cpp),
[`messages_storage.cpp`](https://github.com/AyuGram/AyuGramDesktop/blob/db3b9891cb0b04ebb7d8c0e71ada3bcc669b910a/Telegram/SourceFiles/ayu/data/messages_storage.cpp).

В дереве есть UI с именем `plugin_info_box`, который разбирает Python-подобные
metadata-поля, но его экран прямо показывает `PluginsNotAvailable` и не содержит
исполнения кода. Поэтому этот файл не является доказательством существования
plugin runtime. Источник:
[`plugin_info_box.cpp`](https://github.com/AyuGram/AyuGramDesktop/blob/db3b9891cb0b04ebb7d8c0e71ada3bcc669b910a/Telegram/SourceFiles/ayu/ui/boxes/plugin_info_box.cpp).

### Подтверждено: exteraGram

exteraGram является форком официального Telegram Android и на дату проверки
архивирован владельцем. Его собственные Java-классы находятся в
[`com.exteragram`](https://github.com/exteraSquad/exteraGram/tree/6f78031aafd7f27e0dfaa31a207715569a6df1a2/TMessagesProj/src/main/java/com/exteragram),
а настройки, updater flags и feature switches загружаются из Android
`SharedPreferences` классом
[`ExteraConfig`](https://github.com/exteraSquad/exteraGram/blob/6f78031aafd7f27e0dfaa31a207715569a6df1a2/TMessagesProj/src/main/java/com/exteragram/messenger/ExteraConfig.java).

Функции подключаются прямыми условиями и вызовами из модифицированных классов
официального клиента; например,
[`LaunchActivity`](https://github.com/exteraSquad/exteraGram/blob/6f78031aafd7f27e0dfaa31a207715569a6df1a2/TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java)
читает `ExteraConfig.checkUpdatesOnLaunch`. В проверенном дереве нет выделенного
Luau/Lua scripting runtime, манифеста capabilities или общего sandboxed host
bridge.

### Ограничение доказательства и вывод для VibeTGram

Вывод об отсутствии общего plugin runtime основан на структуре и исходниках
зафиксированных commits; он не утверждает, что авторы никогда не
экспериментировали с ним в других ветках или внешних проектах.

AyuGram и exteraGram полезны как каталог поведения и примеры точек интеграции,
но не как модель динамической загрузки сторонних модов. Их fork-модель даёт
функции полный внутренний доступ клиента и требует пересборки приложения.
VibeTGram намеренно заменяет этот implicit trust явными semantic/raw
интерфейсами, Luau sandbox, capabilities, per-account identity и подписанным
каталогом. Код, assets и переводы этих клиентов не копируются.

## 11. Итог независимой проверки

Подтверждены основные опоры архитектуры: TDLib действительно закрывает сетевой/криптографический слой Telegram и публикует типизированный `td_api`; публичного generic user-account raw MTProto метода в проверенной схеме нет; `tgnet` находится внутри Telegram Android; `tgcalls` является отдельной LGPL calls library; Luau предоставляет необходимые primitives для sandboxing, memory accounting и interrupts; TDLib, FCM, Android WebView profiles и GitHub Releases имеют нужные базовые API.

Три решения нельзя ошибочно представлять как гарантии upstream:

1. Полная безопасность Luau зависит от минимального host bridge, Policy Engine, quotas и тестов VibeTGram, а не только от вызова `luaL_sandbox` ([Luau sandbox](https://luau.org/sandbox/)).
2. microG FCM — заявленная microG совместимость, не официальный support contract Firebase; её нужно подтверждать release-тестом ([microG status](https://github.com/microg/GmsCore/wiki/Implementation-Status), [Firebase prerequisites](https://firebase.google.com/docs/cloud-messaging/android/get-started)).
3. Foreground service не даёт бессрочного универсального исключения из фоновых ограничений Android; fallback нужно пересматривать при каждом повышении `targetSdk` ([Android FGS changes](https://developer.android.com/develop/background-work/services/fgs/changes)).
