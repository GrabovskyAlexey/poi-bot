package ru.grabovsky.poibot.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.grabovsky.poibot.entity.FlowState
import ru.grabovsky.poibot.repository.FlowStateRepository
import ru.grabovsky.poibot.service.interfaces.FlowStateService
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowKey
import ru.grabovsky.poibot.strategy.flow.core.engine.FlowStateSnapshot

@Service
class FlowStateServiceImpl(
    private val repository: FlowStateRepository,
) : FlowStateService {

    @Transactional(readOnly = true)
    override fun load(userId: Long, flowKey: FlowKey): FlowStateSnapshot? {
        val entity = repository.findByUserIdAndFlowKey(userId, flowKey.value) ?: return null
        return FlowStateSnapshot(
            userId = entity.userId,
            flowKey = FlowKey(entity.flowKey),
            stepKey = entity.stepKey,
            payload = entity.payload,
            messageBindings = entity.messageBindings?.toMap().orEmpty(),
        )
    }

    @Transactional
    override fun save(snapshot: FlowStateSnapshot) {
        val entity = repository.findByUserIdAndFlowKey(snapshot.userId, snapshot.flowKey.value)
            ?: FlowState(
                userId = snapshot.userId,
                flowKey = snapshot.flowKey.value,
                stepKey = snapshot.stepKey,
            )
        entity.stepKey = snapshot.stepKey
        entity.payload = snapshot.payload
        entity.messageBindings = snapshot.messageBindings.takeIf { it.isNotEmpty() }?.toMutableMap()
        repository.save(entity)
    }

    @Transactional
    override fun clear(userId: Long, flowKey: FlowKey) {
        repository.deleteByUserIdAndFlowKey(userId, flowKey.value)
    }

    @Transactional
    override fun findListFlow(userId: Long): FlowState? {
        return repository.findAllFlowStatesByUserId(userId).sortedBy { it.updatedAt }.lastOrNull()
    }
}