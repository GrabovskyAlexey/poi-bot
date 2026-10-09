package ru.grabovsky.poibot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.grabovsky.poibot.entity.FlowState

interface FlowStateRepository : JpaRepository<FlowState, Long> {
    @Modifying
    @Query("delete from FlowState f where f.userId = :userId")
    fun deleteByUserId(@Param("userId") userId: Long): Int

    fun findByUserIdAndFlowKey(userId: Long, flowKey: String): FlowState?
    fun deleteByUserIdAndFlowKey(userId: Long, flowKey: String)
    fun findAllFlowStatesByUserId(userId: Long): List<FlowState>
}