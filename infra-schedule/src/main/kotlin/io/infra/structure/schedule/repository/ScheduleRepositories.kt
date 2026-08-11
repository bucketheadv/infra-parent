package io.infra.structure.schedule.repository

import io.infra.structure.schedule.model.ExecutionLogQuery
import io.infra.structure.schedule.model.ExecutorHeartbeat
import io.infra.structure.schedule.model.ExecutorStatus
import io.infra.structure.schedule.model.JobExecutionLog
import io.infra.structure.schedule.model.JobStatus
import io.infra.structure.schedule.model.ScheduleJob
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * 调度中心持久化 SPI。生产集群应实现该接口，并在 claimDueJobs 内使用条件更新或行锁完成抢占。
 */
interface ScheduleJobRepository {
    /** 新建或覆盖保存任务定义与其运行时调度状态。 */
    fun save(job: ScheduleJob): ScheduleJob
    /** 按任务 ID 查询完整定义。 */
    fun findById(id: Long): ScheduleJob?
    /** 查询全部任务，供管理端列表展示。 */
    fun findAll(): List<ScheduleJob>
    /** 删除任务定义及其调度状态。 */
    fun delete(id: Long): Boolean
    /**
     * 原子领取已到期任务，并写入归属节点与租约时间。
     * 多节点实现必须保证同一任务在一个租约窗口内最多被一个节点返回。
     */
    fun claimDueJobs(now: Long, limit: Int, leaseMillis: Long, owner: String): List<ScheduleJob>
    /** 仅释放 [owner] 持有的租约，避免过期节点清理新节点的租约。 */
    fun releaseClaim(id: Long, owner: String)
    /** 无条件清除租约，用于任务定义变更后立即按新配置调度。 */
    fun clearClaim(id: Long)
    /**
     * 仅推进调度进度字段，避免全量回写覆盖期间被修改的 cron 等定义。
     * 仅当租约仍属于 [owner] 时更新成功。
     */
    fun completeSchedule(id: Long, owner: String, lastTriggerAt: Long, nextTriggerAt: Long, updateTime: Long): Boolean
}

interface ScheduleExecutionLogRepository {
    /** 新增一条执行审计记录，返回带数据库主键的完整对象。 */
    fun append(log: JobExecutionLog): JobExecutionLog
    /** 按主键更新执行审计记录（用于运行中 → 终态）。 */
    fun update(log: JobExecutionLog)
    /** 按触发时间倒序查询指定任务的近期执行记录。 */
    fun findByJobId(jobId: Long, limit: Int = 100): List<JobExecutionLog>
    /** 按任务、执行器、状态与触发时间范围查询执行日志。 */
    fun query(query: ExecutionLogQuery): List<JobExecutionLog>
}

interface ExecutorHeartbeatRepository {
    /** 新增或刷新一个执行器的最近存活时间。 */
    fun heartbeat(heartbeat: ExecutorHeartbeat)
    /** 保存执行器节点的完整管理配置。 */
    fun save(executor: ExecutorHeartbeat): ExecutorHeartbeat
    /** 按数据库自增节点 ID 查询已登记执行器。 */
    fun findById(id: Long): ExecutorHeartbeat?
    /** 按全局唯一的执行器分组查询已登记节点。 */
    fun findByGroup(executorGroup: String): ExecutorHeartbeat?
    /** 查询仍在心跳有效期内的分组执行器。 */
    fun list(executorGroup: String, now: Long, timeoutMillis: Long): List<ExecutorHeartbeat>
    /** 查询分组内已注册节点，包含禁用或心跳超时的节点，供管理后台运维。 */
    fun listRegistered(executorGroup: String): List<ExecutorHeartbeat>
    /** 查询所有已登记执行器，供任务编辑器选择目标节点。 */
    fun listRegistered(): List<ExecutorHeartbeat>
    /** 修改执行器可用状态；返回 false 表示节点不存在。 */
    fun updateStatus(id: Long, status: ExecutorStatus): Boolean
    /**
     * 将分组执行器标记为立即离线，不改变启停状态。
     * [address] 非空时仅剔除该实例地址（自动注册）；为空时清空全部自动注册地址并重置心跳。
     * 返回 false 表示节点不存在。
     */
    fun markOffline(executorGroup: String, address: String? = null): Boolean
    /** 查询执行器当前可用于路由的地址（手动=配置列表；自动=心跳未超时实例）。 */
    fun listRoutableAddresses(executorId: Long, now: Long, timeoutMillis: Long): List<String>
    /** 删除执行器节点；返回 false 表示节点不存在。 */
    fun delete(id: Long): Boolean
}

/** 不配置数据源时使用的本地开发仓储，不提供跨进程持久化。 */
class InMemoryScheduleJobRepository : ScheduleJobRepository {
    private val jobs = ConcurrentHashMap<Long, ScheduleJob>()
    private val jobIdSequence = AtomicLong()

    override fun save(job: ScheduleJob): ScheduleJob {
        val saved = job.copy(id = job.id.takeIf { it > 0 } ?: jobIdSequence.incrementAndGet())
        jobs[saved.id] = saved
        return saved
    }

    override fun findById(id: Long): ScheduleJob? = jobs[id]

    override fun findAll(): List<ScheduleJob> = jobs.values.sortedBy { it.name }

    override fun delete(id: Long): Boolean = jobs.remove(id) != null

    override fun claimDueJobs(now: Long, limit: Int, leaseMillis: Long, owner: String): List<ScheduleJob> {
        val claimed = mutableListOf<ScheduleJob>()
        jobs.values
            .asSequence()
            .filter { it.status == JobStatus.ENABLED && it.nextTriggerAt != null && it.nextTriggerAt <= now }
            .sortedBy { it.nextTriggerAt }
            .take(limit)
            .forEach { candidate ->
                if (claimed.size >= limit) return@forEach
                jobs.compute(candidate.id) { _, current ->
                    if (current == null || current.status != JobStatus.ENABLED || current.nextTriggerAt == null ||
                        current.nextTriggerAt > now || (current.claimUntil != null && current.claimUntil > now)
                    ) {
                        current
                    } else {
                        val locked = current.copy(claimOwner = owner, claimUntil = now + leaseMillis, updateTime = now)
                        claimed += locked
                        locked
                    }
                }
            }
        return claimed
    }

    /** 仅释放当前节点实际持有的内存租约。 */
    override fun releaseClaim(id: Long, owner: String) {
        jobs.computeIfPresent(id) { _, current ->
            if (current.claimOwner == owner) current.copy(claimOwner = null, claimUntil = null) else current
        }
    }

    override fun clearClaim(id: Long) {
        jobs.computeIfPresent(id) { _, current -> current.copy(claimOwner = null, claimUntil = null) }
    }

    override fun completeSchedule(
        id: Long,
        owner: String,
        lastTriggerAt: Long,
        nextTriggerAt: Long,
        updateTime: Long
    ): Boolean {
        var updated = false
        jobs.computeIfPresent(id) { _, current ->
            if (current.claimOwner != owner) {
                current
            } else {
                updated = true
                current.copy(
                    lastTriggerAt = lastTriggerAt,
                    nextTriggerAt = nextTriggerAt,
                    claimOwner = null,
                    claimUntil = null,
                    updateTime = updateTime
                )
            }
        }
        return updated
    }
}

/** 有界内存执行日志仓储，最多保留最近 [MAX_LOGS] 条记录。 */
class InMemoryScheduleExecutionLogRepository : ScheduleExecutionLogRepository {
    private val logs = ConcurrentLinkedDeque<JobExecutionLog>()
    private val idSequence = AtomicLong()

    override fun append(log: JobExecutionLog): JobExecutionLog {
        val saved = log.copy(id = log.id.takeIf { it > 0 } ?: idSequence.incrementAndGet())
        logs.addFirst(saved)
        while (logs.size > MAX_LOGS) logs.pollLast()
        return saved
    }

    override fun update(log: JobExecutionLog) {
        require(log.id > 0) { "更新执行日志需要有效主键" }
        val iterator = logs.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == log.id) {
                iterator.remove()
                break
            }
        }
        logs.addFirst(log)
    }

    override fun findByJobId(jobId: Long, limit: Int): List<JobExecutionLog> =
        query(ExecutionLogQuery(jobId = jobId, limit = limit))

    override fun query(query: ExecutionLogQuery): List<JobExecutionLog> =
        logs.asSequence()
            .filter { query.jobId == null || it.jobId == query.jobId }
            .filter { query.executorId == null || it.executorId == query.executorId }
            .filter { query.status == null || it.status == query.status }
            .filter { query.triggerTimeFrom == null || it.triggerTime >= query.triggerTimeFrom }
            .filter { query.triggerTimeTo == null || it.triggerTime <= query.triggerTimeTo }
            .sortedByDescending { it.triggerTime }
            .take(query.limit.coerceIn(1, 1_000))
            .toList()

    private companion object {
        const val MAX_LOGS = 10_000
    }
}

/** 本地执行器心跳仓储，仅用于未接入数据库的开发场景。 */
class InMemoryExecutorHeartbeatRepository(
    private val heartbeatTimeoutMillis: Long = 30_000
) : ExecutorHeartbeatRepository {
    private val executors = ConcurrentHashMap<String, ExecutorHeartbeat>()
    private val registry = ConcurrentHashMap<Long, ConcurrentHashMap<String, Long>>()
    private val nodeIdSequence = AtomicLong()

    override fun heartbeat(heartbeat: ExecutorHeartbeat) {
        val now = heartbeat.lastHeartbeatTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val current = executors[heartbeat.executorGroup]
        val mode = current?.addressMode ?: io.infra.structure.schedule.model.ExecutorAddressMode.AUTO_REGISTER
        val saved = save(
            heartbeat.copy(
                id = current?.id ?: heartbeat.id,
                executorName = current?.executorName?.takeIf { it.isNotBlank() } ?: heartbeat.executorName,
                address = if (mode == io.infra.structure.schedule.model.ExecutorAddressMode.MANUAL) current?.address else current?.address,
                addressMode = mode,
                status = current?.status ?: ExecutorStatus.ENABLED,
                lastHeartbeatTime = now
            )
        )
        if (mode == io.infra.structure.schedule.model.ExecutorAddressMode.AUTO_REGISTER) {
            val address = heartbeat.address?.trim()?.takeIf { it.isNotBlank() }
            if (address != null) {
                registry.computeIfAbsent(saved.id) { ConcurrentHashMap() }[address] = now
            }
            refreshAutoAddresses(saved.id, now)
        }
    }

    override fun save(executor: ExecutorHeartbeat): ExecutorHeartbeat {
        val normalizedAddress = when (executor.addressMode) {
            io.infra.structure.schedule.model.ExecutorAddressMode.MANUAL ->
                io.infra.structure.schedule.core.ExecutorAddresses.format(
                    io.infra.structure.schedule.core.ExecutorAddresses.parse(executor.address)
                )
            io.infra.structure.schedule.model.ExecutorAddressMode.AUTO_REGISTER -> executor.address
        }
        val saved = executor.copy(
            id = executor.id.takeIf { it > 0 } ?: nodeIdSequence.incrementAndGet(),
            address = normalizedAddress
        )
        val previous = executors.put(saved.executorGroup, saved)
        if (saved.addressMode == io.infra.structure.schedule.model.ExecutorAddressMode.MANUAL) {
            registry.remove(saved.id)
        } else if (previous?.addressMode == io.infra.structure.schedule.model.ExecutorAddressMode.MANUAL) {
            executors[saved.executorGroup] = saved.copy(address = null)
            return executors[saved.executorGroup]!!
        }
        return saved
    }

    override fun findById(id: Long): ExecutorHeartbeat? = executors.values.firstOrNull { it.id == id }

    override fun findByGroup(executorGroup: String): ExecutorHeartbeat? = executors[executorGroup]

    override fun list(executorGroup: String, now: Long, timeoutMillis: Long): List<ExecutorHeartbeat> =
        executors.values.filter {
            it.executorGroup == executorGroup && it.status == ExecutorStatus.ENABLED && now - it.lastHeartbeatTime <= timeoutMillis
        }

    override fun listRegistered(executorGroup: String): List<ExecutorHeartbeat> =
        executors.values.filter { it.executorGroup == executorGroup }.sortedBy { it.id }

    override fun listRegistered(): List<ExecutorHeartbeat> = executors.values.sortedBy { it.id }

    override fun updateStatus(id: Long, status: ExecutorStatus): Boolean {
        var changed = false
        executors.forEach { (code, current) ->
            if (current.id == id) {
                executors.computeIfPresent(code) { _, value ->
                    changed = true
                    value.copy(status = status)
                }
            }
        }
        return changed
    }

    override fun markOffline(executorGroup: String, address: String?): Boolean {
        val current = executors[executorGroup] ?: return false
        val normalized = address?.trim()?.takeIf { it.isNotBlank() }
        val now = System.currentTimeMillis()
        if (current.addressMode == io.infra.structure.schedule.model.ExecutorAddressMode.AUTO_REGISTER && normalized != null) {
            registry[current.id]?.remove(normalized)
            refreshAutoAddresses(current.id, now)
            return true
        }
        registry.remove(current.id)
        executors[executorGroup] = current.copy(
            lastHeartbeatTime = 0,
            address = if (current.addressMode == io.infra.structure.schedule.model.ExecutorAddressMode.AUTO_REGISTER) null else current.address
        )
        return true
    }

    override fun listRoutableAddresses(executorId: Long, now: Long, timeoutMillis: Long): List<String> {
        val executor = findById(executorId) ?: return emptyList()
        return when (executor.addressMode) {
            io.infra.structure.schedule.model.ExecutorAddressMode.MANUAL ->
                io.infra.structure.schedule.core.ExecutorAddresses.parse(executor.address)
            io.infra.structure.schedule.model.ExecutorAddressMode.AUTO_REGISTER -> {
                refreshAutoAddresses(executorId, now, timeoutMillis)
                registry[executorId]?.filterValues { now - it <= timeoutMillis }?.keys?.sorted().orEmpty()
            }
        }
    }

    override fun delete(id: Long): Boolean {
        val executor = findById(id) ?: return false
        registry.remove(id)
        return executors.remove(executor.executorGroup) != null
    }

    private fun refreshAutoAddresses(executorId: Long, now: Long, timeoutMillis: Long = heartbeatTimeoutMillis) {
        val addresses = registry[executorId] ?: return
        addresses.entries.removeIf { now - it.value > timeoutMillis }
        val alive = addresses.filterValues { now - it <= timeoutMillis }.keys.sorted()
        val latest = addresses.values.maxOrNull() ?: 0L
        val current = findById(executorId) ?: return
        executors[current.executorGroup] = current.copy(
            address = io.infra.structure.schedule.core.ExecutorAddresses.format(alive),
            lastHeartbeatTime = latest
        )
    }
}
