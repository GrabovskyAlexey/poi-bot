package ru.grabovsky.poibot.strategy.commands

enum class Command(
    val command: String,
    val text: String,
    /** Описание для меню у пользователей с английским Telegram. */
    val textEn: String,
    val order: Int,
    /** Скрытая команда работает, но не показывается в меню бота. */
    val hidden: Boolean = false,
) {
    START("start", "🟢 Начать пользоваться ботом", "🟢 Start using the bot", 1),
    ADD("add", "➕ Добавить место", "➕ Add a place", 2),
    PLACES("places", "📍 Мои места", "📍 My places", 3),
    NEARBY("nearby", "🧭 Найти рядом", "🧭 Find nearby", 4),
    SETTINGS("settings", "⚙️ Настройки", "⚙️ Settings", 80),
    DELETE_ME("deleteme", "🗑 Удалить мои данные", "🗑 Delete my data", 90),
    HELP("help", "❓ Помощь", "❓ Help", 99),
    ADMIN("admin", "📊 Администратор", "📊 Administrator", 100, hidden = true);
}
