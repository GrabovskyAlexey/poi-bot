# Repository Guidelines
- Всегда общайся и веди заметки на русском языке.
- Все файлы сохраняй в кодировке UTF-8 без BOM.
- Не выполняй коммиты без моего явного запроса; такие запросы считаю разовыми, а не постоянными.
- Не меняй текст и логику уже существующего функционала (включая команды) без прямого указания.

Шаблон создан по образцу `dungeoncrusherbot` (соседний каталог) без доменной части.

## Структура
Код: `src/main/kotlin/ru/grabovsky/poibot`: `bot` — приём апдейтов, `service` — бизнес-логика, `strategy` — команды и сценарии (flow), `client` — API-клиенты (Feign), `entity`/`repository` — данные, `config`/`framework`/`util` — инфраструктура. Ресурсы: `application.yaml`, шаблоны Freemarker `message/template/{flow}/{step}{,_ru,_en}.ftl`, локализация `messages_*.properties`, миграции Liquibase: корневой кумулятивный `liquibase/changelog.xml` включает по одному `changelog.xml` на папку (`liquibase/mvp/`, далее по папке на задачу), а сами миграции — formatted SQL `N.name.sql` в этих папках. Схема БД — `poi_bot`.

## Команды
- `./gradlew build` — сборка и тесты; `./gradlew test`; `./gradlew jacocoTestReport`
- `./gradlew bootRun` — запуск (нужны `PG_URL`, `PG_USER`, `PG_PASSWORD`, `TELEGRAM_BOT_NAME`, `TELEGRAM_BOT_TOKEN`)
- `docker compose up -d postgres` — локальная БД

## Архитектура
- `bot/Bot.kt` наследует `CommandLongPollingTelegramBot`, регистрирует `AbstractCommand` и публикует их через `SetMyCommands`.
- Команда (`AbstractCommand`) привязана к `FlowKey` (`FlowKeys.*`) и запускает сценарий через `FlowEngine.start`. Не-командные апдейты обрабатывает `ReceiverServiceImpl`: берёт последний активный flow из БД и делегирует в `FlowEngine`; callback без состояния перезапускает flow по payload (`FlowCallbackPayload`).
- `FlowEngine` держит `FlowHandler` по ключам, сохраняет шаг, payload (JSON) и биндинги сообщений в `flow_state`. Действия (`SendMessageAction`, `EditMessageAction`, ...) исполняет `TelegramFlowActionExecutor`: рендерит Freemarker-шаблон, строит клавиатуру, отслеживает messageId.
- Статичные экраны — `AbstractStaticFlow` (см. `strategy/flow/start`, `strategy/flow/help`). Интерактивные — свои `*FlowState`/`*Step` + `PromptSupport`.

## Доменная модель (как в dungeoncrusherbot — делать так же)
- Слои: `entity` (JPA, `kotlin-jpa` + `allOpen`, схема в `@Table(schema = "poi_bot")`, JSON-поля через `@JdbcTypeCode(SqlTypes.JSON)`) → `repository` (Spring Data JPA) → `service/interfaces` + `service/*Impl` (один интерфейс — одна реализация, бизнес-логика) → `strategy/dto` (модели для шаблонов) → `strategy/flow/<feature>`.
- Фича = пакет `strategy/flow/<feature>` (`*Flow`, `*FlowState`, `*Step : FlowStep`, `*ViewService`, `*PromptBuilder`) + `FlowKeys.<FEATURE>` + запись в `Command` + `*Command` + шаблоны `message/template/<feature>/<step>{,_ru,_en}.ftl` + ключи в `messages_*.properties` (lower.snake.case).
- Пользователь: `User` + `UserProfile` (1:1, `@MapsId`; флаги `isBlocked`/`isAdmin`, `locale`, настройки — JSON `UserSettings`). Доменные связи пользователя добавлять в `User` (OneToOne/ManyToMany) или в JSON-настройки профиля.
- Миграции Liquibase: кумулятивный `changelog.xml` → `<папка>/changelog.xml` → нумерованные SQL-файлы; миграции и релиз-ноуты отдельными коммитами. В dungeoncrusherbot также есть `UpdateMessage`/release notes, админ-сообщения (`AdminMessage` flow), `SchedulerService` (cron в `application.yaml`) — переносить по необходимости.
- Тесты: Kotest `ShouldSpec` + MockK, имена на английском (`shouldDoSomethingWhenPrecondition`), БД — Testcontainers; цель ≥80% покрытия `service` и `strategy`.

## Стиль и безопасность
Kotlin Style Guide, 4 пробела, один публичный класс на файл, `val` и неизменяемые коллекции. Секреты не коммитим: переменные окружения, локально — `application-local.yaml` (в `.gitignore`). Коммиты — на английском, в повелительном наклонении.

## Заметки по реализации (этап 1)
- Шаблоны локализуются двумя файлами: `step.ftl` (ru, по умолчанию) и `step_en.ftl` (Freemarker сам выбирает по локали). Тексты с пользовательскими данными — только `?html` и `FlowParseMode.HTML`.
- Тексты кнопок и алертов — в `messages_{ru,en}.properties` (ключи `buttons.*`, `alerts.*`, `unit.*`).
- Callback-кнопка к другому flow (например, «Изменить» → `ADD_PLACE`): если у flow нет состояния, `ReceiverServiceImpl` запускает его и передаёт `payload.data` как `FlowStartContext.args`.
- Поиск рядом всегда идёт по максимальному радиусу (1 км), результат раскладывается по радиусам в `NearbyResult.build`.
- Тест `PlaceRepositoriesIT` требует Docker (без него пропускается).

## Заметки по реализации (этап 2)
- Публикация: `saved_place_chat`, `PublishService`. Список групп пользователя собирается кнопкой `KeyboardButtonRequestChat` (сообщение `chat_shared`, flow `PUBLISH`), плюс авто-привязка при добавлении бота и при командах в группе.
- Группа: `GroupPlacesService` работает без flow-движка, callback-данные имеют `flow = "GP"`; в группе кнопки доступны всем, снять место с группы может владелец или администратор чата (`ChatMembershipChecker`).
- В группе `/nearby` просит ответить геопозицией на сообщение бота (privacy mode остаётся включённым, нужен селективный ForceReply — для него команда обрабатывается через `processMessage`).
- Liquibase без `default-schema`: схема создаётся первой миграцией, таблицы указываются с префиксом `poi_bot.`.

## Заметки по реализации (этап 3)
- Шаринг по ссылке `https://t.me/<bot>?start=sp_<token>`: токен хранится в `place_share_token` (один на запись, удаляется каскадом вместе с записью). `/start sp_<token>` открывает `SharedFlow`, кнопки которого несут токен, поэтому старые сообщения продолжают работать без состояния.
- «Сохранить себе» создаёт копию `SavedPlace` с тем же `place_id` (рейтинг/комментарии будут общими); повторное сохранение того же места блокируется.
- Временные сообщения помечаются `FlowMessage.autoDeleteAfterSeconds` и удаляются executor-ом по таймеру.

## Заметки по реализации (этап 4)
- Рейтинг (`place_rating`) и комментарии (`place_comment`) привязаны к `Place`, а не к записи. Оценивать и комментировать может только владелец записи о месте (`ReviewService` проверяет по `savedPlaceId`), читать — все; авторы комментариев нигде не показываются.
- Один комментарий на пользователя и место (уникальный индекс, миграция 7): повторный ввод заменяет текст (ReviewService.saveComment); скрытый по жалобам комментарий изменить нельзя. При слиянии мест конфликтующие комментарии одного автора разрешаются в пользу целевого места.
- Жалобы: `place_comment_report`, при `REPORTS_TO_HIDE` (3) разных жалобах комментарий скрывается; автор может удалить свой. Админ-панели нет.
- `PlaceLinkService.relink` меняет `place_id` записи, переносит оценку пользователя и, если на старом месте не осталось записей, сливает его (`merged_into_id`, перенос чужих оценок и комментариев). Кнопка «Другое место» в карточке видна только если рядом есть другие места.
- Метрики (Micrometer): `poi.place.resolve.shown{kind=strong|weak}` и `poi.place.resolve.result{result=linked|rejected}` — доля ложных «другое место».
- Callback-кнопки, которые должны работать повторно при уже существующем состоянии, не сравнивают состояние, а используют одноразовый флаг `fresh` (см. `SharedFlow`, `PublishFlow`) или вообще не хранят состояние (`ReviewsFlow`, `RelinkFlow` делают всё в `onCallback`).

## Деплой
- Схема как в `royalguardians`: `.github/workflows/deploy.yml` на каждый push в master: тесты → версия `vX.Y.Z` (major.minor из `version.txt`, patch - автоинкремент; для нового минора достаточно изменить файл) → `docker build`/`docker push` в GHCR → `appleboy/scp-action` + `appleboy/ssh-action` запускают `scripts/deploy-poibot.sh [тег]` (`docker pull` + `docker run --network host`, без docker compose; без аргумента — образ `latest`, с аргументом — конкретная версия, например откат `v0.1.3`) → git-тег и GitHub Release. Секреты GitHub: `VPS_SSH_HOST`, `VPS_SSH_PORT`, `SSH_USER`, `VPS_SSH_KEY`. Подробности — `docs/DEPLOY.md`.
- `.github/workflows/ci.yml`: тесты на каждый pull request в dev и master и на push в dev; проверка `Build and test` делается обязательной в защите веток.
- Версия приложения задаётся CI через `-PappVersion` (локально — `0.0.1-SNAPSHOT`) и пишется в лог при старте (`StartupInfo`). Jar собирается как `build/libs/poibot.jar` (plain-jar отключён); `Dockerfile` копирует именно её.
- Конфигурация бота на сервере — файл окружения `~/poibot/.env` (формат `docker --env-file`, образец `scripts/.env.server.example`, права 600): токен, `PG_URL`, `PG_USER`, `PG_PASSWORD`, необязательно `USE_PORT`. Профиль `prod` (`application-prod.yaml`) включает скрипт деплоя.
- Бот не поднимает свою БД: контейнер работает в сети хоста и подключается к существующей PostgreSQL (`jdbc:postgresql://localhost:5432/db?currentSchema=poi_bot`). Схему `poi_bot` задаёт `currentSchema` (так и таблицы Liquibase попадают в неё) и создаёт заранее администратор БД. Миграция 1 при первом запуске требует права `CREATE` на базу (`CREATE SCHEMA IF NOT EXISTS` проверяет его даже для существующей схемы). Применённые миграции не редактируем.
- Из-за сети хоста порт приложения (`USE_PORT`, по умолчанию 8091) слушает только `127.0.0.1` и не должен пересекаться с другими сервисами (8080 занят у royalguardians).
- Логи пишутся на хост в `~/poibot/logs/bot.log` (контейнер запускается от пользователя деплоя), смотреть `tail -f`.
- Идеи и улучшения после MVP — `docs/BACKLOG.md`.

## Заметки по релизу 0.2
- Кнопка «Изменить» в карточке несёт контекст `EDIT:<id>:<P|N>:<расстояние>`; `AddPlaceFlow` хранит `CardRef` и после сохранения перерисовывает карточку (`PlaceCardFactory.refreshAction`).
- `/nearby` после получения геопозиции отправляет короткое сообщение `searching` (убирает reply-клавиатуру и исчезает через 1 с). Геопозиция вне диалога (или не обработанная активным flow) запускает `NearbyFlow` с аргументом `LOC:<lat>:<lon>` (`ReceiverServiceImpl.searchNearby`).
- В личке `AbstractCommand.processMessage` удаляет сообщение с командой после обработки (ошибки удаления игнорируются).
- `/deleteme` (`DeleteMeFlow`, `UserDataService`): выгрузка JSON-документом (`SendDocumentAction`) и удаление в два шага; удаление выполняется только на шаге подтверждения. Данные чистятся каскадами БД, потом удаляются места без записей, оценок, комментариев и ссылок слияния.

## Заметки по релизу 0.3
- Списки (`PlaceListService`): пользователь хранит не более 500 мест, у чата их ещё меньше, поэтому поиск, фильтры, склейка дублей и сортировка делаются в Kotlin (`PlaceListing`) над загруженным списком - регистр кириллицы не зависит от локали БД. Оценки берутся одним запросом (`ReviewService.summaries`).
- `PlacesFlow`: состояние хранит `query`, `sort`, фильтры и `awaitingSearch`; текст поиска принимается только после кнопки «Поиск». Фильтры и сортировка работают поверх пагинации (при смене страница сбрасывается на первую).
- Группа: список и `/nearby` показывают место (`place_id`) один раз; список по умолчанию новые сверху, кнопка переключает на сортировку по рейтингу (callback `P:<страница>:<N|R>`).
- Снятие места в группе (`PublishService.unpublishAsModerator`): место, опубликованное несколькими участниками, в списке одно; администратор снимает все записи этого `place_id` в чате, участник - только свою. Кнопка «Снять» в карточке видна, только если `canModerate`; карточка из списка несёт страницу и сортировку (`O:<id>:<стр>:<N|R>`), после снятия список перерисовывается.
