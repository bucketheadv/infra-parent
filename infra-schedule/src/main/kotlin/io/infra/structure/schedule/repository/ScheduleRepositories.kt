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
    /** 锁定最新任务定义后更新并撤销旧 Outbox，避免旧快照覆盖并发调度进度。 */
    fun updateAndCancelPendingOutbox(id: Long, now: Long, updater: (ScheduleJob) -> ScheduleJob): ScheduleJob
    /** 锁定最新任务定义后更新，不撤销已有 Outbox，适用于幂等启用等不应丢弃活跃触发的操作。 */
    fun updateLocked(id: Long, updater: (ScheduleJob) -> ScheduleJob): ScheduleJob
    /** 按主键查询任务。 */
    fun findById(id: Long): ScheduleJob?
    /** 查询全部任务。 */
    fun findAll(): List<ScheduleJob>
    /** 统计仍引用指定执行器的任务数量，用于阻止删除仍被任务使用的执行器。 */
    fun countByExecutorId(executorId: Long): Long
    /** 删除任务定义。 */
    fun delete(id: Long): Boolean
    /** 在同一事务中锁定任务、停用任务并撤销尚未开始的触发，供删除流程先阻断新投递。 */
    fun disableAndCancelPendingOutbox(id: Long, now: Long): Boolean
    /** 在同一事务中锁定任务、撤销未开始触发并删除任务定义。 */
    fun deleteAndCancelPendingOutbox(id: Long, now: Long): Boolean
    /** 原子领取一页到期任务，并写入租约。 */
    fun claimDueJobs(now: Long, limit: Int, leaseMillis: Long, owner: String): List<ScheduleJob>
    /** 仅释放当前 [owner] 的租约。 */
    fun releaseClaim(id: Long, owner: String)
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
    /** 锁定仍存在的任务并写入手动触发 Outbox；暂停仅停止定时调度，不阻止管理员立即执行。 */
    fun enqueueManual(outbox: ScheduleTriggerOutbox): Boolean
}

/** 待回收的活跃执行日志引用。 */
data class StaleRunningLogRef(
    /** 执行日志主键。 */
    var id: Long = 0,
    /** 任务主键。 */
    var jobId: Long = 0,
    /** 目标执行器地址。 */
    var targetAddress: String? = null,
    /** 当前活跃状态，用于将取消确认中的记录收口为正确终态。 */
    var status: ExecutionStatus = ExecutionStatus.RUNNING,
    /** 本次投递尝试创建时间。 */
    var triggerTime: Long = 0
)

/** 调度执行审计日志的 MySQL 持久化 SPI。 */
interface ScheduleExecutionLogRepository {
    fun append(log: JobExecutionLog): JobExecutionLog
    /**
     * 仅当所属任务仍存在且启用时写入日志。
     *
     * 任务删除流程会先在同一行上持锁停用任务，因此已读取旧任务定义的工作线程也无法在删除后创建孤儿日志。
     */
    fun appendIfJobEnabled(
        log: JobExecutionLog,
        outboxId: Long? = null,
        owner: String? = null,
        claimToken: String? = null,
        now: Long? = null
    ): JobExecutionLog?
    fun findById(id: Long): JobExecutionLog?
    fun update(log: JobExecutionLog)
    /** 删除单条尚未执行业务副作用的日志，例如常驻任务被执行器丢弃的触发。 */
    fun delete(id: Long): Boolean
    /** 仅将尚未请求中止的 QUEUED/RUNNING 日志收口为终态。 */
    fun finishIfExecuting(log: JobExecutionLog): Boolean
    /** 将 QUEUED/RUNNING 日志改为等待执行器确认的取消状态。 */
    fun requestCancellation(log: JobExecutionLog, timeout: Boolean): Boolean
    /** 执行器确认已退出后，将取消确认中的记录收口为对应终态。 */
    fun finishPendingCancellation(
        id: Long,
        pendingStatus: ExecutionStatus,
        finalStatus: ExecutionStatus,
        finishTime: Long,
        durationMillis: Long,
        message: String
    ): Boolean
    /**
     * 原子回写执行器报告的终态。
     *
     * 若更新瞬间日志已进入 [ExecutionStatus.TIMING_OUT]，必须强制收口为 TIMEOUT，
     * 不能被迟到的成功回调覆盖；CANCELLING 则以执行器实际返回结果为准。
     */
    fun finishFromExecutor(log: JobExecutionLog, timeoutMessage: String): Boolean
    fun markRunningIfQueued(logId: Long, message: String): Boolean
    /** 追加执行器上报的业务日志。 */
    fun appendHandleLog(logId: Long, chunk: String): Boolean
    /** 按主键游标轮换查询等待执行器确认终止的日志；用于 cancel 失败后的短周期补偿与探活。 */
    fun findPendingCancellationCandidates(afterId: Long, limit: Int): List<StaleRunningLogRef>
    /** 按主键游标轮换查询长时间未结束的普通运行日志。 */
    fun findStaleRunningCandidates(staleBeforeTriggerTime: Long, afterId: Long, limit: Int): List<StaleRunningLogRef>
    /** 仅将仍处于 QUEUED/RUNNING 的日志回收为 LOST，避免覆盖并发进入的取消确认状态。 */
    fun markLostIfActive(id: Long, now: Long, message: String): Boolean
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
    /** 标记为当前领取方已完成处理。 */
    fun markDispatched(id: Long, owner: String, claimToken: String, now: Long): Boolean
    /** 延长正在处理的投递租约，防止长任务执行期间被其他调度节点重复领取。 */
    fun renewClaim(id: Long, owner: String, claimToken: String, claimUntil: Long, now: Long): Boolean
    /**
     * 释放投递租约，保留待投递状态，并在 [retryAt] 到达后才允许下一次领取。
     *
     * 该时间存入 PENDING Outbox 的 claim_until，用于抑制执行器网络异常时的高频重复投递。
     */
    fun releaseForRetry(id: Long, owner: String, claimToken: String, error: String, retryAt: Long): Boolean
    /** 取消指定任务尚未投递的触发；删除任务时 [includeManual] 同时撤销手动触发。 */
    fun cancelPendingByJobId(jobId: Long, now: Long, includeManual: Boolean = false): Int
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
