package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.grabovsky.poibot.entity.FlowState

interface FlowStateRepository : JpaRepository<FlowState, Long> {
    fun findByUserIdAndFlowKey(userId: Long, flowKey: String): FlowState?
    fun deleteByUserIdAndFlowKey(userId: Long, flowKey: String)
    fun findAllFlowStatesByUserId(userId: Long): List<FlowState>
}