package ru.grabovsky.poibot.strategy.command

enum class Command(
    val command: String,
    val text: String
) {
    START("start", "Начать пользоваться ботом"),
    ADD("add", "Добавить место"),
    HELP("help", "Помощь"),
    LIST("list", "Получить список мест");
}