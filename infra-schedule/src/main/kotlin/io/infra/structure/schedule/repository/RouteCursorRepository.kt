package io.infra.structure.schedule.repository

/** 跨调度节点共享的轮询游标。 */
interface RouteCursorRepository {
    /** 返回本次 ROUND 路由应使用的候选下标 `[0, candidateSize)`。 */
    fun nextRoundIndex(cursorKey: String, candidateSize: Int): Int
}
