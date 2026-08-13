package io.infra.structure.schedule.core

import io.infra.structure.schedule.api.ScheduleExecutor
import io.infra.structure.schedule.model.ExecutorAddressMode
import io.infra.structure.schedule.model.ExecutorHeartbeat
import io.infra.structure.schedule.model.ExecutorStatus
import io.infra.structure.schedule.model.RouteStrategy
import io.infra.structure.schedule.model.ScheduleExecutorDraft
import io.infra.structure.schedule.repository.ExecutorHeartbeatRepository
import io.infra.structure.schedule.repository.RouteCursorRepository
import io.infra.structure.schedule.repository.RouteNodeStatRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** 带数据库主键的可调度执行器，供执行日志关联执行器 ID 与目标地址。 */
data class RoutedExecutor(
    val dbId: Long,
    val executor: ScheduleExecutor,
    /** 执行器访问地址，本地执行时可能为空。 */
    val address: String? = null,
    /** 一致性 HASH 环上使用的原始地址（对齐 xxl-job，不做 normalize）。 */
    val hashRingKey: String? = address?.trim()?.takeIf { it.isNotBlank() }
) {
    /** LFU/LRU 统计用的稳定节点键。 */
    val routeNodeKey: String = RouteHash.addressKey(this)
}

/** 管理执行器实例、自动心跳注册、多地址路由和后台 CRUD 配置。 */
class ExecutorRegistry(
    private val heartbeatRepository: ExecutorHeartbeatRepository,
    private val heartbeatTimeoutMillis: Long,
    private val clientFactory: ScheduleExecutorClientFactory? = null,
    private val routeStatRepository: RouteNodeStatRepository,
    private val routeCursorRepository: RouteCursorRepository,
    /** 查询执行器是否仍被任务引用，防止管理端删除后留下不可路由任务。 */
    private val jobReferenceCounter: (Long) -> Long = { 0L }
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val executors = ConcurrentHashMap<String, ScheduleExecutor>()

    /** 注册本地执行器并刷新以分组为唯一标识的心跳。 */
    fun register(executor: ScheduleExecutor) {
        executors[executor.group] = executor
        heartbeat(executor.group, executor.id)
    }

    /** 从当前进程移除本地执行器实现。 */
    fun unregister(executorGroup: String) { executors.remove(executorGroup) }

    /** 执行器自动上报分组、展示名称和服务地址；自动模式下会登记到多地址列表。 */
    fun heartbeat(executorGroup: String, executorName: String, address: String? = null) {
        heartbeatRepository.heartbeat(ExecutorHeartbeat(
            executorGroup = executorGroup,
            executorName = executorName,
            address = address,
            lastHeartbeatTime = System.currentTimeMillis()
        ))
    }

    /**
     * 主动标记执行器离线。
     * [address] 非空时仅从自动注册地址列表剔除该实例；为空时清空全部自动注册地址。
     */
    fun markOffline(executorGroup: String, address: String? = null): Boolean {
        if (address.isNullOrBlank()) unregister(executorGroup)
        return heartbeatRepository.markOffline(executorGroup, address)
    }

    /** 返回给定分组的健康、已启用执行器地址节点。 */
    fun activeExecutors(executorGroup: String): List<ScheduleExecutor> =
        activeRouted(executorGroup).map { it.executor }

    /** 返回给定分组下全部可路由地址节点。 */
    fun activeRouted(executorGroup: String): List<RoutedExecutor> {
        val now = System.currentTimeMillis()
        return heartbeatRepository.list(executorGroup, now, heartbeatTimeoutMillis)
            .filter { it.status == ExecutorStatus.ENABLED }
            .flatMap { expandAddresses(it, now) }
            .sortedWith(compareBy({ it.dbId }, { it.address ?: "" }))
    }

    /** 按数据库自增 ID 获取健康节点（多地址时返回首个）。 */
    fun activeExecutor(id: Long): ScheduleExecutor? = activeNodes(id).firstOrNull()?.executor

    /** 按数据库自增 ID 获取健康、已启用的全部可路由地址。 */
    fun activeNodes(id: Long): List<RoutedExecutor> {
        val heartbeat = heartbeatRepository.findById(id) ?: return emptyList()
        if (heartbeat.status != ExecutorStatus.ENABLED) return emptyList()
        val now = System.currentTimeMillis()
        if (heartbeat.addressMode == ExecutorAddressMode.AUTO_REGISTER &&
            now - heartbeat.lastHeartbeatTime > heartbeatTimeoutMillis
        ) {
            return emptyList()
        }
        return expandAddresses(heartbeat, now)
    }

    /**
     * 按数据库自增 ID 获取可调用节点。
     * 手动模式返回全部配置地址；自动模式仅返回心跳未超时的实例地址。
     */
    fun runnableExecutor(id: Long): ScheduleExecutor? = runnableNodes(id).firstOrNull()?.executor

    /** 按数据库自增 ID 获取可调用的全部地址节点。 */
    fun runnableNodes(id: Long): List<RoutedExecutor> {
        val heartbeat = heartbeatRepository.findById(id) ?: return emptyList()
        if (heartbeat.status != ExecutorStatus.ENABLED) return emptyList()
        return expandAddresses(heartbeat, System.currentTimeMillis())
    }

    /** 兼容旧调用：返回单个路由节点。 */
    fun runnableRouted(id: Long): RoutedExecutor? = runnableNodes(id).firstOrNull()

    /** 返回分组内健康节点的心跳信息。 */
    fun heartbeats(executorGroup: String): List<ExecutorHeartbeat> =
        heartbeatRepository.list(executorGroup, System.currentTimeMillis(), heartbeatTimeoutMillis)

    /** 返回已登记节点，用于后台管理和任务执行器选择。 */
    fun registeredExecutors(executorGroup: String): List<ExecutorHeartbeat> =
        heartbeatRepository.listRegistered(executorGroup)

    /** 返回全部已登记节点。 */
    fun registeredExecutors(): List<ExecutorHeartbeat> = heartbeatRepository.listRegistered()

    /** 新建执行器，数据库负责生成自增 ID；分组全局唯一。 */
    fun createExecutor(draft: ScheduleExecutorDraft): ExecutorHeartbeat {
        require(heartbeatRepository.findByGroup(draft.executorGroup) == null) {
            "执行器分组已存在: ${draft.executorGroup}"
        }
        return heartbeatRepository.save(ExecutorHeartbeat(
            executorGroup = draft.executorGroup,
            executorName = draft.executorName,
            address = draft.address,
            addressMode = draft.addressMode,
            status = draft.status,
            lastHeartbeatTime = System.currentTimeMillis()
        ))
    }

    /** 编辑执行器配置；分组不可修改，以保持心跳关联稳定。 */
    fun updateExecutor(id: Long, draft: ScheduleExecutorDraft): ExecutorHeartbeat {
        val current = requireNotNull(heartbeatRepository.findById(id)) { "执行器不存在: $id" }
        require(current.executorGroup == draft.executorGroup) { "执行器分组不可修改" }
        return heartbeatRepository.save(
            current.copy(
                executorName = draft.executorName,
                address = draft.address,
                addressMode = draft.addressMode,
                status = draft.status
            )
        )
    }

    /** 删除执行器登记配置。 */
    fun deleteExecutor(id: Long): Boolean {
        val executor = heartbeatRepository.findById(id) ?: return false
        check(jobReferenceCounter(id) == 0L) { "执行器仍被任务引用，不能删除: $id" }
        // 持久化层还会以 SQL 条件再次校验，覆盖“检查后恰有任务新建”的并发窗口。
        if (!heartbeatRepository.deleteIfUnreferenced(id)) {
            throw IllegalStateException("执行器仍被任务引用，不能删除: $id")
        }
        executors.remove(executor.executorGroup)
        return true
    }

    /** 设置执行器启停状态。 */
    fun setStatus(id: Long, status: ExecutorStatus): Boolean = heartbeatRepository.updateStatus(id, status)

    /** 按策略选择执行器；任务已指定自增 ID 时由调用方使用 [runnableNodes] + [applyRoute]。 */
    fun select(executorGroup: String, strategy: RouteStrategy, routeKey: String): List<ScheduleExecutor> =
        selectRouted(executorGroup, strategy, routeKey).map { it.executor }

    /** 按策略选择分组内可路由地址节点。 */
    fun selectRouted(executorGroup: String, strategy: RouteStrategy, routeKey: String): List<RoutedExecutor> =
        applyRoute(activeRouted(executorGroup), strategy, routeKey, executorGroup)

    /**
     * 在候选地址节点上应用路由策略（对齐 xxl-job）。
     * [FAILOVER] / [BUSYOVER] 返回全部有序候选，由调度侧按 beat / idleBeat 挑选第一个可用节点。
     * [SHARDING_BROADCAST] 返回全部候选。
     */
    fun applyRoute(
        candidates: List<RoutedExecutor>,
        strategy: RouteStrategy,
        routeKey: String,
        cursorKey: String
    ): List<RoutedExecutor> {
        if (candidates.isEmpty()) return emptyList()
        when (strategy) {
            RouteStrategy.SHARDING_BROADCAST,
            RouteStrategy.FAILOVER,
            RouteStrategy.BUSYOVER -> return candidates
            else -> Unit
        }
        val selected = when (strategy) {
            RouteStrategy.FIRST -> candidates.first()
            RouteStrategy.LAST -> candidates.last()
            RouteStrategy.ROUND -> {
                val index = routeCursorRepository.nextRoundIndex(cursorKey, candidates.size)
                candidates[index]
            }
            RouteStrategy.RANDOM -> candidates[Random.nextInt(candidates.size)]
            RouteStrategy.CONSISTENT_HASH -> selectConsistentHash(candidates, routeKey)
            RouteStrategy.LEAST_FREQUENTLY_USED -> selectLfu(candidates)
            RouteStrategy.LEAST_RECENTLY_USED -> selectLru(candidates)
            RouteStrategy.FAILOVER,
            RouteStrategy.BUSYOVER,
            RouteStrategy.SHARDING_BROADCAST -> error("已提前处理的路由策略: $strategy")
        }
        recordUse(selected)
        return listOf(selected)
    }

    private fun selectConsistentHash(candidates: List<RoutedExecutor>, routeKey: String): RoutedExecutor {
        val byAddress = candidates.groupBy { RouteHash.hashRingKey(it) }
        val selectedAddress = RouteHash.selectConsistentAddress(routeKey, byAddress.keys.sorted())
            ?: return candidates.first()
        return byAddress[selectedAddress]?.firstOrNull() ?: candidates.first()
    }

    private fun selectLfu(candidates: List<RoutedExecutor>): RoutedExecutor {
        val stats = routeStatRepository.stats(candidates.map { it.routeNodeKey })
        return candidates.minWith(
            compareBy<RoutedExecutor> { stats[it.routeNodeKey]?.useCount ?: 0 }
                .thenBy { it.dbId }
                .thenBy { it.address ?: "" }
        )
    }

    private fun selectLru(candidates: List<RoutedExecutor>): RoutedExecutor {
        val stats = routeStatRepository.stats(candidates.map { it.routeNodeKey })
        return candidates.minWith(
            compareBy<RoutedExecutor> { stats[it.routeNodeKey]?.lastRouteTime ?: 0L }
                .thenBy { it.dbId }
                .thenBy { it.address ?: "" }
        )
    }

    private fun recordUse(selected: RoutedExecutor) {
        routeStatRepository.recordUse(selected.routeNodeKey)
    }

    private fun expandAddresses(heartbeat: ExecutorHeartbeat, now: Long): List<RoutedExecutor> {
        val addresses = heartbeatRepository.listRoutableAddresses(heartbeat.id, now, heartbeatTimeoutMillis)
        if (addresses.isEmpty()) {
            val local = executors[heartbeat.executorGroup] ?: return emptyList()
            return listOf(RoutedExecutor(heartbeat.id, local, null))
        }
        return addresses.mapNotNull { rawAddress -> resolveRoutedNode(heartbeat, rawAddress) }
    }

    /**
     * 解析单个可路由节点：
     * - 有效 HTTP 地址走远程客户端（调度中心单独部署时的常态路径）；
     * - 无地址时使用本进程 in-process 执行器（仅嵌入执行器场景）。
     */
    private fun resolveRoutedNode(heartbeat: ExecutorHeartbeat, rawAddress: String): RoutedExecutor? {
        val trimmed = rawAddress.trim()
        if (trimmed.isBlank()) {
            val local = executors[heartbeat.executorGroup]
                ?: run {
                    logger.warn(
                        "无地址且本地执行器未注册，节点已跳过: executorId={}, group={}",
                        heartbeat.id,
                        heartbeat.executorGroup
                    )
                    return null
                }
            return RoutedExecutor(heartbeat.id, local, null)
        }
        val normalized = ExecutorAddresses.normalizeHttpBaseUrl(trimmed)
        if (normalized != null) {
            val remote = clientFactory?.create(heartbeat.copy(address = trimmed))
                ?: run {
                    logger.warn(
                        "无法创建远程执行器客户端，节点已跳过: executorId={}, group={}, address={}",
                        heartbeat.id,
                        heartbeat.executorGroup,
                        trimmed
                    )
                    return null
                }
            return RoutedExecutor(heartbeat.id, remote, normalized, trimmed)
        }
        logger.warn(
            "跳过无效执行器地址: executorId={}, group={}, raw={}",
            heartbeat.id,
            heartbeat.executorGroup,
            rawAddress
        )
        return null
    }
}
