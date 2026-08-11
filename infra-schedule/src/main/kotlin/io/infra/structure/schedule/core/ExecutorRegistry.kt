package io.infra.structure.schedule.core

import io.infra.structure.schedule.api.ScheduleExecutor
import io.infra.structure.schedule.model.ExecutorAddressMode
import io.infra.structure.schedule.model.ExecutorHeartbeat
import io.infra.structure.schedule.model.ExecutorStatus
import io.infra.structure.schedule.model.RouteStrategy
import io.infra.structure.schedule.model.ScheduleExecutorDraft
import io.infra.structure.schedule.repository.ExecutorHeartbeatRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/** 带数据库主键的可调度执行器，供执行日志关联执行器 ID 与目标地址。 */
data class RoutedExecutor(
    val dbId: Long,
    val executor: ScheduleExecutor,
    /** 执行器访问地址，本地执行时可能为空。 */
    val address: String? = null
)

/** 管理执行器实例、自动心跳注册、多地址路由和后台 CRUD 配置。 */
class ExecutorRegistry(
    private val heartbeatRepository: ExecutorHeartbeatRepository,
    private val heartbeatTimeoutMillis: Long,
    private val clientFactory: ScheduleExecutorClientFactory? = null
) {
    private val executors = ConcurrentHashMap<String, ScheduleExecutor>()
    private val cursorByGroup = ConcurrentHashMap<String, AtomicLong>()

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
        executors.remove(executor.executorGroup)
        return heartbeatRepository.delete(id)
    }

    /** 设置执行器启停状态。 */
    fun setStatus(id: Long, status: ExecutorStatus): Boolean = heartbeatRepository.updateStatus(id, status)

    /** 按策略选择执行器；任务已指定自增 ID 时由调用方使用 [runnableNodes] + [applyRoute]。 */
    fun select(executorGroup: String, strategy: RouteStrategy, routeKey: String): List<ScheduleExecutor> =
        selectRouted(executorGroup, strategy, routeKey).map { it.executor }

    /** 按策略选择分组内可路由地址节点。 */
    fun selectRouted(executorGroup: String, strategy: RouteStrategy, routeKey: String): List<RoutedExecutor> =
        applyRoute(activeRouted(executorGroup), strategy, routeKey, executorGroup)

    /** 在候选地址节点上应用路由策略。 */
    fun applyRoute(
        candidates: List<RoutedExecutor>,
        strategy: RouteStrategy,
        routeKey: String,
        cursorKey: String
    ): List<RoutedExecutor> {
        if (candidates.isEmpty()) return emptyList()
        if (strategy == RouteStrategy.BROADCAST || strategy == RouteStrategy.FAILOVER) return candidates
        val selected = when (strategy) {
            RouteStrategy.FIRST -> candidates.first()
            RouteStrategy.FAILOVER -> error("故障转移已提前返回全部候选节点")
            RouteStrategy.ROUND_ROBIN -> candidates[(cursorByGroup.computeIfAbsent(cursorKey) { AtomicLong() }.getAndIncrement() % candidates.size).toInt()]
            RouteStrategy.RANDOM -> candidates[Random.nextInt(candidates.size)]
            RouteStrategy.CONSISTENT_HASH -> candidates[(routeKey.hashCode().toUInt().toLong() % candidates.size).toInt()]
            RouteStrategy.BROADCAST -> error("已提前处理广播路由")
        }
        return listOf(selected)
    }

    private fun expandAddresses(heartbeat: ExecutorHeartbeat, now: Long): List<RoutedExecutor> {
        val addresses = heartbeatRepository.listRoutableAddresses(heartbeat.id, now, heartbeatTimeoutMillis)
        if (addresses.isEmpty()) {
            val local = executors[heartbeat.executorGroup] ?: return emptyList()
            return listOf(RoutedExecutor(heartbeat.id, local, null))
        }
        return addresses.mapNotNull { address ->
            val executor = executors[heartbeat.executorGroup]
                ?: clientFactory?.create(heartbeat.copy(address = address))
                ?: return@mapNotNull null
            RoutedExecutor(heartbeat.id, executor, address)
        }
    }
}
