package io.infra.structure.schedule.admin.persistence

import io.infra.structure.schedule.admin.persistence.mapper.ScheduleRouteCursorMapper
import io.infra.structure.schedule.repository.RouteCursorRepository
import org.springframework.transaction.annotation.Transactional

/** 基于 MySQL 的 ROUND 轮询游标仓储。 */
open class FlexRouteCursorRepository(
    private val mapper: ScheduleRouteCursorMapper
) : RouteCursorRepository {
    @Transactional
    override fun nextRoundIndex(cursorKey: String, candidateSize: Int): Int {
        require(candidateSize > 0) { "候选节点数必须大于 0" }
        val now = System.currentTimeMillis()
        mapper.incrementCursor(cursorKey, now)
        val value = mapper.selectCursorValue(cursorKey) ?: 1L
        return ((value - 1) % candidateSize).toInt()
    }
}
