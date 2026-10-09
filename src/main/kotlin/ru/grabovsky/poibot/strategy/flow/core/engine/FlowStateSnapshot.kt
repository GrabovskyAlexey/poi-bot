package ru.grabovsky.poibot.strategy.flow.core.engine

/**
 * Снимок состояния флоу, возвращаемый persistence-слоем.
 */
data class FlowStateSnapshot(
    val userId: Long,
    val flowKey: FlowKey,
    val stepKey: String,
    val payload: String?,
    val messageBindings: Map<String, Int>,
)
