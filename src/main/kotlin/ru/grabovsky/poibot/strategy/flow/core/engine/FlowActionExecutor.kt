package ru.grabovsky.poibot.strategy.flow.core.engine

import org.telegram.telegrambots.meta.api.objects.User
import java.util.*

/**
 * Выполняет набор действий, возвращая изменённые биндинги сообщений.
 */
interface FlowActionExecutor {
    fun execute(
        user: User,
        locale: Locale,
        currentBindings: Map<String, Int>,
        actions: List<FlowAction>,
    ): FlowBindingsMutation
}

data class FlowBindingsMutation(
    val replacements: Map<String, Int> = emptyMap(),
    val removed: Set<String> = emptySet(),
)
