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
