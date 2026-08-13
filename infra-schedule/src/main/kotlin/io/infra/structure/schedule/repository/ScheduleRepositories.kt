package io.infra.structure.schedule.repository

import io.infra.structure.schedule.model.ExecutionLogQuery
import io.infra.structure.schedule.model.ExecutionStatus
import io.infra.structure.schedule.model.ExecutorHeartbeat
import io.infra.structure.schedule.model.ExecutorStatus
import io.infra.structure.schedule.model.JobExecutionLog
import io.infra.structure.schedule.model.ScheduleJob
import io.infra.structure.schedule.model.ScheduleTriggerOutbox

/** 调度任务定义的 MySQL 持久化 SPI。 */
interface ScheduleJobRepository {
    /** 新建或覆盖保存任务定义与运行时调度状态。 */
    fun save(job: ScheduleJob): ScheduleJob
    /** 按主键查询任务。 */
    fun findById(id: Long): ScheduleJob?
    /** 查询全部任务。 */
    fun findAll(): List<ScheduleJob>
    /** 统计仍引用指定执行器的任务数量，用于阻止删除仍被任务使用的执行器。 */
    fun countByExecutorId(executorId: Long): Long
    /** 删除任务定义。 */
    fun delete(id: Long): Boolean
    /** 原子领取一页到期任务，并写入租约。 */
    fun claimDueJobs(now: Long, limit: Int, leaseMillis: Long, owner: String): List<ScheduleJob>
    /** 仅释放当前 [owner] 的租约。 */
    fun releaseClaim(id: Long, owner: String)
    /** 无条件清除租约，用于任务定义变更。 */
    fun clearClaim(id: Long)
    /** 仅推进调度进度字段；仅当前租约持有者能成功。 */
    fun completeSchedule(id: Long, owner: String, lastTriggerAt: Long, nextTriggerAt: Long, updateTime: Long): Boolean
    /** 在同一 MySQL 事务中推进调度进度并插入可靠触发 Outbox。 */
    fun completeScheduleAndEnqueue(
        id: Long,
        owner: String,
        lastTriggerAt: Long,
        nextTriggerAt: Long,
        outbox: ScheduleTriggerOutbox,
        updateTime: Long
    ): Boolean
}

/** 待回收的活跃执行日志引用。 */
data class StaleRunningLogRef(
    /** 执行日志主键。 */
    var id: Long = 0,
    /** 任务主键。 */
    var jobId: Long = 0,
    /** 目标执行器地址。 */
    var targetAddress: String? = null
)

/** 调度执行审计日志的 MySQL 持久化 SPI。 */
interface ScheduleExecutionLogRepository {
    fun append(log: JobExecutionLog): JobExecutionLog
    fun findById(id: Long): JobExecutionLog?
    fun update(log: JobExecutionLog)
    /** 删除单条尚未执行业务副作用的日志，例如常驻任务被执行器丢弃的触发。 */
    fun delete(id: Long): Boolean
    fun updateIfRunning(log: JobExecutionLog): Boolean
    fun cancelRunningByJobId(jobId: Long, message: String, finishTime: Long): Int
    fun markRunningIfQueued(logId: Long, message: String): Boolean
    /** 追加执行器上报的业务日志。 */
    fun appendHandleLog(logId: Long, chunk: String): Boolean
    fun findStaleRunningCandidates(staleBeforeTriggerTime: Long, limit: Int): List<StaleRunningLogRef>
    fun markLostIfActive(id: Long, now: Long, message: String): Boolean
    fun failRunningByJobAndTrigger(jobId: Long, triggerTime: Long, message: String, finishTime: Long): Int
    fun findActiveByJobId(jobId: Long, limit: Int = 100): List<JobExecutionLog>
    fun findByJobId(jobId: Long, limit: Int = 100): List<JobExecutionLog>
    fun query(query: ExecutionLogQuery): List<JobExecutionLog>
    fun count(query: ExecutionLogQuery): Long
    /** 按主键批量删除早于阈值且已结束的历史日志。 */
    fun deleteFinishedBefore(finishTimeBefore: Long, limit: Int): Int
}

/** 可靠触发 Outbox 的 MySQL 持久化 SPI。 */
interface ScheduleTriggerOutboxRepository {
    /** 与任务调度进度在同一事务中插入待投递记录。 */
    fun enqueue(outbox: ScheduleTriggerOutbox): ScheduleTriggerOutbox
    /** 用租约领取待投递记录。 */
    fun claimPending(now: Long, limit: Int, leaseMillis: Long, owner: String): List<ScheduleTriggerOutbox>
    /** 标记为当前节点已完成处理。 */
    fun markDispatched(id: Long, owner: String, now: Long): Boolean
    /** 延长正在处理的投递租约，防止长任务执行期间被其他调度节点重复领取。 */
    fun renewClaim(id: Long, owner: String, claimUntil: Long, now: Long): Boolean
    /** 释放投递租约，保留待投递状态以便下一轮重试。 */
    fun releaseForRetry(id: Long, owner: String, error: String, now: Long): Boolean
    /** 取消指定任务尚未投递的触发。 */
    fun cancelPendingByJobId(jobId: Long, now: Long): Int
    /** 分批清理已投递或已取消的历史 Outbox 记录。 */
    fun deleteCompletedBefore(updateTimeBefore: Long, limit: Int): Int
}

/** 执行器注册、地址与心跳的 MySQL 持久化 SPI。 */
interface ExecutorHeartbeatRepository {
    fun heartbeat(heartbeat: ExecutorHeartbeat)
    fun save(executor: ExecutorHeartbeat): ExecutorHeartbeat
    fun findById(id: Long): ExecutorHeartbeat?
    fun findByGroup(executorGroup: String): ExecutorHeartbeat?
    fun list(executorGroup: String, now: Long, timeoutMillis: Long): List<ExecutorHeartbeat>
    fun listRegistered(executorGroup: String): List<ExecutorHeartbeat>
    fun listRegistered(): List<ExecutorHeartbeat>
    fun updateStatus(id: Long, status: ExecutorStatus): Boolean
    fun markOffline(executorGroup: String, address: String? = null): Boolean
    fun listRoutableAddresses(executorId: Long, now: Long, timeoutMillis: Long): List<String>
    /** 仅当没有任务引用该执行器时删除，避免并发创建任务形成孤儿引用。 */
    fun deleteIfUnreferenced(id: Long): Boolean
}
