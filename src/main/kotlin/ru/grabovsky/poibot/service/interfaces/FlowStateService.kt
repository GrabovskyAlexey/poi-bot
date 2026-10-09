package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.entity.FlowState
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKey
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowStateSnapshot

interface FlowStateService {
    fun load(userId: Long, flowKey: FlowKey): FlowStateSnapshot?
    fun save(snapshot: FlowStateSnapshot)
    fun clear(userId: Long, flowKey: FlowKey)
    fun findListFlow(userId: Long): FlowState?
}