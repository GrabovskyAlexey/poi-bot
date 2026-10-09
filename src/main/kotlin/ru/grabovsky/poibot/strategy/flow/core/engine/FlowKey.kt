package ru.grabovsky.poibot.strategy.flow.core.engine

/**
 * Уникальный идентификатор флоу. Используем строковое значение, чтобы не быть
 * привязанными к конкретной реализации (enum/list) и иметь возможность хранить
 * идентификатор в базе как простой текст.
 */
@JvmInline
value class FlowKey(val value: String) {
    override fun toString() = value
}
