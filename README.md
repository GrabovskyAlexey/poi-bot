# poibot

Telegram-бот для сохранения мест интереса (бары, кафе, рестораны): личные места с фото и геопозицией, поиск рядом, публикация в группы, шаринг по ссылке, оценки и анонимные комментарии. Kotlin, Spring Boot, PostgreSQL, long polling.

## Быстрый старт
1. Скопируйте `.env.example` в `.env` и заполните `TELEGRAM_BOT_TOKEN` и `TELEGRAM_BOT_NAME`.
2. Запустите PostgreSQL: `docker compose up -d postgres` (или стартуйте приложение из IDE — `spring-boot-docker-compose` поднимет БД сам).
3. Запустите `PoiBotApplication` с переменными из `.env` (в IntelliJ IDEA — через «Paths to .env files» или плагин EnvFile).
4. Тесты: `./gradlew test` (интеграционные тесты используют Testcontainers и требуют Docker).

## Документация
- `AGENTS.md` — архитектура, соглашения и заметки по реализации.
- `docs/ROADMAP.md` — выполненный план MVP и чеклист.
- `docs/DEPLOY.md` — CI, версионирование и деплой на сервер.
- `docs/BACKLOG.md` — идеи и улучшения после MVP.
