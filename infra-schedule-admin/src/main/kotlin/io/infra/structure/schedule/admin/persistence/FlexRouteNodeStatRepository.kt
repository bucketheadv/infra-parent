package io.infra.structure.schedule.admin.persistence

import io.infra.structure.schedule.admin.persistence.mapper.ScheduleRouteStatMapper
import io.infra.structure.schedule.repository.RouteNodeStat
import io.infra.structure.schedule.repository.RouteNodeStatRepository

/** 基于 MySQL 的路由 LFU/LRU 统计仓储。 */
open class FlexRouteNodeStatRepository(
    private val mapper: ScheduleRouteStatMapper
) : RouteNodeStatRepository {
    override fun stats(nodeKeys: Collection<String>): Map<String, RouteNodeStat> {
        if (nodeKeys.isEmpty()) return emptyMap()
        return nodeKeys.mapNotNull { key ->
            mapper.selectOneById(key)?.let { entity ->
                key to RouteNodeStat(
                    nodeKey = entity.nodeKey,
                    useCount = entity.useCount,
                    lastRouteTime = entity.lastRouteTime
                )
            }
        }.toMap()
    }

    override fun recordUse(nodeKey: String, now: Long) {
        mapper.upsertRouteUse(nodeKey, now)
    }
}
