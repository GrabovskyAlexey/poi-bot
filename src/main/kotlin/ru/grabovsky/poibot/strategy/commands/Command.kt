package ru.grabovsky.poibot.strategy.commands

enum class Command(
    val command: String,
    val text: String,
    val order: Int
) {
    START("start", "🟢 Начать пользоваться ботом", 1),
    HELP("help", "❓ Помощь", 99);
}
