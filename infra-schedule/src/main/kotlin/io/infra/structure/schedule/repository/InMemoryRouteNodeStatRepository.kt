package io.infra.structure.schedule.repository

import java.util.concurrent.ConcurrentHashMap

/** 无数据源时的路由统计仓储（单 JVM 有效）。 */
class InMemoryRouteNodeStatRepository : RouteNodeStatRepository {
    private val statsByKey = ConcurrentHashMap<String, RouteNodeStat>()

    override fun stats(nodeKeys: Collection<String>): Map<String, RouteNodeStat> =
        nodeKeys.associateWith { key -> statsByKey[key] ?: RouteNodeStat(nodeKey = key) }

    override fun recordUse(nodeKey: String, now: Long) {
        statsByKey.compute(nodeKey) { _, current ->
            val base = current ?: RouteNodeStat(nodeKey = nodeKey)
            base.copy(useCount = base.useCount + 1, lastRouteTime = now)
        }
    }
}
