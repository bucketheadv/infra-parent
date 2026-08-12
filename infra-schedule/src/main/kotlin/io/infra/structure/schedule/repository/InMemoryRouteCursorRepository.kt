package io.infra.structure.schedule.repository

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** 无数据源时的轮询游标（单 JVM 有效）。 */
class InMemoryRouteCursorRepository : RouteCursorRepository {
    private val cursors = ConcurrentHashMap<String, AtomicLong>()

    override fun nextRoundIndex(cursorKey: String, candidateSize: Int): Int {
        require(candidateSize > 0) { "候选节点数必须大于 0" }
        val index = cursors.computeIfAbsent(cursorKey) { AtomicLong(0) }.getAndIncrement()
        return (index % candidateSize).toInt()
    }
}
