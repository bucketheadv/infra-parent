package io.infra.structure.schedule.repository

/** 路由节点 LFU/LRU 统计快照。 */
data class RouteNodeStat(
    /** 稳定路由节点键，通常是规范化地址或本地执行器标识。 */
    val nodeKey: String,
    /** 节点累计被 LFU 路由选中的次数。 */
    val useCount: Int = 0,
    /** 节点最近一次被 LRU 路由选中的 Unix 毫秒时间戳。 */
    val lastRouteTime: Long = 0
)

/** 跨调度节点共享的路由选用统计。 */
interface RouteNodeStatRepository {
    /** 批量读取节点统计；未记录的节点视为 0 次 / 0 时间。 */
    fun stats(nodeKeys: Collection<String>): Map<String, RouteNodeStat>
    /** 记录一次路由选用。 */
    fun recordUse(nodeKey: String, now: Long = System.currentTimeMillis())
}
