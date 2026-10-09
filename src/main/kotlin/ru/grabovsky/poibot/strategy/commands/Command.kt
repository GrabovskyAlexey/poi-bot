package ru.grabovsky.poibot.strategy.commands

enum class Command(
    val command: String,
    val text: String,
    val order: Int
) {
    START("start", "🟢 Начать пользоваться ботом", 1),
    ADD("add", "➕ Добавить место", 2),
    PLACES("places", "📍 Мои места", 3),
    NEARBY("nearby", "🧭 Найти рядом", 4),
    HELP("help", "❓ Помощь", 99);
}
