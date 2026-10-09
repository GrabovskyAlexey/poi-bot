package ru.grabovsky.poibot.strategy.flow.core.engine

/**
 * Шаг внутри флоу. Представляем как объект с уникальным ключом, чтобы хранить
 * его в Persistence-слое и использовать в логике переходов.
 */
interface FlowStep {
    val key: String
}
